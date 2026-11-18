package com.kazesu.manager.ui.screen.tune

import android.content.Context
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kazesu.manager.R
import com.kazesu.manager.data.kernel.KernelTuningRepository
import com.kazesu.manager.data.model.KernelTuningState
import com.kazesu.manager.data.model.SysctlEntry
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

typealias TuneUiState = KernelTuningState

sealed interface TuneUiAction {
    data object Refresh : TuneUiAction
    data class SetTcp(val name: String, val persist: Boolean) : TuneUiAction
    data class SetTcpPersist(val persist: Boolean) : TuneUiAction
    data class AddOrUpdateSysctl(val key: String, val value: String, val persist: Boolean) :
        TuneUiAction
    data class SetBoreEnabled(val enabled: Boolean) : TuneUiAction
    data class SetBoreKnob(val key: String, val value: String) : TuneUiAction
    data class SetBorePersist(val persist: Boolean) : TuneUiAction
    data class ApplyBoreProfile(val id: String) : TuneUiAction
    data class ConfigureZram(val sizeBytes: Long, val algo: String, val streams: Long) :
        TuneUiAction
    data class SetZramSwappiness(val value: String) : TuneUiAction
    data class SetZramPersist(val persist: Boolean) : TuneUiAction
    data class SetCpuGovernor(val policyId: String, val governor: String) : TuneUiAction
    data class SetCpuFreqs(val policyId: String, val minKhz: Long, val maxKhz: Long) :
        TuneUiAction
    data class SetSchedutilRateLimit(val value: String) : TuneUiAction
    data class SetCpuPersist(val persist: Boolean) : TuneUiAction
    data class SetGpuGovernor(val id: String, val governor: String) : TuneUiAction
    data class SetGpuFreqs(val id: String, val minHz: Long, val maxHz: Long) :
        TuneUiAction
    data class SetGpuPersist(val persist: Boolean) : TuneUiAction
    data class SetIoScheduler(val name: String, val scheduler: String) : TuneUiAction
    data class SetIoReadAhead(val name: String, val kb: Long) : TuneUiAction
    data class SetIoPersist(val persist: Boolean) : TuneUiAction
    data class SetVmKnob(val key: String, val value: String) : TuneUiAction
    data class SetVmPersist(val persist: Boolean) : TuneUiAction
    data class ApplyVmProfile(val id: String) : TuneUiAction
    data object ResetVmDefaults : TuneUiAction
    data class SetSchedKnob(val key: String, val value: String) : TuneUiAction
    data class SetSchedPersist(val persist: Boolean) : TuneUiAction
    data object ResetSchedDefaults : TuneUiAction
    data class SetLmkLevel(val index: Int, val pages: Long, val adj: Long) : TuneUiAction
    data class SetLmkPersist(val persist: Boolean) : TuneUiAction
    data class ApplyLmkProfile(val id: String) : TuneUiAction
    data object ResetLmkStock : TuneUiAction
    data class ImportProfile(val json: String) : TuneUiAction
    data object ResetBoreDefaults : TuneUiAction
    data class RemoveSysctl(val entry: SysctlEntry) : TuneUiAction
    data class SetSysctlPersist(val entry: SysctlEntry, val persist: Boolean) :
        TuneUiAction
    data object SaveSnapshot : TuneUiAction
    data object RestoreSnapshot : TuneUiAction
}

sealed interface TuneUiEvent {
    data class Message(val message: String) : TuneUiEvent
}

