package com.originsu.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.originsu.manager.data.packageinfo.InstalledPackageRepository
import com.originsu.manager.data.shell.KsuCliRepository
import com.originsu.manager.domain.model.InstalledPackageInfo
import com.originsu.manager.domain.model.NomountUidState
import com.originsu.manager.domain.model.OriginMountMode
import com.originsu.manager.domain.model.OriginMountStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OriginMountUiState(
    val status: OriginMountStatus? = null,
    val uidState: NomountUidState = NomountUidState(),
    val apps: List<InstalledPackageInfo> = emptyList(),
    val zeromountDriverPresent: Boolean = false,
    val zeromountVersion: String = "",
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val loadingApps: Boolean = false,
    val lastCheckedAt: Long? = null,
)

sealed interface OriginMountUiAction {
    data object Refresh : OriginMountUiAction
    data class SetMode(val mode: OriginMountMode) : OriginMountUiAction
    data class SetMagicMountEnabled(val enabled: Boolean) : OriginMountUiAction
    data class SetMagicMountBackend(val backend: String) : OriginMountUiAction
    data object MountNow : OriginMountUiAction
    data object RefreshUids : OriginMountUiAction
    data class SetUids(val uids: List<Int>) : OriginMountUiAction
    data class SetIsolated(val stockVisible: Boolean) : OriginMountUiAction
    data object ClearRules : OriginMountUiAction
    data object LoadApps : OriginMountUiAction
}

sealed interface OriginMountUiEvent {
    data object RefreshFailed : OriginMountUiEvent
    data object ModeChanged : OriginMountUiEvent
    data object MagicMountChanged : OriginMountUiEvent
    data class MagicMountFailed(val message: String) : OriginMountUiEvent
    data object MountSucceeded : OriginMountUiEvent
    data class MountFailed(val message: String) : OriginMountUiEvent
    data object UidsUpdated : OriginMountUiEvent
    data class UidsFailed(val message: String) : OriginMountUiEvent
    data object RulesCleared : OriginMountUiEvent
    data class ActionFailed(val message: String) : OriginMountUiEvent
}

class OriginMountViewModel(
    private val ksuCli: KsuCliRepository,
    private val installedPackageRepository: InstalledPackageRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(OriginMountUiState())
    private val mutableEvents = MutableSharedFlow<OriginMountUiEvent>(extraBufferCapacity = 4)

    val state: StateFlow<OriginMountUiState> = mutableState.asStateFlow()
    val events: SharedFlow<OriginMountUiEvent> = mutableEvents.asSharedFlow()

    fun dispatch(action: OriginMountUiAction) {
        when (action) {
            OriginMountUiAction.Refresh -> refresh(showLoading = true)

            is OriginMountUiAction.SetMode -> perform(
                operation = { ksuCli.setOriginMountMode(action.mode) },
                success = OriginMountUiEvent.ModeChanged,
            )

            is OriginMountUiAction.SetMagicMountEnabled -> perform(
                operation = { ksuCli.setMagicMountEnabled(action.enabled) },
                success = OriginMountUiEvent.MagicMountChanged,
                failure = { message -> OriginMountUiEvent.MagicMountFailed(message) },
            )

            is OriginMountUiAction.SetMagicMountBackend -> perform(
                operation = { ksuCli.setMagicMountBackend(action.backend) },
                success = OriginMountUiEvent.MagicMountChanged,
                failure = { message -> OriginMountUiEvent.MagicMountFailed(message) },
            )

            OriginMountUiAction.MountNow -> perform(
                operation = { ksuCli.runOriginMount() },
                success = OriginMountUiEvent.MountSucceeded,
                failure = { message -> OriginMountUiEvent.MountFailed(message) },
            )

            OriginMountUiAction.RefreshUids -> loadUids()

            is OriginMountUiAction.SetUids -> perform(
                operation = { ksuCli.setNomountUids(action.uids) },
                success = OriginMountUiEvent.UidsUpdated,
                failure = { message -> OriginMountUiEvent.UidsFailed(message) },
                refreshUids = true,
            )

            is OriginMountUiAction.SetIsolated -> perform(
                operation = { ksuCli.setNomountIsolated(action.stockVisible) },
                success = OriginMountUiEvent.UidsUpdated,
                failure = { message -> OriginMountUiEvent.UidsFailed(message) },
                refreshUids = true,
            )

            OriginMountUiAction.ClearRules -> perform(
                operation = { ksuCli.clearNomountRules() },
                success = OriginMountUiEvent.RulesCleared,
            )

            OriginMountUiAction.LoadApps -> loadApps()
        }
    }

    private fun refresh(showLoading: Boolean) {
        viewModelScope.launch {
            if (showLoading) {
                mutableState.value = mutableState.value.copy(isLoading = true)
            }
            val status = runCatching { ksuCli.getOriginMountStatus() }.getOrNull()
            val uidState = runCatching { ksuCli.getNomountUidState() }.getOrNull()
            val zeromount = runCatching {
                withContext(Dispatchers.IO) {
                    ksuCli.isZeromountDriverPresent() to ksuCli.getZeromountVersion()
                }
            }.getOrElse { false to "" }
            if (status == null) {
                mutableState.value = mutableState.value.copy(isLoading = false)
                mutableEvents.tryEmit(OriginMountUiEvent.RefreshFailed)
                return@launch
            }
            mutableState.value = mutableState.value.copy(
                status = status,
                uidState = uidState ?: mutableState.value.uidState,
                zeromountDriverPresent = zeromount.first,
                zeromountVersion = zeromount.second,
                isLoading = false,
                lastCheckedAt = System.currentTimeMillis(),
            )
        }
    }

    private fun loadUids() {
        viewModelScope.launch {
            runCatching { ksuCli.getNomountUidState() }
                .onSuccess { uidState ->
                    mutableState.value = mutableState.value.copy(uidState = uidState)
                }
                .onFailure {
                    mutableEvents.tryEmit(OriginMountUiEvent.UidsFailed(it.message.orEmpty()))
                }
        }
    }

    private fun loadApps() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loadingApps = true)
            val apps = runCatching {
                installedPackageRepository.refresh()
                installedPackageRepository.packages.value
            }.getOrDefault(emptyList())
            mutableState.value = mutableState.value.copy(
                apps = apps
                    .filter { it.uid >= 0 }
                    .distinctBy { it.uid }
                    .sortedWith(compareBy({ !it.isSystem }, { it.appLabel.lowercase() })),
                loadingApps = false,
            )
        }
    }

    private fun perform(
        operation: suspend () -> Boolean,
        success: OriginMountUiEvent,
        failure: (String) -> OriginMountUiEvent = { OriginMountUiEvent.ActionFailed(it) },
        refreshUids: Boolean = false,
    ) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isWorking = true)
            val result = runCatching { operation() }
            mutableState.value = mutableState.value.copy(isWorking = false)
            if (result.getOrDefault(false)) {
                mutableEvents.tryEmit(success)
                if (refreshUids) {
                    runCatching { ksuCli.getNomountUidState() }.onSuccess { uidState ->
                        mutableState.value = mutableState.value.copy(uidState = uidState)
                    }
                }
                refresh(showLoading = false)
            } else {
                val message = result.exceptionOrNull()?.message.orEmpty()
                mutableEvents.tryEmit(failure(message))
            }
        }
    }
}
