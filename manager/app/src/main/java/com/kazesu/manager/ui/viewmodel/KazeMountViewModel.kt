package com.kazesu.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazesu.manager.R
import com.kazesu.manager.data.mount.KazeMountMode
import com.kazesu.manager.data.mount.KazeMountRepository
import com.kazesu.manager.data.mount.KazeMountStatus
import com.kazesu.manager.data.mount.NoMountUidState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class KazeMountUiState(
    val status: KazeMountStatus = KazeMountStatus(),
    val uidState: NoMountUidState = NoMountUidState(),
    val zeroMountDriverPresent: Boolean = false,
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
)

class KazeMountViewModel : ViewModel() {
    private val repository = KazeMountRepository()
    private val mutableState = MutableStateFlow(KazeMountUiState())
    private val mutableEvents = MutableSharedFlow<Int>(extraBufferCapacity = 4)

    val state: StateFlow<KazeMountUiState> = mutableState.asStateFlow()
    val events: SharedFlow<Int> = mutableEvents.asSharedFlow()

    init {
        refresh(showLoading = true)
    }

    fun refresh(showLoading: Boolean = false) {
        viewModelScope.launch {
            if (showLoading) mutableState.value = mutableState.value.copy(isLoading = true)
            val status = runCatching { repository.status() }.getOrDefault(KazeMountStatus())
            val uids = runCatching { repository.uidState() }.getOrDefault(NoMountUidState())
            val zeroMount = runCatching { repository.isZeroMountDriverPresent() }.getOrDefault(false)
            mutableState.value = mutableState.value.copy(
                status = status,
                uidState = uids,
                zeroMountDriverPresent = zeroMount,
                isLoading = false,
            )
            if (!status.available) mutableEvents.tryEmit(R.string.kaze_mount_refresh_failed)
        }
    }

    fun setMode(mode: KazeMountMode) = perform(
        successMessage = R.string.kaze_mount_mode_updated,
        operation = { repository.setMode(mode) },
    )

    fun mountNow() = perform(
        successMessage = R.string.kaze_mount_mount_succeeded,
        failureMessage = R.string.kaze_mount_mount_failed,
        operation = { repository.mountNow() },
    )

    fun setUids(raw: String) {
        val tokens = raw.split(Regex("[,\\s]+")).filter { it.isNotBlank() }
        val parsed = tokens.map { it.toIntOrNull() }
        if (parsed.any { it == null || it < 0 }) {
            mutableEvents.tryEmit(R.string.kaze_mount_invalid_uids)
            return
        }
        perform(
            successMessage = R.string.kaze_mount_uids_updated,
            operation = { repository.setUids(parsed.filterNotNull()) },
        )
    }

    fun setIsolated(stockVisible: Boolean) = perform(
        successMessage = R.string.kaze_mount_uids_updated,
        operation = { repository.setIsolated(stockVisible) },
    )

    fun clearRules() = perform(
        successMessage = R.string.kaze_mount_rules_cleared,
        operation = { repository.clearRules() },
    )

    private fun perform(
        successMessage: Int,
        failureMessage: Int = R.string.kaze_mount_action_failed,
        operation: suspend () -> Boolean,
    ) {
        if (mutableState.value.isWorking) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isWorking = true)
            val succeeded = runCatching { operation() }.getOrDefault(false)
            mutableState.value = mutableState.value.copy(isWorking = false)
            mutableEvents.tryEmit(if (succeeded) successMessage else failureMessage)
            refresh()
        }
    }
}
