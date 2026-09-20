package com.originsu.manager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.originsu.manager.data.shell.KsuCliRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.concurrent.thread

/**
 * Raises a root-request notification (Grant / Cloak / Ignore) and handles those
 * three action buttons. The su-notifyd daemon (which watches Veil's su-probe
 * history) broadcasts here to post the notification; taps come back here too.
 *   Grant  -> add the app to the superuser allowlist
 *   Cloak  -> add the app to the Veil cloak list
 *   Ignore -> dismiss
 */
class RootRequestReceiver : BroadcastReceiver(), KoinComponent {

    private val ksuCliRepository: KsuCliRepository by inject()

    companion object {
        const val PREFS = "su_notify"
        const val KEY_ENABLED = "enabled"
        private const val CHANNEL_ID = "root_requests"
        private const val EXTRA_ACTION = "su_action"
        private const val EXTRA_UID = "uid"

        /** Post the 3-action root-request notification for [uid]. Called after
         *  filtering (enabled / not cloaked) in onReceive. */
        fun postNotification(context: Context, uid: Int) {
            val pm = context.packageManager
            val pkg = pm.getPackagesForUid(uid)?.firstOrNull()
            val label = pkg?.let {
                runCatching { pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString() }.getOrNull()
            } ?: context.getString(R.string.veil_uid_title, uid)

            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.su_notify_channel), NotificationManager.IMPORTANCE_HIGH)
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(context.getString(R.string.su_notify_title))
                .setContentText(context.getString(R.string.su_notify_text, label))
                .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.su_notify_text, label)))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .addAction(0, context.getString(R.string.su_notify_grant), action(context, uid, "grant"))
                .addAction(0, context.getString(R.string.su_notify_cloak), action(context, uid, "cloak"))
                .addAction(0, context.getString(R.string.su_notify_ignore), action(context, uid, "ignore"))
                .build()

            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                NotificationManagerCompat.from(context).notify(uid, notification) // dedup per uid
            }
        }

        private fun action(context: Context, uid: Int, what: String): PendingIntent {
            val intent = Intent(context, RootRequestReceiver::class.java)
                .putExtra(EXTRA_ACTION, what)
                .putExtra(EXTRA_UID, uid)
            // Unique request code per uid+action so PendingIntents don't collide.
            val code = uid * 8 + when (what) { "grant" -> 1; "cloak" -> 2; else -> 3 }
            return PendingIntent.getBroadcast(
                context, code, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val uid = intent.getIntExtra(EXTRA_UID, -1)
        if (uid < 0) return

        when (intent.getStringExtra(EXTRA_ACTION)) {
            "grant" -> {
                cancel(context, uid)
                runRoot(context, uid, granted = true)
            }
            "cloak" -> {
                cancel(context, uid)
                runRoot(context, uid, granted = false)
            }
            "ignore" -> cancel(context, uid)
            // No action extra = a root request from the su-notifyd daemon.
            else -> {
                val on = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getBoolean(KEY_ENABLED, false)
                if (!on) return
                // Defense in depth: re-check the cloak list here (off the main
                // thread) so a CLOAKED app is NEVER shown a notification, even if
                // the daemon's filter ever raced with cloak restore at boot.
                val pending = goAsync()
                thread {
                    try {
                        if (!ksuCliRepository.isVeilCloaked(uid)) postNotification(context, uid)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private fun cancel(context: Context, uid: Int) {
        NotificationManagerCompat.from(context).cancel(uid)
    }

    // Grant (allowlist) or Cloak (Veil) need root work; run off the main thread.
    private fun runRoot(context: Context, uid: Int, granted: Boolean) {
        val pm = context.packageManager
        val pkg = pm.getPackagesForUid(uid)?.firstOrNull()
        val label = pkg?.let {
            runCatching { pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString() }.getOrNull()
        } ?: context.getString(R.string.veil_uid_title, uid)
        val pending = goAsync()
        thread {
            try {
                val ok = if (granted) {
                    pkg != null && Natives.setAppProfile(
                        Natives.getAppProfile(pkg, uid).copy(allowSu = true, currentUid = uid)
                    )
                } else {
                    ksuCliRepository.setVeilCloaked(uid)
                }
                android.os.Handler(context.mainLooper).post {
                    val msg = when {
                        granted && ok -> context.getString(R.string.su_notify_granted_toast, label)
                        !granted && ok -> context.getString(R.string.su_notify_cloaked_toast, label)
                        else -> context.getString(R.string.su_notify_failed_toast, label)
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
