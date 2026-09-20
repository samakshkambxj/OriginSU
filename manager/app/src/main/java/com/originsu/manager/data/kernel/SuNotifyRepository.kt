package com.originsu.manager.data.kernel

import android.app.Application
import android.content.Context
import com.originsu.manager.RootRequestReceiver
import com.originsu.manager.data.shell.KsuCliRepository

/**
 * Root-request notification state (Veil-driven su-notifyd). The enabled flag
 * lives in the receiver's prefs so the broadcast path can read it without DI.
 */
class SuNotifyRepository(
    private val application: Application,
    private val ksuCliRepository: KsuCliRepository,
) {
    private fun prefs() =
        application.getSharedPreferences(RootRequestReceiver.PREFS, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean =
        prefs().getBoolean(RootRequestReceiver.KEY_ENABLED, false)

    suspend fun setEnabled(enabled: Boolean): Boolean {
        val ok = ksuCliRepository.setSuNotify(enabled)
        if (ok) prefs().edit().putBoolean(RootRequestReceiver.KEY_ENABLED, enabled).apply()
        return ok
    }
}
