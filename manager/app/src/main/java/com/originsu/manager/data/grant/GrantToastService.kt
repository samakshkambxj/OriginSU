package com.originsu.manager.data.grant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.originsu.manager.Natives
import com.originsu.manager.R
import com.originsu.manager.domain.model.SulogEventType
import com.originsu.manager.domain.model.parseSulogLine
import com.originsu.manager.ui.MainActivity
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.time.LocalDate

class GrantToastService : Service() {
    private val repository: GrantToastRepository by inject()

    private val serviceScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    private var monitorJob: Job? = null
    private lateinit var notificationManager: NotificationManager
    private lateinit var windowManager: WindowManager
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private var maxSeq = -1L
    private var streamPfd: ParcelFileDescriptor? = null
    private var toastView: android.view.View? = null
    private val hideToastRunnable = Runnable { hideToast() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        windowManager = getSystemService(WindowManager::class.java)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitoring()
                return START_NOT_STICKY
            }

            else -> startMonitoring()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        runCatching { streamPfd?.close() }
        streamPfd = null
        serviceScope.cancel()
        mainHandler.removeCallbacks(hideToastRunnable)
        hideToast()
        super.onDestroy()
    }

    private fun startMonitoring() {
        // startForegroundService() requires startForeground() within ~10s even
        // when we are about to exit (e.g. toggle off at boot). Promote first,
        // then stop if disabled — otherwise the system kills the app with
        // ForegroundServiceDidNotStartInTimeException (seen in logcat).
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(getString(R.string.grant_toast_monitoring))
            .setContentText(getString(R.string.grant_toast_monitoring_summary))
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .addAction(
                0, getString(R.string.grant_toast_stop),
                PendingIntent.getService(
                    this, 1,
                    Intent(this, GrantToastService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure {
            // startForeground can throw when started from background on
            // Android 12+ (FGS-start restriction) or when the declared
            // foregroundServiceType mismatches. Don't crash: without the
            // foreground promotion the monitor would be killed, so stop.
            stopSelf()
            return
        }
        // Boot receiver starts us unconditionally; exit now when disabled so
        // we don't linger as a foreground service nobody asked for.
        if (!repository.isToastEnabled()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        if (monitorJob?.isActive == true) return
        monitorJob = serviceScope.launch {
            // Prefer the live kernel event stream (real-time, no root needed,
            // same source ksuGrantToast uses). Fall back to polling sulogd's
            // log files when another reader (e.g. ksud sulogd) holds the
            // single stream, or the kernel predates the ioctl.
            if (!runCatching { streamEvents() }.isSuccess) {
                Log.w(TAG, "event stream unavailable, falling back to log files")
            }
            // Seek to the end first so old grants never toast.
            maxSeq = readLatestMaxSeq()
            while (isActive) {
                runCatching { pollOnce() }
                delay(POLL_MILLIS)
            }
        }
    }

    /**
     * Blocking read loop over the live sulog event fd. Returns (for the file
     * fallback above) only when the stream ends or the job is cancelled.
     */
    private suspend fun streamEvents() {
        while (serviceScope.isActive) {
            var pfd: ParcelFileDescriptor? = null
            try {
                pfd = Natives.openSulogStream() ?: return
                streamPfd = pfd
                readStream(pfd)
            } catch (e: Exception) {
                if (e is InterruptedException) return
                Log.w(TAG, "sulog stream error, reconnecting", e)
            } finally {
                runCatching { pfd?.close() }
                if (streamPfd === pfd) streamPfd = null
            }
            if (!serviceScope.isActive) return
            delay(RECONNECT_MILLIS)
        }
    }

    private fun readStream(pfd: ParcelFileDescriptor) {
        val fd = pfd.fileDescriptor
        val buf = ByteArray(8192)
        val staging = ByteArrayOutputStream(8192)
        while (serviceScope.isActive) {
            val n = try {
                Os.read(fd, buf, 0, buf.size)
            } catch (e: ErrnoException) {
                Log.w(TAG, "sulog stream read failed", e)
                return
            }
            if (n <= 0) return
            staging.write(buf, 0, n)
            drainFrames(staging)
        }
    }

    private fun drainFrames(staging: ByteArrayOutputStream) {
        val data = staging.toByteArray()
        var off = 0
        while (data.size - off >= RECORD_HEADER_SIZE) {
            val type = leU16(data, off)
            val len = leU32(data, off + 4).coerceAtMost(MAX_FRAME_SIZE)
            if ((data.size - off).toLong() < RECORD_HEADER_SIZE + len) break
            if (type != DROPPED_TYPE && len >= SULOG_EVENT_FIXED_SIZE) {
                handleStreamEvent(data, off + RECORD_HEADER_SIZE)
            }
            off += (RECORD_HEADER_SIZE + len).toInt()
        }
        staging.reset()
        if (off < data.size) staging.write(data, off, data.size - off)
    }

    private fun handleStreamEvent(data: ByteArray, base: Int) {
        val eventType = leU16(data, base + 2)
        if (eventType != SULOG_ROOT_EXECVE &&
            eventType != SULOG_SUCOMPAT &&
            eventType != SULOG_GRANT_ROOT
        ) {
            return
        }
        if (leI32(data, base + 4) != 0) return
        val uid = leU32(data, base + 20)
        if (uid < 10000L) return
        onGrant(uid.toInt())
    }

    private fun onGrant(uid: Int) {
        val now = System.currentTimeMillis()
        val last = repository.lastToastAt(uid)
        if (now - last < GrantToastRepository.THROTTLE_MILLIS) return
        repository.markToasted(uid, now)
        val label = resolveLabel(uid)
        showToast(getString(R.string.grant_toast_granted, label))
    }

    private fun leU16(data: ByteArray, off: Int): Int =
        (data[off].toInt() and 0xff) or ((data[off + 1].toInt() and 0xff) shl 8)

    private fun leU32(data: ByteArray, off: Int): Long =
        (data[off].toLong() and 0xff) or
                ((data[off + 1].toLong() and 0xff) shl 8) or
                ((data[off + 2].toLong() and 0xff) shl 16) or
                ((data[off + 3].toLong() and 0xff) shl 24)

    private fun leI32(data: ByteArray, off: Int): Int = leU32(data, off).toInt()

    private fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        runCatching { streamPfd?.close() }
        streamPfd = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun pollOnce() {
        val lines = readNewLines() ?: return
        val now = System.currentTimeMillis()
        val uptime = SystemClock.uptimeMillis()
        for (line in lines) {
            val entry = runCatching { parseSulogLine(line, now, uptime) }.getOrNull()
                ?: continue
            if (entry.eventType != SulogEventType.RootExecve &&
                entry.eventType != SulogEventType.SuCompat &&
                entry.eventType != SulogEventType.IoctlGrantRoot
            ) {
                continue
            }
            if (entry.fields["retval"] != "0") continue
            val uid = entry.fields["uid"]?.toIntOrNull() ?: continue
            if (uid < 10000) continue
            onGrant(uid)
        }
    }

    private fun readLatestMaxSeq(): Long {
        val file = latestFile() ?: return -1L
        return readLines(file).mapNotNull { line ->
            SEQ_REGEX.find(line)?.groupValues?.getOrNull(1)?.toLongOrNull()
        }.maxOrNull() ?: -1L
    }

    private fun readNewLines(): List<String>? {
        val file = latestFile() ?: return null
        val fresh = ArrayList<String>()
        var max = maxSeq
        for (line in readLines(file)) {
            val seq = SEQ_REGEX.find(line)?.groupValues?.getOrNull(1)?.toLongOrNull()
                ?: continue
            if (seq > maxSeq) {
                max = maxOf(max, seq)
                fresh.add(line)
            }
        }
        maxSeq = max
        return fresh
    }

    private fun latestFile(): SuFile? = runCatching {
        val names = SuFile(SULOG_DIR).list().orEmpty().mapNotNull { name ->
            val match = FILE_NAME_REGEX.matchEntire(name) ?: return@mapNotNull null
            Triple(name, LocalDate.parse(match.groupValues[1]), match.groupValues[2]
                .takeIf(String::isNotEmpty)?.toInt() ?: 0)
        }.sortedWith(
            compareByDescending<Triple<String, LocalDate, Int>> { it.second }
                .thenByDescending { it.third }
        ).map { it.first }
        names.firstOrNull()?.let { SuFile("$SULOG_DIR/$it") }?.takeIf { it.isFile }
    }.getOrNull()

    private fun readLines(file: SuFile, limit: Int = LINE_LIMIT): List<String> {
        val lines = ArrayDeque<String>(limit)
        runCatching {
            SuFileInputStream.open(file).use { input ->
                InputStreamReader(input).buffered().useLines { sequence ->
                    sequence.forEach { line ->
                        if (lines.size == limit) lines.removeFirst()
                        lines.addLast(line)
                    }
                }
            }
        }
        return lines.toList()
    }

    private fun resolveLabel(uid: Int): String = runCatching {
        val pm = packageManager
        val raw = pm.getNameForUid(uid) ?: return@runCatching "UID $uid"
        val packageName = raw.split(',', ':').firstOrNull().orEmpty()
        val info = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info)?.toString() ?: packageName
    }.getOrDefault("UID $uid")

    private fun showToast(message: String) {
        // Fallback: overlay denied (common reason 'toast not working') -> post a
        // notification so the grant is still visible instead of silently dropping.
        if (!repository.hasOverlayPermission()) {
            showFallbackNotification(message)
            return
        }
        mainHandler.post {
            hideToast()
            val context = this
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val pad = dp(12)
                setPadding(pad, dp(10), pad, dp(10))
                background = getDrawable(R.drawable.grant_toast_background)
            }
            val icon = ImageView(context).apply {
                setImageResource(R.mipmap.ic_launcher_foreground)
                val size = dp(32)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = dp(10)
                }
            }
            val text = TextView(context).apply {
                setText(message)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            }
            row.addView(icon)
            row.addView(text)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(64)
            }
            toastView = row
            runCatching { windowManager.addView(row, params) }
                .onFailure { toastView = null; return@post }
            mainHandler.removeCallbacks(hideToastRunnable)
            mainHandler.postDelayed(hideToastRunnable, TOAST_MILLIS)
        }
    }

    private fun showFallbackNotification(message: String) {
        runCatching {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_foreground)
                .setContentTitle(getString(R.string.grant_toast_channel))
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        this, 0, Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    )
                )
                .build()
            notificationManager.notify(FALLBACK_NOTIFICATION_ID + (System.currentTimeMillis() % 1000).toInt(), notification)
        }
    }

    private fun hideToast() {
        toastView?.let { view ->
            runCatching { windowManager.removeView(view) }
            toastView = null
        }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics,
        ).toInt()

    private fun createChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.grant_toast_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    companion object {
        const val ACTION_START = "com.originsu.manager.action.GRANT_TOAST_START"
        const val ACTION_STOP = "com.originsu.manager.action.GRANT_TOAST_STOP"
        private const val TAG = "GrantToastService"
        private const val CHANNEL_ID = "grant_toast_channel"
        private const val RECORD_HEADER_SIZE = 24
        private const val SULOG_EVENT_FIXED_SIZE = 52
        private const val MAX_FRAME_SIZE = 1024L * 1024L
        private const val DROPPED_TYPE = 0xFFFF
        private const val SULOG_ROOT_EXECVE = 1
        private const val SULOG_SUCOMPAT = 2
        private const val SULOG_GRANT_ROOT = 3
        private const val RECONNECT_MILLIS = 3000L
        private const val NOTIFICATION_ID = 424200
        private const val FALLBACK_NOTIFICATION_ID = 424210
        private const val POLL_MILLIS = 3000L
        private const val TOAST_MILLIS = 2500L
        private const val LINE_LIMIT = 1000
        private const val SULOG_DIR = "/data/adb/ksu/log"
        private val FILE_NAME_REGEX = Regex("""sulog-(\d{4}-\d{2}-\d{2})(?:-(\d+))?\.log""")
        private val SEQ_REGEX = Regex("""(?:^|\s)seq=(\d+)""")
    }
}
