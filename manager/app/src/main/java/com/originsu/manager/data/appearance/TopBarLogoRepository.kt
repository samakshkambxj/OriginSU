package com.originsu.manager.data.appearance

import com.originsu.manager.data.AppSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TopBarLogo {
    YIN_YANG,
    FILLED_LEAF,
    WHITE_LEAF,
    ;

    companion object {
        fun fromName(name: String?): TopBarLogo =
            entries.firstOrNull { it.name == name } ?: YIN_YANG
    }
}

class TopBarLogoRepository(
    private val appSettings: AppSettingsRepository,
) {
    private val mutableState = MutableStateFlow(current())
    val state: StateFlow<TopBarLogo> = mutableState.asStateFlow()

    fun refresh() {
        mutableState.value = current()
    }

    fun set(logo: TopBarLogo): Boolean = runCatching {
        appSettings.putString(PREF_TOP_BAR_LOGO, logo.name)
        mutableState.value = logo
        true
    }.getOrDefault(false)

    private fun current(): TopBarLogo =
        TopBarLogo.fromName(appSettings.getString(PREF_TOP_BAR_LOGO))

    companion object {
        const val PREF_TOP_BAR_LOGO = "top_bar_logo"
    }
}
