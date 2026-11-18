package com.kazesu.manager.data.bootloop

import android.util.Log
import com.kazesu.manager.ui.util.createRootShell
import com.kazesu.manager.ui.util.execKsud
import com.kazesu.manager.ui.util.getKsuDaemonPath
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class BootloopStatus(
    val enabled: Boolean = true,
    val maxFailed: Int = 3,
    val failedCount: Int = 0,
    val rescued: Boolean = false,
    val rescuedFailedBoots: Int? = null,
)

/**
 * Native bootloop protection: ksud counts boots that reach `post-fs-data` but
 * never complete, and disables every module once the threshold is crossed.
 * The rescued marker is left behind so the manager can explain what happened.
 */
class BootloopRepository {
    companion object {
        private const val TAG = "BootloopRepository"
    }

    suspend fun getStatus(): BootloopStatus = withContext(Dispatchers.IO) {
        runCatching {
            val out = createRootShell().use { shell ->
                ShellUtils.fastCmd(shell, "${getKsuDaemonPath()} bootloop status")
            }.trim()
            val json = JSONObject(out)
            BootloopStatus(
                enabled = json.optBoolean("enabled", true),
                maxFailed = json.optInt("max_failed", 3),
                failedCount = json.optInt("failed_count", 0),
                rescued = json.optBoolean("rescued", false),
                rescuedFailedBoots = if (json.isNull("rescued_failed_boots")) {
                    null
                } else {
                    json.optInt("rescued_failed_boots")
                },
            )
        }.onFailure {
            Log.w(TAG, "read bootloop status failed", it)
        }.getOrDefault(BootloopStatus())
    }

    suspend fun setEnabled(enabled: Boolean): Boolean = runCommand(
        if (enabled) "bootloop enable" else "bootloop disable"
    )

    suspend fun setMaxFailed(count: Int): Boolean = runCommand("bootloop set-max $count")

    suspend fun clearRescued(): Boolean = runCommand("bootloop clear-rescued")

    private suspend fun runCommand(args: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { execKsud(args, true) }.getOrDefault(false)
    }
}
