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
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
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
        serviceScope.cancel()
        mainHandler.removeCallbacks(hideToastRunnable)
        hideToast()
        super.onDestroy()
    }

    private fun startMonitoring() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
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
        if (monitorJob?.isActive == true) return
        monitorJob = serviceScope.launch {
            // Seek to the end first so old grants never toast.
            maxSeq = readLatestMaxSeq()
            while (isActive) {
                runCatching { pollOnce() }
                delay(POLL_MILLIS)
            }
        }
    }

    private fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
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
            val last = repository.lastToastAt(uid)
            if (now - last < GrantToastRepository.THROTTLE_MILLIS) continue
            repository.markToasted(uid, now)
            val label = resolveLabel(uid)
            showToast(getString(R.string.grant_toast_granted, label))
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
        if (!repository.hasOverlayPermission()) return
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
                setImageResource(R.drawable.ic_launcher_foreground)
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
        private const val CHANNEL_ID = "grant_toast_channel"
        private const val NOTIFICATION_ID = 424200
        private const val POLL_MILLIS = 3000L
        private const val TOAST_MILLIS = 2500L
        private const val LINE_LIMIT = 1000
        private const val SULOG_DIR = "/data/adb/ksu/log"
        private val FILE_NAME_REGEX = Regex("""sulog-(\d{4}-\d{2}-\d{2})(?:-(\d+))?\.log""")
        private val SEQ_REGEX = Regex("""(?:^|\s)seq=(\d+)""")
    }
}
