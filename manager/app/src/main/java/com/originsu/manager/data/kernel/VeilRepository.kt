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
                val history = runCatching { Natives.getVeilHistory().orEmpty() }
                    .getOrDefault(emptyList())
                val cloakedUids = cloaked.sorted().map { uid ->
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
        check(Natives.setVeilCloaked(uid, cloaked))
        ksuCliRepository.persistVeil()
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
        val history = runCatching { Natives.getVeilHistory().orEmpty() }
            .getOrDefault(emptyList())
        mutableState.update { current ->
            current.copy(
                autoCloak = runCatching { Natives.isVeilAutoCloak() }
                    .getOrDefault(current.autoCloak),
                cloakedUids = cloaked.sorted().map { uid ->
                    VeilCloakedUid(
                        uid = uid,
                        userName = runCatching { Natives.getUserName(uid) }.getOrNull(),
                    )
                },
                history = history.sortedByDescending { it.lastNs }.map { entry ->
                    entry.toProbeHistory(
                        runCatching { Natives.getUserName(entry.uid) }.getOrNull()
                    )
                },
            )
        }
    }
}
