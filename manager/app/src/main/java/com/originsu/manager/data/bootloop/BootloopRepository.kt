package com.originsu.manager.data.bootloop

import android.util.Log
import com.originsu.manager.data.shell.KsuCliRepository
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

class BootloopRepository(
    private val ksuCliRepository: KsuCliRepository,
) {
    companion object {
        private const val TAG = "BootloopRepository"
    }

    suspend fun getStatus(): BootloopStatus = withContext(Dispatchers.IO) {
        runCatching {
            val shell = ksuCliRepository.getRootShell()
            val out = shell.newJob()
                .add("${ksuCliRepository.getKsuDaemonPath()} bootloop status")
                .to(ArrayList<String>(), null)
                .exec().out.joinToString("\n").trim()
            val json = JSONObject(out)
            BootloopStatus(
                enabled = json.optBoolean("enabled", true),
                maxFailed = json.optInt("max_failed", 3),
                failedCount = json.optInt("failed_count", 0),
                rescued = json.optBoolean("rescued", false),
                rescuedFailedBoots = json.takeIf { it.optBoolean("rescued", false) }
                    ?.optInt("rescued_failed_boots", -1)
                    ?.takeIf { it >= 0 },
            )
        }.getOrElse { error ->
            Log.w(TAG, "Failed to read bootloop status", error)
            BootloopStatus()
        }
    }

    suspend fun setEnabled(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        ksuCliRepository.execKsud(
            if (enabled) "bootloop enable" else "bootloop disable",
            newShell = true,
        )
    }

    suspend fun setMax(count: Int): Boolean = withContext(Dispatchers.IO) {
        if (count !in 2..10) return@withContext false
        ksuCliRepository.execKsud("bootloop set-max $count", newShell = true)
    }

    suspend fun clearRescued(): Boolean = withContext(Dispatchers.IO) {
        ksuCliRepository.execKsud("bootloop clear-rescued", newShell = true)
    }
}
