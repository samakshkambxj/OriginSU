package com.originsu.manager.data.grant

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.originsu.manager.R
import com.originsu.manager.ui.MainActivity
import org.koin.android.ext.android.inject

/**
 * Applies the "suspend root while locked" policy. Kept as a foreground service
 * so the screen on/off receiver is registered even when the activity is gone.
 */
class RootGateService : Service() {
    private val repository: RootGateRepository by inject()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> suspendForLock()
                Intent.ACTION_USER_PRESENT -> resumeAfterUnlock()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenReceiver, filter)
        }
        startForegroundCompat()
        // Starting while already locked must not leave root available.
        if (isLocked() && repository.isRootGateEnabled()) {
            suspendForLock()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            resumeAfterUnlock()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        resumeAfterUnlock()
        super.onDestroy()
    }

    private fun suspendForLock() {
        if (!repository.isLockBoundEnabled()) return
        if (!repository.isRootGateEnabled()) return
        if (repository.setRootGateEnabled(false)) {
            repository.markSuspendedByLock(true)
        }
    }

    private fun resumeAfterUnlock() {
        if (!repository.wasSuspendedByLock()) return
        // A timed panic must keep root suspended even when the device unlocks.
        if (repository.isPanicActive()) return
        if (repository.setRootGateEnabled(true)) {
            repository.markSuspendedByLock(false)
        }
    }

    private fun isLocked(): Boolean {
        val keyguard = getSystemService(KeyguardManager::class.java)
        return keyguard?.isKeyguardLocked == true
    }

    private fun startForegroundCompat() {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(getString(R.string.root_gate_notification_title))
            .setContentText(getString(R.string.root_gate_notification_summary))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.root_gate_notification_channel),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            setShowBadge(false)
            description = getString(R.string.root_gate_notification_summary)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.originsu.manager.root_gate.START"
        const val ACTION_STOP = "com.originsu.manager.root_gate.STOP"
        private const val CHANNEL_ID = "root_gate"
        private const val NOTIFICATION_ID = 0x52A7
    }
}
