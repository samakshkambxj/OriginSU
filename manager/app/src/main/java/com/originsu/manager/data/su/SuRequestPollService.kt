package com.originsu.manager.data.su

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.originsu.manager.Natives
import com.originsu.manager.R
import com.originsu.manager.ui.SuRequestActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class SuRequestPollService : Service() {
    private val repository: SuRequestRepository by inject()

    private val serviceScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    private var pollJob: Job? = null
    private lateinit var notificationManager: NotificationManager
    private val seenIds = LinkedHashSet<Long>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopPolling()
                return START_NOT_STICKY
            }
            else -> startPolling()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        pollJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startPolling() {
        val notification = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.su_request_title))
            .setContentText(getString(R.string.settings_su_prompt_summary))
            .setOngoing(true)
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
        if (pollJob?.isActive == true) return
        repository.syncToKernel()
        pollJob = serviceScope.launch {
            while (isActive) {
                runCatching { pollOnce() }
                delay(POLL_MILLIS)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun pollOnce() {
        val req = runCatching { Natives.pollSuRequest() }.getOrNull() ?: return
        synchronized(seenIds) {
            if (!seenIds.add(req.id)) return
            if (seenIds.size > 64) seenIds.remove(seenIds.first())
        }
        val pm = packageManager
        val raw = runCatching { pm.getNameForUid(req.uid) }.getOrNull()
        val packageName = raw?.split(',', ':')?.firstOrNull().orEmpty()
        val label = runCatching {
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info)?.toString()
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: packageName.ifBlank { "UID ${req.uid}" }

        val fullIntent = Intent(this, SuRequestActivity::class.java)
            .setAction("${ACTION_PROMPT}_${req.id}")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            .putExtra(SuRequestActivity.EXTRA_REQUEST_ID, req.id)
            .putExtra(SuRequestActivity.EXTRA_UID, req.uid)
            .putExtra(SuRequestActivity.EXTRA_PID, req.pid)
            .putExtra(SuRequestActivity.EXTRA_PACKAGE, packageName)
            .putExtra(SuRequestActivity.EXTRA_LABEL, label)
        // Activity launch may be blocked in background on Android 10+; the
        // notification below guarantees the user still gets a tap target.
        runCatching { startActivity(fullIntent) }

        val tapIntent = PendingIntent.getActivity(
            this, req.id.toInt(),
            Intent(this, SuRequestActivity::class.java)
                .setAction("${ACTION_PROMPT}_${req.id}")
                .putExtra(SuRequestActivity.EXTRA_REQUEST_ID, req.id)
                .putExtra(SuRequestActivity.EXTRA_UID, req.uid)
                .putExtra(SuRequestActivity.EXTRA_PID, req.pid)
                .putExtra(SuRequestActivity.EXTRA_PACKAGE, packageName)
                .putExtra(SuRequestActivity.EXTRA_LABEL, label),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val prompt = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.su_request_notification_title, label))
            .setContentText(getString(R.string.su_request_notification_text))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(tapIntent, true)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setTimeoutAfter(REQUEST_TIMEOUT_MILLIS)
            .build()
        notificationManager.notify(REQUEST_NOTIFICATION_BASE + (req.id % 1000).toInt(), prompt)
    }

    private fun createChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.su_request_channel),
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
    }

    companion object {
        const val ACTION_START = "com.originsu.manager.action.SU_REQUEST_START"
        const val ACTION_STOP = "com.originsu.manager.action.SU_REQUEST_STOP"
        private const val ACTION_PROMPT = "com.originsu.manager.action.SU_REQUEST_PROMPT"
        private const val CHANNEL_ID = "su_request_channel"
        private const val NOTIFICATION_ID = 424201
        private const val REQUEST_NOTIFICATION_BASE = 424300
        private const val POLL_MILLIS = 1000L
        private const val REQUEST_TIMEOUT_MILLIS = 32000L
    }
}
