package com.originsu.manager.data.kernel

import com.originsu.manager.Natives
import com.originsu.manager.data.shell.KsuCliRepository
import com.originsu.manager.domain.model.VeilCloakedUid
import com.originsu.manager.domain.model.VeilState
import com.originsu.manager.domain.model.toProbeHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class VeilRepository(
    private val ksuCliRepository: KsuCliRepository,
    private val manage: VeilManageRepository,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(VeilState())
    val state: StateFlow<VeilState> = mutableState.asStateFlow()

    suspend fun refresh(): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            mutableState.update { it.copy(isRefreshing = !it.isLoading, errorMessage = null) }
            runCatching {
                val status = runCatching {
                    ksuCliRepository.getFeatureStatus("veil")
                }.getOrDefault("")
                val enabled = runCatching { Natives.isVeilEnabled() }.getOrDefault(false)
                val autoCloak = runCatching { Natives.isVeilAutoCloak() }.getOrDefault(false)
                val cloaked = runCatching { Natives.getVeilCloakedUids() ?: intArrayOf() }
                    .getOrDefault(intArrayOf())
                val excluded = manage.getExcludedUids()
                val history = runCatching { Natives.getVeilHistory().orEmpty() }
                    .getOrDefault(emptyList())
                val visibleCloaked = enforceExclusions(cloaked, excluded)
                val cloakedUids = visibleCloaked.sorted().map { uid ->
                    VeilCloakedUid(
                        uid = uid,
                        userName = runCatching { Natives.getUserName(uid) }.getOrNull(),
                    )
                }
                val probes = history.sortedByDescending { it.lastNs }.map { entry ->
                    entry.toProbeHistory(
                        runCatching { Natives.getUserName(entry.uid) }.getOrNull()
                    )
                }
                mutableState.value = VeilState(
                    status = status,
                    enabled = enabled,
                    autoCloak = autoCloak,
                    cloakedUids = cloakedUids,
                    excludedUids = excluded,
                    history = probes,
                    isLoading = false,
                )
            }.onFailure { error ->
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = error.message,
                    )
                }
            }
        }
    }

    suspend fun setEnabled(enabled: Boolean): Result<Unit> = mutate {
        check(Natives.setVeilEnabled(enabled))
        check(ksuCliRepository.execKsud("feature save", true))
        ksuCliRepository.persistVeil()
        mutableState.update { it.copy(enabled = enabled) }
    }

    suspend fun setAutoCloak(enabled: Boolean): Result<Unit> = mutate {
        check(Natives.setVeilAutoCloak(enabled))
        ksuCliRepository.persistVeil()
        mutableState.update { it.copy(autoCloak = enabled) }
    }

    suspend fun setCloaked(uid: Int, cloaked: Boolean): Result<Unit> = mutate {
        if (cloaked) {
            // Explicit cloaks lift the exclusion so the cloak sticks: the
            // enforcement pass below would otherwise undo it on next refresh.
            manage.setExcluded(uid, false)
        }
        check(Natives.setVeilCloaked(uid, cloaked))
        ksuCliRepository.persistVeil()
        refreshLocked()
    }

    suspend fun setExcluded(uid: Int, excluded: Boolean): Result<Unit> = mutate {
        if (excluded) {
            // Exclusion wins immediately: uncloak best-effort, then record.
            runCatching { Natives.setVeilCloaked(uid, false) }
            ksuCliRepository.persistVeil()
        }
        manage.setExcluded(uid, excluded)
        refreshLocked()
    }

    suspend fun clearCloaked(): Result<Unit> = mutate {
        check(Natives.clearVeilCloaked())
        ksuCliRepository.persistVeil()
        refreshLocked()
    }

    suspend fun clearHistory(): Result<Unit> = mutate {
        check(Natives.clearVeilHistory())
        refreshLocked()
    }

    private suspend fun mutate(block: suspend () -> Unit): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) { runCatching { block() } }
    }

    private suspend fun refreshLocked() {
        // Re-read cloak set and history after a mutation; the mutex is already held.
        val cloaked = runCatching { Natives.getVeilCloakedUids() ?: intArrayOf() }
            .getOrDefault(intArrayOf())
        val excluded = manage.getExcludedUids()
        val history = runCatching { Natives.getVeilHistory().orEmpty() }
            .getOrDefault(emptyList())
        val visibleCloaked = enforceExclusions(cloaked, excluded)
        mutableState.update { current ->
            current.copy(
                autoCloak = runCatching { Natives.isVeilAutoCloak() }
                    .getOrDefault(current.autoCloak),
                cloakedUids = visibleCloaked.sorted().map { uid ->
                    VeilCloakedUid(
                        uid = uid,
                        userName = runCatching { Natives.getUserName(uid) }.getOrNull(),
                    )
                },
                excludedUids = excluded,
                history = history.sortedByDescending { it.lastNs }.map { entry ->
                    entry.toProbeHistory(
                        runCatching { Natives.getUserName(entry.uid) }.getOrNull()
                    )
                },
            )
        }
    }

    /**
     * Uncloak any excluded uid the kernel grabbed (e.g. via auto-cloak) and
     * return the cloak set minus enforced uids. Runs on Dispatchers.IO under
     * the repository mutex in both refresh paths.
     */
    private fun enforceExclusions(cloaked: IntArray, excluded: Set<Int>): IntArray {
        val violating = cloaked.filter { it in excluded }
        if (violating.isEmpty()) return cloaked
        violating.forEach { uid ->
            runCatching { Natives.setVeilCloaked(uid, false) }
        }
        runCatching { ksuCliRepository.persistVeil() }
        return cloaked.filter { it !in excluded }.toIntArray()
    }
}
