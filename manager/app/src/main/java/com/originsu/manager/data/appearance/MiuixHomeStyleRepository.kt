package com.originsu.manager.data.appearance

import com.originsu.manager.data.AppSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MiuixHomeStyle {
    Standard,
    Compact,
    ;

    companion object {
        fun fromName(name: String?): MiuixHomeStyle =
            entries.firstOrNull { it.name == name } ?: Standard
    }
}

class MiuixHomeStyleRepository(
    private val appSettings: AppSettingsRepository,
) {
    private val mutableState = MutableStateFlow(current())
    val state: StateFlow<MiuixHomeStyle> = mutableState.asStateFlow()

    fun refresh() {
        mutableState.value = current()
    }

    fun set(style: MiuixHomeStyle): Boolean = runCatching {
        appSettings.putString(PREF_MIUIX_HOME_STYLE, style.name)
        mutableState.value = style
        true
    }.getOrDefault(false)

    private fun current(): MiuixHomeStyle =
        MiuixHomeStyle.fromName(appSettings.getString(PREF_MIUIX_HOME_STYLE))

    companion object {
        const val PREF_MIUIX_HOME_STYLE = "miuix_home_style"
    }
}
