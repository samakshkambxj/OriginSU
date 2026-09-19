package com.originsu.manager.data.grant

import android.app.Application
import com.originsu.manager.data.shell.KsuCliRepository
import com.originsu.manager.domain.model.TempGrantDuration
import com.originsu.manager.domain.model.TempGrantInfo
import com.originsu.manager.domain.model.TempGrantState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class TempGrantRepository(
    private val application: Application,
    private val ksuCliRepository: KsuCliRepository,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(TempGrantState())
    val state: StateFlow<TempGrantState> = mutableState.asStateFlow()

    suspend fun refresh(): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val now = System.currentTimeMillis() / 1000L
                val grants = ksuCliRepository.listTempGrants().map { record ->
                    TempGrantInfo(
                        uid = record.uid,
                        packageName = record.packageName,
                        label = resolveLabel(record.packageName),
                        remainingSecs = (record.expiresAtEpoch - now).coerceAtLeast(0L),
                    )
                }.sortedBy { it.remainingSecs }
                mutableState.value = TempGrantState(grants = grants, isLoading = false)
            }.onFailure { error ->
                mutableState.update {
                    it.copy(isLoading = false, errorMessage = error.message)
                }
            }
        }
    }

    suspend fun grant(packageName: String, uid: Int, duration: TempGrantDuration): Result<Unit> =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    check(
                        ksuCliRepository.grantTempRoot(packageName, uid, duration.seconds)
                    )
                }
            }
        }.onSuccess { refresh() }

    suspend fun revoke(uid: Int): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching { check(ksuCliRepository.revokeTempGrant(uid)) }
        }
    }.onSuccess { refresh() }

    fun isTempGranted(uid: Int): Boolean =
        mutableState.value.grants.any { it.uid == uid }

    fun remainingSecs(uid: Int): Long? =
        mutableState.value.grants.firstOrNull { it.uid == uid }?.remainingSecs

    private fun resolveLabel(packageName: String): String? = runCatching {
        val pm = application.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info)?.toString()
    }.getOrNull()
}
