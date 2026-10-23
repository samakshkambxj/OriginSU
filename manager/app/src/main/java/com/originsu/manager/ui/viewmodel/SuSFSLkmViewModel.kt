package com.originsu.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.originsu.manager.domain.model.SuSFSLkmActionResult
import com.originsu.manager.domain.model.SuSFSLkmStatus
import com.originsu.manager.domain.usecase.SuSFSConfigUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SuSFSLkmUiState(
    val status: SuSFSLkmStatus? = null,
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val lastAction: SuSFSLkmActionResult? = null,
)

sealed interface SuSFSLkmUiAction {
    data object Refresh : SuSFSLkmUiAction
    data class Load(val kmi: String? = null) : SuSFSLkmUiAction
    data class SetAutoload(
        val enabled: Boolean,
        val kmi: String? = null,
    ) : SuSFSLkmUiAction
    data class UpdateAutoloadKmi(val kmi: String? = null) : SuSFSLkmUiAction
    data object RemoveForThisBoot : SuSFSLkmUiAction
}

sealed interface SuSFSLkmUiEvent {
    data object LoadSucceeded : SuSFSLkmUiEvent
    data class LoadFailed(val message: String) : SuSFSLkmUiEvent
    data object AutoloadEnabled : SuSFSLkmUiEvent
    data object AutoloadDisabled : SuSFSLkmUiEvent
    data object AutoloadUpdated : SuSFSLkmUiEvent
    data class AutoloadFailed(val message: String) : SuSFSLkmUiEvent
    data object RemoveSucceeded : SuSFSLkmUiEvent
    data class RemoveFailed(val message: String) : SuSFSLkmUiEvent
    data object RefreshFailed : SuSFSLkmUiEvent
}

class SuSFSLkmViewModel(
    private val configUseCase: SuSFSConfigUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SuSFSLkmUiState())
    private val mutableEvents = MutableSharedFlow<SuSFSLkmUiEvent>(extraBufferCapacity = 4)

    val state: StateFlow<SuSFSLkmUiState> = mutableState.asStateFlow()
    val events: SharedFlow<SuSFSLkmUiEvent> = mutableEvents.asSharedFlow()

    fun dispatch(action: SuSFSLkmUiAction) {
        when (action) {
            SuSFSLkmUiAction.Refresh -> refresh(
                showLoading = mutableState.value.status == null,
            )

            is SuSFSLkmUiAction.Load -> perform(
                operation = { configUseCase.loadLkm(action.kmi) },
                onSuccess = { mutableEvents.tryEmit(SuSFSLkmUiEvent.LoadSucceeded) },
                onFailure = { error -> mutableEvents.tryEmit(SuSFSLkmUiEvent.LoadFailed(error)) },
            )

            is SuSFSLkmUiAction.SetAutoload -> perform(
                operation = { configUseCase.setLkmAutoload(action.enabled, action.kmi) },
                onSuccess = {
                    mutableEvents.tryEmit(
                        if (action.enabled) {
                            SuSFSLkmUiEvent.AutoloadEnabled
                        } else {
                            SuSFSLkmUiEvent.AutoloadDisabled
                        }
                    )
                },
                onFailure = { error ->
                    mutableEvents.tryEmit(SuSFSLkmUiEvent.AutoloadFailed(error))
                },
            )

            is SuSFSLkmUiAction.UpdateAutoloadKmi -> perform(
                operation = { configUseCase.setLkmAutoload(true, action.kmi) },
                onSuccess = { mutableEvents.tryEmit(SuSFSLkmUiEvent.AutoloadUpdated) },
                onFailure = { error ->
                    mutableEvents.tryEmit(SuSFSLkmUiEvent.AutoloadFailed(error))
                },
            )

            SuSFSLkmUiAction.RemoveForThisBoot -> perform(
                operation = { configUseCase.uninstallLkmTemporarily() },
                onSuccess = { mutableEvents.tryEmit(SuSFSLkmUiEvent.RemoveSucceeded) },
                onFailure = { error -> mutableEvents.tryEmit(SuSFSLkmUiEvent.RemoveFailed(error)) },
            )
        }
    }

    private fun refresh(showLoading: Boolean) {
        viewModelScope.launch {
            loadStatus(showLoading)
        }
    }

    private fun perform(
        operation: suspend () -> SuSFSLkmActionResult,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isWorking = true)
            runCatching { operation() }
                .onSuccess { result ->
                    mutableState.value = mutableState.value.copy(
                        isWorking = false,
                        lastAction = result,
                    )
                    if (result.success) {
                        onSuccess()
                        loadStatus(showLoading = false)
                    } else {
                        onFailure(result.error)
                    }
                }
                .onFailure { error ->
                    val message = error.message.orEmpty()
                    mutableState.value = mutableState.value.copy(
                        isWorking = false,
                        lastAction = SuSFSLkmActionResult(
                            success = false,
                            error = message,
                        ),
                    )
                    onFailure(message)
                }
        }
    }

    private suspend fun loadStatus(showLoading: Boolean) {
        if (showLoading) {
            mutableState.value = mutableState.value.copy(isLoading = true)
        }
        runCatching { configUseCase.loadLkmStatus(forceRefresh = true) }
            .onSuccess { status ->
                mutableState.value = mutableState.value.copy(
                    status = status,
                    isLoading = false,
                )
            }
            .onFailure {
                mutableState.value = mutableState.value.copy(isLoading = false)
                mutableEvents.tryEmit(SuSFSLkmUiEvent.RefreshFailed)
            }
    }
}
