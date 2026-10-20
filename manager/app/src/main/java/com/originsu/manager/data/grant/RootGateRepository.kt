package com.originsu.manager.data.grant

import android.app.AlarmManager
import android.app.PendingIntent
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
        val success = runCatching { Natives.setRootGateEnabled(enabled) }.getOrDefault(false)
        if (enabled && success) clearPanicState()
        return success
    }

    /**
     * Suspends root for [durationMillis] and schedules an automatic resume.
     * Unlike a plain [setRootGateEnabled] call this survives a manager restart
     * and is restored after a reboot.
     */
    fun startPanic(durationMillis: Long): Boolean {
        val until = System.currentTimeMillis() + durationMillis
        appSettings.putLong(PREF_PANIC_UNTIL, until)
        if (!setRootGateEnabled(false)) {
            clearPanicState()
            return false
        }
        schedulePanic(until)
        return true
    }

    /** Cancels an active panic and restores root immediately. */
    fun endPanic(): Boolean {
        if (panicUntil() <= 0L) return false
        return setRootGateEnabled(true)
    }

    /** Restores root only when the scheduled panic deadline has passed. */
    fun endPanicIfDue(): Boolean {
        val until = panicUntil()
        if (until <= 0L || until > System.currentTimeMillis()) return false
        return setRootGateEnabled(true)
    }

    fun panicUntil(): Long = appSettings.getLong(PREF_PANIC_UNTIL, 0L)

    fun isPanicActive(): Boolean = panicUntil() > System.currentTimeMillis()

    /** Drops a pending panic without touching the kernel gate. */
    fun clearPanic() {
        clearPanicState()
    }

    /**
     * Called after boot: the kernel gate resets to allowed, so an unfinished
     * panic has to be applied again until its original deadline.
     */
    fun restorePanicIfNeeded() {
        val until = panicUntil()
        if (until <= 0L) return
        if (until <= System.currentTimeMillis()) {
            clearPanicState()
            return
        }
        if (setRootGateEnabled(false)) {
            schedulePanic(until)
        } else {
            clearPanicState()
        }
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

    private fun schedulePanic(until: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                until,
                panicPendingIntent(),
            )
        }
    }

    private fun clearPanicState() {
        appSettings.putLong(PREF_PANIC_UNTIL, 0L)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = panicPendingIntent()
        runCatching { alarmManager?.cancel(pendingIntent) }
        pendingIntent.cancel()
    }

    private fun panicPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        PANIC_REQUEST_CODE,
        Intent(context, RootGatePanicReceiver::class.java)
            .setAction(RootGatePanicReceiver.ACTION_END_PANIC),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val PREF_LOCK_BOUND = "root_gate_lock_bound"
        const val PREF_SUSPENDED_BY_LOCK = "root_gate_suspended_by_lock"
        const val PREF_PANIC_UNTIL = "root_gate_panic_until"
        private const val PANIC_REQUEST_CODE = 0x52A8
    }
}
