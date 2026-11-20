package com.kazesu.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazesu.manager.data.model.ManagerUpdateChannel
import com.kazesu.manager.data.model.ManagerUpdateInfo
import com.kazesu.manager.data.model.ManagerVariant
import com.kazesu.manager.data.update.ManagerUpdateRepository
import com.kazesu.manager.ksuApp
import com.kazesu.manager.ui.util.DownloadManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

class UpdaterViewModel : ViewModel() {
    private val repository = ManagerUpdateRepository(ksuApp.okhttpClient)
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
            try {
                val update = when (channel) {
                    ManagerUpdateChannel.STABLE -> repository.checkStableUpdate(variant)
                    ManagerUpdateChannel.BETA -> repository.checkBetaUpdate(variant)
                }
                mutableState.update {
                    it.copy(checking = false, checked = true, update = update, checkFailed = false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(checking = false, checked = true, update = null, checkFailed = true)
                }
            }
        }
    }

    private fun download() {
        val update = mutableState.value.update ?: return
        if (mutableState.value.downloadId != null && !mutableState.value.downloadComplete &&
            mutableState.value.downloadFailed == null
        ) return

        observeJob?.cancel()
        val id = runCatching {
            DownloadManager.enqueueManagerUpdate(ksuApp, update)
        }.getOrElse { error ->
            mutableState.update {
                it.copy(downloadFailed = error.message ?: error.javaClass.simpleName)
            }
            return
        }
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
            DownloadManager.downloads
                .map { downloads -> downloads[id] }
                .distinctUntilChanged()
                .collect { download ->
                    download ?: return@collect
                    mutableState.update {
                        it.copy(
                            downloadProgress = download.progress,
                            downloadFailed = if (download.status == DownloadManager.Status.FAILED) {
                                download.error.orEmpty()
                            } else {
                                null
                            },
                            downloadComplete = download.status == DownloadManager.Status.COMPLETED,
                            resultUri = download.resultUri?.toString(),
                        )
                    }
                }
        }
    }
}