class TuneViewModel(app: Application) : AndroidViewModel(app) {
    private val context: Context = app.applicationContext
    private val repository = KernelTuningRepository(app)
    val uiState: StateFlow<TuneUiState> = repository.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, KernelTuningState())

    val boreProfileIds: List<String> = KernelTuningRepository.BORE_PROFILE_ORDER

    val vmProfileIds: List<String> = KernelTuningRepository.VM_PROFILE_ORDER

    val lmkProfileIds: List<String> = KernelTuningRepository.LMK_PROFILE_ORDER

    private val mutableEvents = MutableSharedFlow<TuneUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<TuneUiEvent> = mutableEvents.asSharedFlow()

    private val mutableHasSnapshot = MutableStateFlow(repository.hasSnapshot())
    val hasSnapshot: StateFlow<Boolean> = mutableHasSnapshot.asStateFlow()

    init {
        viewModelScope.launch { repository.refresh() }
    }

    fun dispatch(action: TuneUiAction) {
        when (action) {
            is TuneUiAction.Refresh -> viewModelScope.launch { repository.refresh() }
            is TuneUiAction.SetTcp -> submit(
                command = { repository.setTcp(action.name, action.persist) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is TuneUiAction.SetTcpPersist -> submit(
                command = { repository.setTcpPersist(action.persist) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is TuneUiAction.AddOrUpdateSysctl -> submit(
                command = {
                    repository.addOrUpdateSysctl(action.key, action.value, action.persist)
                },
                successMessage = R.string.kernel_tuning_applied,
            )
            is TuneUiAction.SetBoreEnabled -> submit(
                command = { repository.setBoreEnabled(action.enabled) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is TuneUiAction.SetBoreKnob -> submit(
                command = { repository.setBoreKnob(action.key, action.value) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is TuneUiAction.SetBorePersist -> submit(
                command = { repository.setBorePersist(action.persist) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is TuneUiAction.ResetBoreDefaults -> submit(
                command = { repository.resetBoreDefaults() },
                successMessage = R.string.kernel_tuning_applied,
            )
            is TuneUiAction.ApplyBoreProfile -> submit(
                command = { repository.applyBoreProfile(action.id) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is TuneUiAction.ConfigureZram -> submit(
                command = {
                    repository.configureZram(action.sizeBytes, action.algo, action.streams)
                },
                successMessage = R.string.kernel_tuning_applied,
            )
            is TuneUiAction.SetZramSwappiness -> submit(
                command = { repository.setZramSwappiness(action.value) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is TuneUiAction.SetZramPersist -> submit(
                command = { repository.setZramPersist(action.persist) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is TuneUiAction.SetCpuGovernor -> submit(
                command = { repository.setCpuGovernor(action.policyId, action.governor) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is TuneUiAction.SetCpuFreqs -> submit(
                command = { repository.setCpuFreqs(action.policyId, action.minKhz, action.maxKhz) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is TuneUiAction.SetSchedutilRateLimit -> submit(
                command = { repository.setSchedutilRateLimit(action.value) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is TuneUiAction.SetCpuPersist -> submit(
                command = { repository.setCpuPersist(action.persist) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is TuneUiAction.SetGpuGovernor -> submit(
                command = { repository.setGpuGovernor(action.id, action.governor) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is TuneUiAction.SetGpuFreqs -> submit(
                command = { repository.setGpuFreqs(action.id, action.minHz, action.maxHz) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is TuneUiAction.SetGpuPersist -> submit(
                command = { repository.setGpuPersist(action.persist) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is TuneUiAction.SetIoScheduler -> submit(
                command = { repository.setIoScheduler(action.name, action.scheduler) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is TuneUiAction.SetIoReadAhead -> submit(
                command = { repository.setIoReadAhead(action.name, action.kb) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is TuneUiAction.SetIoPersist -> submit(
                command = { repository.setIoPersist(action.persist) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is TuneUiAction.SetVmKnob -> submit(
                command = { repository.setVmKnob(action.key, action.value) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is TuneUiAction.SetVmPersist -> submit(
                command = { repository.setVmPersist(action.persist) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is TuneUiAction.ApplyVmProfile -> submit(
                command = { repository.applyVmProfile(action.id) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is TuneUiAction.ResetVmDefaults -> submit(
                command = { repository.resetVmDefaults() },
                successMessage = R.string.kernel_tuning_applied,
            )
            is TuneUiAction.SetSchedKnob -> submit(
                command = { repository.setSchedKnob(action.key, action.value) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is TuneUiAction.SetSchedPersist -> submit(
                command = { repository.setSchedPersist(action.persist) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is TuneUiAction.ResetSchedDefaults -> submit(
                command = { repository.resetSchedDefaults() },
                successMessage = R.string.kernel_tuning_applied,
            )
            is TuneUiAction.SetLmkLevel -> submit(
                command = { repository.setLmkLevel(action.index, action.pages, action.adj) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is TuneUiAction.SetLmkPersist -> submit(
                command = { repository.setLmkPersist(action.persist) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is TuneUiAction.ApplyLmkProfile -> submit(
                command = { repository.applyLmkProfile(action.id) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is TuneUiAction.ResetLmkStock -> submit(
                command = { repository.resetLmkStock() },
                successMessage = R.string.kernel_tuning_applied,
            )
            is TuneUiAction.ImportProfile -> import(action.json)
            is TuneUiAction.RemoveSysctl -> submit(
                command = { repository.removeSysctl(action.entry.key) },
                successMessage = R.string.kernel_tuning_removed,
            )
            is TuneUiAction.SetSysctlPersist -> submit(
                command = { repository.setSysctlPersist(action.entry.key, action.persist) },
                successMessage = R.string.kernel_tuning_applied,
            )
            is TuneUiAction.SaveSnapshot -> {
                val saved = repository.saveSnapshot().isSuccess
                if (saved) mutableHasSnapshot.value = true
                viewModelScope.launch {
                    mutableEvents.emit(
                        TuneUiEvent.Message(
                            context.getString(
                                if (saved) R.string.kernel_tuning_snapshot_saved
                                else R.string.operation_failed
                            )
                        )
                    )
                }
            }
            is TuneUiAction.RestoreSnapshot -> submit(
                command = { repository.restoreSnapshot() },
                successMessage = R.string.kernel_tuning_snapshot_restored,
            )
        }
    }

    private fun submit(
        command: suspend () -> Result<Unit>,
        successMessage: Int,
    ) {
        viewModelScope.launch {
            val message = if (command().isSuccess) successMessage else R.string.operation_failed
            mutableEvents.emit(TuneUiEvent.Message(context.getString(message)))
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
            mutableEvents.emit(TuneUiEvent.Message(message))
        }
    }
}
