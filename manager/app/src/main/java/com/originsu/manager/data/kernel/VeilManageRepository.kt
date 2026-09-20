package com.originsu.manager.data.kernel

import android.app.Application
import android.content.Context
import com.originsu.manager.Natives
import com.originsu.manager.data.shell.KsuCliRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Per-app management for Origin Veil: permission allow/block, freeze, and
 * tracking of every change so uncloaking restores the app to default.
 */
class VeilManageRepository(
    private val application: Application,
    private val ksuCliRepository: KsuCliRepository,
) {
    private fun managedPrefs() =
        application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun packageForUid(uid: Int): String? =
        application.packageManager.getPackagesForUid(uid)?.firstOrNull()

    suspend fun getAppOpsModes(packageName: String): Map<String, String> =
        ksuCliRepository.getAppOpsModes(packageName)

    fun setPermissionMode(packageName: String, perm: String, block: Boolean) {
        ksuCliRepository.setPermissionMode(packageName, perm, block)
        val prefs = managedPrefs()
        val set = prefs.getStringSet(KEY_PERMS.format(packageName), emptySet())!!.toMutableSet()
        if (block) set.add(perm) else set.remove(perm)
        prefs.edit().putStringSet(KEY_PERMS.format(packageName), set).apply()
    }

    fun setAppEnabled(packageName: String, enabled: Boolean): Boolean {
        val ok = ksuCliRepository.setAppEnabled(packageName, enabled)
        managedPrefs().edit().putBoolean(KEY_DISABLED.format(packageName), !enabled).apply()
        return ok
    }

    fun forceStop(packageName: String) = ksuCliRepository.forceStopApp(packageName)

    suspend fun dumpAppLog(uid: Int, lines: Int = 400): List<String> =
        ksuCliRepository.dumpAppLog(uid, lines)

    /**
     * Uncloak an app and restore everything Veil changed back to default:
     * re-grant any permissions it blocked and re-enable it if it was frozen.
     */
    suspend fun uncloakRestore(uid: Int) = withContext(Dispatchers.IO) {
        Natives.setVeilCloaked(uid, false)
        ksuCliRepository.persistVeil()
        val pkg = packageForUid(uid)
        if (pkg != null) {
            val prefs = managedPrefs()
            prefs.getStringSet(KEY_PERMS.format(pkg), emptySet())?.forEach { perm ->
                ksuCliRepository.setPermissionMode(pkg, perm, false)
            }
            if (prefs.getBoolean(KEY_DISABLED.format(pkg), false)) {
                ksuCliRepository.setAppEnabled(pkg, true)
            }
            prefs.edit().remove(KEY_PERMS.format(pkg)).remove(KEY_DISABLED.format(pkg)).apply()
        }
    }

    companion object {
        private const val PREFS = "veil_managed"
        private const val KEY_PERMS = "perms_%s"
        private const val KEY_DISABLED = "disabled_%s"

        /**
         * Map a permission to its appop name, or null if it has no appop. Curated (not a
         * blind prefix strip) so callers can tell a genuinely appop-controllable perm
         * from a plain install-time one (e.g. INTERNET) that cannot be blocked at all.
         */
        fun opForPermission(perm: String): String? = when (perm) {
            "android.permission.ACCESS_FINE_LOCATION" -> "FINE_LOCATION"
            "android.permission.ACCESS_COARSE_LOCATION" -> "COARSE_LOCATION"
            "android.permission.ACCESS_BACKGROUND_LOCATION" -> "FINE_LOCATION"
            "android.permission.ACCESS_MEDIA_LOCATION" -> "ACCESS_MEDIA_LOCATION"
            "android.permission.CAMERA" -> "CAMERA"
            "android.permission.RECORD_AUDIO" -> "RECORD_AUDIO"
            "android.permission.READ_CONTACTS" -> "READ_CONTACTS"
            "android.permission.WRITE_CONTACTS" -> "WRITE_CONTACTS"
            "android.permission.READ_CALENDAR" -> "READ_CALENDAR"
            "android.permission.WRITE_CALENDAR" -> "WRITE_CALENDAR"
            "android.permission.READ_SMS" -> "READ_SMS"
            "android.permission.SEND_SMS" -> "SEND_SMS"
            "android.permission.RECEIVE_SMS" -> "RECEIVE_SMS"
            "android.permission.READ_PHONE_STATE" -> "READ_PHONE_STATE"
            "android.permission.READ_PHONE_NUMBERS" -> "READ_PHONE_NUMBERS"
            "android.permission.CALL_PHONE" -> "CALL_PHONE"
            "android.permission.READ_CALL_LOG" -> "READ_CALL_LOG"
            "android.permission.WRITE_CALL_LOG" -> "WRITE_CALL_LOG"
            "android.permission.READ_EXTERNAL_STORAGE" -> "READ_EXTERNAL_STORAGE"
            "android.permission.WRITE_EXTERNAL_STORAGE" -> "WRITE_EXTERNAL_STORAGE"
            "android.permission.READ_MEDIA_IMAGES" -> "READ_MEDIA_IMAGES"
            "android.permission.READ_MEDIA_VIDEO" -> "READ_MEDIA_VIDEO"
            "android.permission.READ_MEDIA_AUDIO" -> "READ_MEDIA_AUDIO"
            "android.permission.BODY_SENSORS" -> "BODY_SENSORS"
            "android.permission.ACTIVITY_RECOGNITION" -> "ACTIVITY_RECOGNITION"
            "android.permission.POST_NOTIFICATIONS" -> "POST_NOTIFICATION"
            "android.permission.SYSTEM_ALERT_WINDOW" -> "SYSTEM_ALERT_WINDOW"
            "android.permission.WRITE_SETTINGS" -> "WRITE_SETTINGS"
            "android.permission.PACKAGE_USAGE_STATS" -> "GET_USAGE_STATS"
            "android.permission.REQUEST_INSTALL_PACKAGES" -> "REQUEST_INSTALL_PACKAGES"
            "android.permission.SCHEDULE_EXACT_ALARM" -> "SCHEDULE_EXACT_ALARM"
            else -> null
        }

        /** Whether a permission can actually be blocked (runtime revoke or an appop). */
        fun isPermissionBlockable(perm: String, dangerous: Boolean): Boolean =
            dangerous || opForPermission(perm) != null
    }
}
