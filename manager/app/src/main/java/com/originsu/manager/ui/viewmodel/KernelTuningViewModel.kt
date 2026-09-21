package com.originsu.manager.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.originsu.manager.R
import com.originsu.manager.data.kernel.KernelTuningRepository
import com.originsu.manager.domain.model.KernelTuningState
import com.originsu.manager.domain.model.SysctlEntry
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

typealias KernelTuningUiState = KernelTuningState

sealed interface KernelTuningUiAction {
    data object Refresh : KernelTuningUiAction
    data class SetTcp(val name: String, val persist: Boolean) : KernelTuningUiAction
    data class SetTcpPersist(val persist: Boolean) : KernelTuningUiAction
    data class AddOrUpdateSysctl(val key: String, val value: String, val persist: Boolean) :
        KernelTuningUiAction
    data class SetBoreEnabled(val enabled: Boolean) : KernelTuningUiAction
    data class SetBoreKnob(val key: String, val value: String) : KernelTuningUiAction
    data class SetBorePersist(val persist: Boolean) : KernelTuningUiAction
    data class ApplyBoreProfile(val id: String) : KernelTuningUiAction
    data class ConfigureZram(val sizeBytes: Long, val algo: String, val streams: Long) :
        KernelTuningUiAction
    data class SetZramSwappiness(val value: String) : KernelTuningUiAction
    data class SetZramPersist(val persist: Boolean) : KernelTuningUiAction
    data object ResetBoreDefaults : KernelTuningUiAction
    data class RemoveSysctl(val entry: SysctlEntry) : KernelTuningUiAction
    data class SetSysctlPersist(val entry: SysctlEntry, val persist: Boolean) :
        KernelTuningUiAction
}

sealed interface KernelTuningUiEvent {
    data class Message(val message: String) : KernelTuningUiEvent
}

class KernelTuningViewModel(
    private val context: Context,
    private val repository: KernelTuningRepository,
) : ViewModel() {
    val uiState: StateFlow<KernelTuningUiState> = repository.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, KernelTuningState())

    val boreProfileIds: List<String> = KernelTuningRepository.BORE_PROFILE_ORDER

    private val mutableEvents = MutableSharedFlow<KernelTuningUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<KernelTuningUiEvent> = mutableEvents.asSharedFlow()

    init {
        viewModelScope.launch { repository.refresh() }
    }

    fun dispatch(action: KernelTuningUiAction) {
        when (action) {
            is KernelTuningUiAction.Refresh -> viewModelScope.launch { repository.refresh() }
            is KernelTuningUiAction.SetTcp -> submit(
                command = { repository.setTcp(action.name, action.persist) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is KernelTuningUiAction.SetTcpPersist -> submit(
                command = { repository.setTcpPersist(action.persist) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is KernelTuningUiAction.AddOrUpdateSysctl -> submit(
                command = {
                    repository.addOrUpdateSysctl(action.key, action.value, action.persist)
                },
                successMessage = R.string.kernel_tuning_applied,
            )
            is KernelTuningUiAction.SetBoreEnabled -> submit(
                command = { repository.setBoreEnabled(action.enabled) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is KernelTuningUiAction.SetBoreKnob -> submit(
                command = { repository.setBoreKnob(action.key, action.value) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is KernelTuningUiAction.SetBorePersist -> submit(
                command = { repository.setBorePersist(action.persist) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is KernelTuningUiAction.ResetBoreDefaults -> submit(
                command = { repository.resetBoreDefaults() },
                successMessage = R.string.kernel_tuning_applied,
            )
            is KernelTuningUiAction.ApplyBoreProfile -> submit(
                command = { repository.applyBoreProfile(action.id) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is KernelTuningUiAction.ConfigureZram -> submit(
                command = {
                    repository.configureZram(action.sizeBytes, action.algo, action.streams)
                },
                successMessage = R.string.kernel_tuning_applied,
            )
            is KernelTuningUiAction.SetZramSwappiness -> submit(
                command = { repository.setZramSwappiness(action.value) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is KernelTuningUiAction.SetZramPersist -> submit(
                command = { repository.setZramPersist(action.persist) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is KernelTuningUiAction.RemoveSysctl -> submit(
                command = { repository.removeSysctl(action.entry.key) },
                successMessage = R.string.kernel_tuning_removed,
            )
            is KernelTuningUiAction.SetSysctlPersist -> submit(
                command = { repository.setSysctlPersist(action.entry.key, action.persist) },
                successMessage = R.string.kernel_tuning_applied,
            )
        }
    }

    private fun submit(
        command: suspend () -> Result<Unit>,
        successMessage: Int,
    ) {
        viewModelScope.launch {
            val message = if (command().isSuccess) successMessage else R.string.operation_failed
            mutableEvents.emit(KernelTuningUiEvent.Message(context.getString(message)))
        }
    }
}
