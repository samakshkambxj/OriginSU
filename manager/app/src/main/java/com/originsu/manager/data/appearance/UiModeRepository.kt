package com.originsu.manager.data.appearance

import com.originsu.manager.data.AppSettingsRepository
import com.originsu.manager.ui.UiMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UiModeRepository(
    private val appSettings: AppSettingsRepository,
) {
    private val mutableState = MutableStateFlow(current())
    val state: StateFlow<UiMode> = mutableState.asStateFlow()

    fun refresh() {
        mutableState.value = current()
    }

    fun set(mode: UiMode): Boolean = runCatching {
        appSettings.putString(PREF_UI_MODE, mode.value)
        mutableState.value = mode
        true
    }.getOrDefault(false)

    private fun current(): UiMode =
        UiMode.fromValue(appSettings.getString(PREF_UI_MODE, UiMode.DEFAULT_VALUE) ?: UiMode.DEFAULT_VALUE)

    companion object {
        const val PREF_UI_MODE = "ui_mode"
    }
}
