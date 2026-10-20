package com.originsu.manager.data.grant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Restores root when a timed Root Gate panic reaches its deadline. */
class RootGatePanicReceiver : BroadcastReceiver(), KoinComponent {
    private val repository: RootGateRepository by inject()

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != ACTION_END_PANIC) return
        runCatching { repository.endPanicIfDue() }
    }

    companion object {
        const val ACTION_END_PANIC = "com.originsu.manager.root_gate.END_PANIC"
    }
}
