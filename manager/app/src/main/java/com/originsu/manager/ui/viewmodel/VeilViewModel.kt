package com.originsu.manager.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.originsu.manager.R
import com.originsu.manager.domain.model.VeilCloakedUid
import com.originsu.manager.domain.model.VeilProbeHistory
import com.originsu.manager.domain.usecase.ClearVeilCloakedUseCase
import com.originsu.manager.domain.usecase.ClearVeilHistoryUseCase
import com.originsu.manager.domain.usecase.ObserveVeilStateUseCase
import com.originsu.manager.domain.usecase.RefreshVeilUseCase
import com.originsu.manager.domain.usecase.SetVeilAutoCloakUseCase
import com.originsu.manager.domain.usecase.SetVeilCloakedUseCase
import com.originsu.manager.domain.usecase.SetVeilEnabledUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VeilUiState(
    val status: String = "",
    val enabled: Boolean = false,
    val autoCloak: Boolean = false,
    val cloakedUids: List<VeilCloakedUid> = emptyList(),
    val history: List<VeilProbeHistory> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface VeilUiAction {
    data object Refresh : VeilUiAction
    data class SetEnabled(val enabled: Boolean) : VeilUiAction
    data class SetAutoCloak(val enabled: Boolean) : VeilUiAction
    data class CloakUid(val uid: Int) : VeilUiAction
    data class UncloakUid(val uid: Int) : VeilUiAction
    data object ClearCloaked : VeilUiAction
    data object ClearHistory : VeilUiAction
}

sealed interface VeilUiEvent {
    data class Error(val message: String) : VeilUiEvent
}

class VeilViewModel(
    private val context: Context,
    observeState: ObserveVeilStateUseCase,
    private val refreshVeil: RefreshVeilUseCase,
    private val setVeilEnabled: SetVeilEnabledUseCase,
    private val setVeilAutoCloak: SetVeilAutoCloakUseCase,
    private val setVeilCloaked: SetVeilCloakedUseCase,
    private val clearVeilCloaked: ClearVeilCloakedUseCase,
    private val clearVeilHistory: ClearVeilHistoryUseCase,
) : ViewModel() {
    private val mutableEvents = MutableSharedFlow<VeilUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<VeilUiEvent> = mutableEvents.asSharedFlow()

    val state: StateFlow<VeilUiState> = observeState()
        .map { source ->
            VeilUiState(
                status = source.status,
                enabled = source.enabled,
                autoCloak = source.autoCloak,
                cloakedUids = source.cloakedUids,
                history = source.history,
                isLoading = source.isLoading,
                isRefreshing = source.isRefreshing,
                errorMessage = source.errorMessage,
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, VeilUiState())
    val uiState: StateFlow<VeilUiState> = state

    fun dispatch(action: VeilUiAction) {
        when (action) {
            VeilUiAction.Refresh -> viewModelScope.launch {
                refreshVeil().exceptionOrNull()?.let {
                    mutableEvents.emit(VeilUiEvent.Error(it.message.orEmpty()))
                }
            }

            is VeilUiAction.SetEnabled -> submit(
                command = { setVeilEnabled(action.enabled) },
                failureMessage = R.string.operation_failed,
            )

            is VeilUiAction.SetAutoCloak -> submit(
                command = { setVeilAutoCloak(action.enabled) },
                failureMessage = R.string.operation_failed,
            )

            is VeilUiAction.CloakUid -> submit(
                command = { setVeilCloaked(action.uid, true) },
                failureMessage = R.string.operation_failed,
            )

            is VeilUiAction.UncloakUid -> submit(
                command = { setVeilCloaked(action.uid, false) },
                failureMessage = R.string.operation_failed,
            )

            VeilUiAction.ClearCloaked -> submit(
                command = { clearVeilCloaked() },
                failureMessage = R.string.operation_failed,
            )

            VeilUiAction.ClearHistory -> submit(
                command = { clearVeilHistory() },
                failureMessage = R.string.operation_failed,
            )
        }
    }

    private fun submit(
        command: suspend () -> Result<Unit>,
        failureMessage: Int,
    ) {
        viewModelScope.launch {
            command().exceptionOrNull()?.let {
                val message = it.message?.takeIf(String::isNotBlank)
                    ?: context.getString(failureMessage)
                mutableEvents.emit(VeilUiEvent.Error(message))
            }
        }
    }
}
