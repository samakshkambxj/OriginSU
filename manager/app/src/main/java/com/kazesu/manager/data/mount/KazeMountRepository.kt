package com.kazesu.manager.data.mount

import com.kazesu.manager.ui.util.execKsud
import com.kazesu.manager.ui.util.getKsuDaemonPath
import com.kazesu.manager.ui.util.getRootShell
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Thin root-shell boundary; ksud owns every mount decision and persisted flag. */
class KazeMountRepository {
    private fun output(args: String): String = runCatching {
        getRootShell().newJob()
            .add("${getKsuDaemonPath()} $args")
            .to(ArrayList<String>(), null)
            .exec()
            .out
            .joinToString("\n")
    }.getOrDefault("")

    suspend fun status(): KazeMountStatus = withContext(Dispatchers.IO) {
        parseKazeMountStatus(output("kaze-mount status --json"))
    }

    suspend fun uidState(): NoMountUidState = withContext(Dispatchers.IO) {
        parseNoMountUidState(output("nomount uid list --json"))
    }

    suspend fun isZeroMountDriverPresent(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            ShellUtils.fastCmdResult(getRootShell(), "[ -e /dev/zeromount ]")
        }.getOrDefault(false)
    }

    suspend fun setMode(mode: KazeMountMode): Boolean = withContext(Dispatchers.IO) {
        runCatching { execKsud("kaze-mount set-mode ${mode.id}") }.getOrDefault(false)
    }

    suspend fun mountNow(): Boolean = withContext(Dispatchers.IO) {
        runCatching { execKsud("kaze-mount mount") }.getOrDefault(false)
    }

    suspend fun setUids(uids: List<Int>): Boolean = withContext(Dispatchers.IO) {
        if (uids.any { it < 0 }) return@withContext false
        val args = uids.distinct().sorted().joinToString(" ")
        runCatching { execKsud("nomount uid set $args".trim()) }.getOrDefault(false)
    }

    suspend fun setIsolated(stockVisible: Boolean): Boolean = withContext(Dispatchers.IO) {
        val state = if (stockVisible) "on" else "off"
        runCatching { execKsud("nomount uid isolated $state") }.getOrDefault(false)
    }

    suspend fun clearRules(): Boolean = withContext(Dispatchers.IO) {
        runCatching { execKsud("nomount clear") }.getOrDefault(false)
    }
}
