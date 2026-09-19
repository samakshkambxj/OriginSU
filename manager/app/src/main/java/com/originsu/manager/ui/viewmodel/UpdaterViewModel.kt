package com.originsu.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.originsu.manager.domain.model.DownloadStatus
import com.originsu.manager.domain.model.ManagerUpdateChannel
import com.originsu.manager.domain.model.ManagerUpdateInfo
import com.originsu.manager.domain.model.ManagerVariant
import com.originsu.manager.domain.usecase.CheckManagerUpdateUseCase
import com.originsu.manager.domain.usecase.EnqueueManagerUpdateUseCase
import com.originsu.manager.domain.usecase.ObserveDownloadUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UpdaterUiState(
    val channel: ManagerUpdateChannel = ManagerUpdateChannel.STABLE,
    val variant: ManagerVariant = ManagerVariant.NORMAL,
    val checking: Boolean = false,
    val checked: Boolean = false,
    val update: ManagerUpdateInfo? = null,
    val checkFailed: Boolean = false,
    val downloadId: Int? = null,
    val downloadProgress: Int = 0,
    val downloadFailed: String? = null,
    val downloadComplete: Boolean = false,
    val resultUri: String? = null,
)

sealed interface UpdaterUiAction {
    data class SelectChannel(val channel: ManagerUpdateChannel) : UpdaterUiAction
    data class SelectVariant(val variant: ManagerVariant) : UpdaterUiAction
    data object Check : UpdaterUiAction
    data object Download : UpdaterUiAction
}

class UpdaterViewModel(
    private val checkManagerUpdate: CheckManagerUpdateUseCase,
    private val enqueueManagerUpdate: EnqueueManagerUpdateUseCase,
    private val observeDownload: ObserveDownloadUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(UpdaterUiState())
    val state: StateFlow<UpdaterUiState> = mutableState.asStateFlow()

    private var checkJob: Job? = null
    private var observeJob: Job? = null

    init {
        check()
    }

    fun dispatch(action: UpdaterUiAction) {
        when (action) {
            is UpdaterUiAction.SelectChannel -> {
                if (mutableState.value.channel == action.channel) return
                mutableState.update { it.copy(channel = action.channel) }
                check()
            }

            is UpdaterUiAction.SelectVariant -> {
                if (mutableState.value.variant == action.variant) return
                mutableState.update { it.copy(variant = action.variant) }
                check()
            }

            UpdaterUiAction.Check -> check()
            UpdaterUiAction.Download -> download()
        }
    }

    private fun check() {
        checkJob?.cancel()
        observeJob?.cancel()
        observeJob = null
        checkJob = viewModelScope.launch {
            mutableState.update {
                it.copy(
                    checking = true,
                    checked = false,
                    checkFailed = false,
                    update = null,
                    downloadId = null,
                    downloadProgress = 0,
                    downloadFailed = null,
                    downloadComplete = false,
                    resultUri = null,
                )
            }
            val channel = mutableState.value.channel
            val variant = mutableState.value.variant
            val result = runCatching { checkManagerUpdate(channel, variant) }
            mutableState.update {
                it.copy(
                    checking = false,
                    checked = true,
                    update = result.getOrNull(),
                    checkFailed = result.isFailure,
                )
            }
        }
    }

    private fun download() {
        val update = mutableState.value.update ?: return
        observeJob?.cancel()
        val id = enqueueManagerUpdate(update)
        mutableState.update {
            it.copy(
                downloadId = id,
                downloadProgress = 0,
                downloadFailed = null,
                downloadComplete = false,
                resultUri = null,
            )
        }
        observeJob = viewModelScope.launch {
            observeDownload(id).collect { download ->
                download ?: return@collect
                mutableState.update {
                    it.copy(
                        downloadProgress = download.progress,
                        downloadFailed = if (download.status == DownloadStatus.FAILED) {
                            download.error ?: ""
                        } else {
                            null
                        },
                        downloadComplete = download.status == DownloadStatus.COMPLETED,
                        resultUri = download.resultUri,
                    )
                }
            }
        }
    }
}
