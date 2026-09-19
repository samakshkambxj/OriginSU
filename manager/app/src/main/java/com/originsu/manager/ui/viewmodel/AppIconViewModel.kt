package com.originsu.manager.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import com.originsu.manager.R
import com.originsu.manager.data.appearance.AppIcon
import com.originsu.manager.data.appearance.AppIconRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AppIconUiState(
    val current: AppIcon = AppIcon.ORIGIN_BLACK,
)

sealed interface AppIconUiEvent {
    data class Error(val message: String) : AppIconUiEvent
}

class AppIconViewModel(
    private val context: Context,
    private val appIconRepository: AppIconRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        AppIconUiState(current = appIconRepository.getAppIcon())
    )
    val state: StateFlow<AppIconUiState> = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<AppIconUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<AppIconUiEvent> = mutableEvents.asSharedFlow()

    fun select(icon: AppIcon) {
        if (mutableState.value.current == icon) return
        if (appIconRepository.setAppIcon(icon)) {
            mutableState.update { it.copy(current = icon) }
        } else {
            mutableEvents.tryEmit(
                AppIconUiEvent.Error(context.getString(R.string.operation_failed))
            )
        }
    }
}
