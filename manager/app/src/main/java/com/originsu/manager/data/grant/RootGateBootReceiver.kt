package com.originsu.manager.data.grant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Restarts lock-bound root protection after a reboot. */
class RootGateBootReceiver : BroadcastReceiver(), KoinComponent {
    private val repository: RootGateRepository by inject()

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        runCatching { repository.restorePanicIfNeeded() }
        if (!repository.isLockBoundEnabled()) return
        runCatching { repository.startService() }
    }
}
