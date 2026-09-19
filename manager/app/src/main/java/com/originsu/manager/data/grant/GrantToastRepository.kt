package com.originsu.manager.data.grant

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.originsu.manager.data.AppSettingsRepository

class GrantToastRepository(
    private val context: Context,
    private val appSettings: AppSettingsRepository,
) {
    fun isToastEnabled(): Boolean = appSettings.getBoolean(PREF_GRANT_TOAST, false)

    fun setToastEnabled(enabled: Boolean) {
        appSettings.putBoolean(PREF_GRANT_TOAST, enabled)
        if (enabled) {
            startMonitor()
        } else {
            stopMonitor()
        }
    }

    fun isTempGrantEnabled(): Boolean = appSettings.getBoolean(PREF_TEMP_GRANT, false)

    fun setTempGrantEnabled(enabled: Boolean) {
        appSettings.putBoolean(PREF_TEMP_GRANT, enabled)
    }

    fun hasOverlayPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    fun overlayPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun startMonitor() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, GrantToastService::class.java)
                .setAction(GrantToastService.ACTION_START),
        )
    }

    fun stopMonitor() {
        context.startService(
            Intent(context, GrantToastService::class.java)
                .setAction(GrantToastService.ACTION_STOP),
        )
    }

    fun lastToastAt(uid: Int): Long =
        appSettings.getLong("$PREF_GRANT_TOAST_LAST$uid", 0L)

    fun markToasted(uid: Int, atMillis: Long) {
        appSettings.putLong("$PREF_GRANT_TOAST_LAST$uid", atMillis)
    }

    companion object {
        const val PREF_GRANT_TOAST = "grant_toast_enabled"
        const val PREF_TEMP_GRANT = "temp_grant_enabled"
        const val THROTTLE_MILLIS = 10L * 60L * 1000L
        private const val PREF_GRANT_TOAST_LAST = "grant_toast_last_"
    }
}
