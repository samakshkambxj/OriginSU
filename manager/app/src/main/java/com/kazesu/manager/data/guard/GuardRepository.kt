package com.kazesu.manager.data.guard

import android.util.Log
import com.kazesu.manager.ui.util.createRootShell
import com.kazesu.manager.ui.util.execKsud
import com.kazesu.manager.ui.util.getKsuDaemonPath
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * KazeGuard pre-install audit: the enabled flag and the remote rule catalog
 * live in ksud, so the manager only forwards commands.
 */
class GuardRepository {
    companion object {
        private const val TAG = "GuardRepository"
    }

    suspend fun isEnabled(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            createRootShell().use { shell ->
                ShellUtils.fastCmd(shell, "${getKsuDaemonPath()} module guard status")
            }.trim()
        }.onFailure {
            Log.w(TAG, "read KazeGuard status failed", it)
        }.getOrDefault("Enabled").equals("Enabled", ignoreCase = true)
    }

    suspend fun setEnabled(enabled: Boolean): Boolean = runCommand(
        if (enabled) "module guard enable" else "module guard disable"
    )

    suspend fun updateRules(): Boolean = runCommand("module guard update")

    private suspend fun runCommand(args: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { execKsud(args, true) }.getOrDefault(false)
    }
}
