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
    data class SetCpuGovernor(val policyId: String, val governor: String) : KernelTuningUiAction
    data class SetCpuFreqs(val policyId: String, val minKhz: Long, val maxKhz: Long) :
        KernelTuningUiAction
    data class SetSchedutilRateLimit(val value: String) : KernelTuningUiAction
    data class SetCpuPersist(val persist: Boolean) : KernelTuningUiAction
    data class SetGpuGovernor(val id: String, val governor: String) : KernelTuningUiAction
    data class SetGpuFreqs(val id: String, val minHz: Long, val maxHz: Long) :
        KernelTuningUiAction
    data class SetGpuPersist(val persist: Boolean) : KernelTuningUiAction
    data class SetIoScheduler(val name: String, val scheduler: String) : KernelTuningUiAction
    data class SetIoReadAhead(val name: String, val kb: Long) : KernelTuningUiAction
    data class SetIoPersist(val persist: Boolean) : KernelTuningUiAction
    data class SetVmKnob(val key: String, val value: String) : KernelTuningUiAction
    data class SetVmPersist(val persist: Boolean) : KernelTuningUiAction
    data class ApplyVmProfile(val id: String) : KernelTuningUiAction
    data object ResetVmDefaults : KernelTuningUiAction
    data class SetSchedKnob(val key: String, val value: String) : KernelTuningUiAction
    data class SetSchedPersist(val persist: Boolean) : KernelTuningUiAction
    data object ResetSchedDefaults : KernelTuningUiAction
    data class SetLmkLevel(val index: Int, val pages: Long, val adj: Long) : KernelTuningUiAction
    data class SetLmkPersist(val persist: Boolean) : KernelTuningUiAction
    data class ApplyLmkProfile(val id: String) : KernelTuningUiAction
    data object ResetLmkStock : KernelTuningUiAction
    data class ImportProfile(val json: String) : KernelTuningUiAction
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

    val vmProfileIds: List<String> = KernelTuningRepository.VM_PROFILE_ORDER

    val lmkProfileIds: List<String> = KernelTuningRepository.LMK_PROFILE_ORDER

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
            is KernelTuningUiAction.SetCpuGovernor -> submit(
                command = { repository.setCpuGovernor(action.policyId, action.governor) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is KernelTuningUiAction.SetCpuFreqs -> submit(
                command = { repository.setCpuFreqs(action.policyId, action.minKhz, action.maxKhz) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is KernelTuningUiAction.SetSchedutilRateLimit -> submit(
                command = { repository.setSchedutilRateLimit(action.value) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is KernelTuningUiAction.SetCpuPersist -> submit(
                command = { repository.setCpuPersist(action.persist) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is KernelTuningUiAction.SetGpuGovernor -> submit(
                command = { repository.setGpuGovernor(action.id, action.governor) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is KernelTuningUiAction.SetGpuFreqs -> submit(
                command = { repository.setGpuFreqs(action.id, action.minHz, action.maxHz) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is KernelTuningUiAction.SetGpuPersist -> submit(
                command = { repository.setGpuPersist(action.persist) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is KernelTuningUiAction.SetIoScheduler -> submit(
                command = { repository.setIoScheduler(action.name, action.scheduler) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is KernelTuningUiAction.SetIoReadAhead -> submit(
                command = { repository.setIoReadAhead(action.name, action.kb) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is KernelTuningUiAction.SetIoPersist -> submit(
                command = { repository.setIoPersist(action.persist) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is KernelTuningUiAction.SetVmKnob -> submit(
                command = { repository.setVmKnob(action.key, action.value) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is KernelTuningUiAction.SetVmPersist -> submit(
                command = { repository.setVmPersist(action.persist) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is KernelTuningUiAction.ApplyVmProfile -> submit(
                command = { repository.applyVmProfile(action.id) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is KernelTuningUiAction.ResetVmDefaults -> submit(
                command = { repository.resetVmDefaults() },
                successMessage = R.string.kernel_tuning_applied,
            )
            is KernelTuningUiAction.SetSchedKnob -> submit(
                command = { repository.setSchedKnob(action.key, action.value) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is KernelTuningUiAction.SetSchedPersist -> submit(
                command = { repository.setSchedPersist(action.persist) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is KernelTuningUiAction.ResetSchedDefaults -> submit(
                command = { repository.resetSchedDefaults() },
                successMessage = R.string.kernel_tuning_applied,
            )
            is KernelTuningUiAction.SetLmkLevel -> submit(
                command = { repository.setLmkLevel(action.index, action.pages, action.adj) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is KernelTuningUiAction.SetLmkPersist -> submit(
                command = { repository.setLmkPersist(action.persist) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is KernelTuningUiAction.ApplyLmkProfile -> submit(
                command = { repository.applyLmkProfile(action.id) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is KernelTuningUiAction.ResetLmkStock -> submit(
                command = { repository.resetLmkStock() },
                successMessage = R.string.kernel_tuning_applied,
            )
            is KernelTuningUiAction.ImportProfile -> import(action.json)
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

    /** Current setup as shareable JSON (read from the latest refresh). */
    fun exportProfileJson(): String = repository.exportProfile()

    private fun import(json: String) {
        viewModelScope.launch {
            val result = repository.importProfile(json)
            val message = if (result.isSuccess) {
                context.getString(
                    R.string.kernel_tuning_profile_imported,
                    result.getOrDefault(""),
                )
            } else {
                context.getString(R.string.operation_failed)
            }
            mutableEvents.emit(KernelTuningUiEvent.Message(message))
        }
    }
}
