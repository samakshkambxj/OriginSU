package com.originsu.manager.data.sui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.originsu.manager.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.shizuku.server.IShizukuApplication

data class SuiPermissionRequest(
    val uid: Int,
    val pid: Int,
    val packageName: String,
    val requestCode: Int,
)

/** The answers the manager can send back, in Sui's own vocabulary. */
enum class SuiPermissionDecision(val allowed: Boolean, val shell: Boolean, val onetime: Boolean) {
    ALLOW_ROOT(allowed = true, shell = false, onetime = false),
    ALLOW_SHELL(allowed = true, shell = true, onetime = false),
    ALLOW_ONCE(allowed = true, shell = false, onetime = true),
    DENY(allowed = false, shell = false, onetime = false),
    DISMISS(allowed = false, shell = false, onetime = true),
}

/**
 * Hosts Sui's permission prompts. Sui used to render these inside SystemUI; OriginSU is the
 * registered manager now, so the requests arrive here and the answer goes back through the
 * same management channel.
 */
object SuiPermissionHost {
    private const val TAG = "SuiManagement"
    private const val API_VERSION = 13
    private const val PACKAGE_NAME = "com.originsu.manager"

    private const val KEY_PACKAGE_NAME = "shizuku:attach-package-name"
    private const val KEY_API_VERSION = "shizuku:attach-api-version"
    private const val KEY_HANDOFF = "shizuku:attach-supports-server-binder-handoff"
    private const val KEY_ALLOWED = "shizuku:request-permission-reply-allowed"
    private const val KEY_ONETIME = "shizuku:request-permission-reply-is-onetime"
    private const val KEY_SHELL = "shizuku:request-permission-reply-is-shell"
    private const val CHANNEL_ID = "sui_permission_requests"

    private val _pending = MutableStateFlow<SuiPermissionRequest?>(null)
    val pending: StateFlow<SuiPermissionRequest?> = _pending.asStateFlow()

    private var registered = false
    private var appContext: Context? = null

    /** Called from the application so background requests can notify before any screen opens. */
    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    /** Remembers an application context so background requests can raise a notification. */
    fun register(context: Context): Boolean {
        attach(context)
        return register()
    }

    /** Registers this app as the host Sui asks for confirmation; safe to call repeatedly. */
    fun register(): Boolean {
        if (registered) return true
        val service = SuiBridge.service() ?: return false
        return try {
            service.attachApplication(
                application,
                Bundle().apply {
                    putString(KEY_PACKAGE_NAME, PACKAGE_NAME)
                    putInt(KEY_API_VERSION, API_VERSION)
                    putBoolean(KEY_HANDOFF, false)
                },
            )
            registered = true
            Log.i(TAG, "registered as the Sui permission host")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "cannot register as the Sui permission host: $t")
            false
        }
    }

    /** Answers the pending request; a no-op when nothing is waiting. */
    fun reply(decision: SuiPermissionDecision) {
        val request = _pending.value ?: return
        answer(request, decision)
    }

    private fun answer(request: SuiPermissionRequest, decision: SuiPermissionDecision) {
        _pending.value = null
        Log.i(
            TAG,
            "answering ${request.packageName}: allowed=${decision.allowed} " +
                "shell=${decision.shell} onetime=${decision.onetime}",
        )
        try {
            SuiBridge.service()?.dispatchPermissionConfirmationResult(
                request.uid,
                request.pid,
                request.requestCode,
                Bundle().apply {
                    putBoolean(KEY_ALLOWED, decision.allowed)
                    putBoolean(KEY_ONETIME, decision.onetime)
                    putBoolean(KEY_SHELL, decision.shell)
                },
            )
        } catch (t: Throwable) {
            Log.w(TAG, "cannot deliver the permission answer: $t")
        }
    }

    /** Used when the app is not in front, because Android blocks background activity starts. */
    fun postNotification(context: Context, request: SuiPermissionRequest) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!notificationsEnabled(context)) {
            // Without the grant the prompt still waits in the app; only the heads-up is lost.
            Log.i(TAG, "notifications are disabled; the Sui prompt is kept in the app")
            return
        }
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.sui_notification_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
        val intent = Intent(context, Class.forName("com.originsu.manager.ui.MainActivity"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.sui_notification_title))
            .setContentText(context.getString(R.string.sui_notification_message, request.packageName))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        try {
            manager.notify(request.requestCode, notification)
        } catch (t: Throwable) {
            Log.w(TAG, "cannot post the Sui request notification: $t")
        }
    }

    fun notificationsEnabled(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true

    /** Verification hook: lets a debug launch show the prompt without a requesting app. */
    fun fake(packageName: String) {
        val request = SuiPermissionRequest(10547, 0, packageName, 1)
        _pending.value = request
        appContext?.let { postNotification(it, request) }
    }

    private val application = object : IShizukuApplication.Stub() {
        override fun bindApplication(data: Bundle?) = Unit

        override fun dispatchRequestPermissionResult(requestCode: Int, data: Bundle?) = Unit

        override fun showPermissionConfirmation(
            requestUid: Int,
            requestPid: Int,
            requestPackageName: String?,
            requestCode: Int,
        ) {
            Log.i(TAG, "permission request from ${requestPackageName.orEmpty()} (uid=$requestUid)")
            val request = SuiPermissionRequest(
                uid = requestUid,
                pid = requestPid,
                packageName = requestPackageName.orEmpty(),
                requestCode = requestCode,
            )
            _pending.value = request
            // The app may not be in front, and Android blocks background activity starts here.
            appContext?.let { postNotification(it, request) }
        }

        override fun dispatchServerBinder(
            binder: IBinder?,
            packageName: String?,
            generation: Long,
        ): Boolean = false
    }
}
