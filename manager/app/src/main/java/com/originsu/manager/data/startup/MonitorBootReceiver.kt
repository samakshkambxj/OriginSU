package com.originsu.manager.data.startup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.originsu.manager.data.AppSettingsRepository
import com.originsu.manager.data.grant.GrantToastRepository
import com.originsu.manager.data.grant.GrantToastService
import com.originsu.manager.data.su.SuRequestPollService
import com.originsu.manager.data.su.SuRequestRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Restarts the notification-backed monitors after a reboot. The setting store
 * is loaded first: on a cold process start the in-memory cache is empty until
 * DataStore finishes reading, and starting the services before that made them
 * see their toggle as disabled and exit.
 */
class MonitorBootReceiver : BroadcastReceiver(), KoinComponent {
    private val settings: AppSettingsRepository by inject()

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching { settings.preload() }
                if (settings.getBoolean(GrantToastRepository.PREF_GRANT_TOAST, false)) {
                    startService(context, GrantToastService::class.java, GrantToastService.ACTION_START)
                }
                if (settings.getBoolean(SuRequestRepository.PREF_SU_PROMPT, false)) {
                    startService(context, SuRequestPollService::class.java, SuRequestPollService.ACTION_START)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun startService(context: Context, service: Class<*>, action: String) {
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, service).setAction(action),
            )
        }
    }
}
