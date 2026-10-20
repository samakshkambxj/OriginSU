package com.originsu.manager.data.grant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Re-validates pinned signing certificates whenever an installed package
 * changes, so an updated APK cannot keep an old root grant.
 */
class GrantSignerReceiver : BroadcastReceiver(), KoinComponent {
    private val repository: GrantSignerRepository by inject()

    override fun onReceive(context: Context?, intent: Intent?) {
        val packageName = intent?.data?.schemeSpecificPart ?: return
        val uid = intent.getIntExtra(Intent.EXTRA_UID, -1)
        if (uid < 0) return

        when (intent.action) {
            Intent.ACTION_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_ADDED,
            -> {
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        repository.verifyPackage(packageName, uid)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
