package com.originsu.manager.data.grant

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.originsu.manager.Natives
import com.originsu.manager.data.AppSettingsRepository

/**
 * Root Gate: a kernel-side master switch that suspends every root grant while
 * leaving the manager itself able to turn it back on.
 *
 * The "lock bound" option keeps a foreground service alive that suspends root
 * when the screen locks and resumes it once the user unlocks the device.
 */
class RootGateRepository(
    private val context: Context,
    private val appSettings: AppSettingsRepository,
) {
    fun isRootGateEnabled(): Boolean =
        runCatching { Natives.isRootGateEnabled() }.getOrDefault(true)

    /** Returns true when the kernel accepted the change. */
    fun setRootGateEnabled(enabled: Boolean): Boolean {
        appSettings.putBoolean(PREF_SUSPENDED_BY_LOCK, false)
        return runCatching { Natives.setRootGateEnabled(enabled) }.getOrDefault(false)
    }

    fun isLockBoundEnabled(): Boolean =
        appSettings.getBoolean(PREF_LOCK_BOUND, false)

    fun setLockBoundEnabled(enabled: Boolean) {
        appSettings.putBoolean(PREF_LOCK_BOUND, enabled)
        if (enabled) {
            startService()
        } else {
            stopService()
        }
    }

    fun startService() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, RootGateService::class.java)
                .setAction(RootGateService.ACTION_START),
        )
    }

    fun stopService() {
        context.startService(
            Intent(context, RootGateService::class.java)
                .setAction(RootGateService.ACTION_STOP),
        )
    }

    fun markSuspendedByLock(suspended: Boolean) {
        appSettings.putBoolean(PREF_SUSPENDED_BY_LOCK, suspended)
    }

    fun wasSuspendedByLock(): Boolean =
        appSettings.getBoolean(PREF_SUSPENDED_BY_LOCK, false)

    companion object {
        const val PREF_LOCK_BOUND = "root_gate_lock_bound"
        const val PREF_SUSPENDED_BY_LOCK = "root_gate_suspended_by_lock"
    }
}
