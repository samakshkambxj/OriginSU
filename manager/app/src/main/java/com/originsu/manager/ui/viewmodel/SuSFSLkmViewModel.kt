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
)

sealed interface SuSFSLkmUiAction {
    data object Refresh : SuSFSLkmUiAction
    data class Load(val kmi: String? = null) : SuSFSLkmUiAction
    data class SetAutoload(
        val enabled: Boolean,
        val kmi: String? = null,
    ) : SuSFSLkmUiAction
}

sealed interface SuSFSLkmUiEvent {
    data object LoadSucceeded : SuSFSLkmUiEvent
    data class LoadFailed(val message: String) : SuSFSLkmUiEvent
    data object AutoloadEnabled : SuSFSLkmUiEvent
    data object AutoloadDisabled : SuSFSLkmUiEvent
    data class AutoloadFailed(val message: String) : SuSFSLkmUiEvent
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
                    mutableState.value = mutableState.value.copy(isWorking = false)
                    if (result.success) {
                        onSuccess()
                        loadStatus(showLoading = false)
                    } else {
                        onFailure(result.error)
                    }
                }
                .onFailure { error ->
                    mutableState.value = mutableState.value.copy(isWorking = false)
                    onFailure(error.message.orEmpty())
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
