package com.originsu.manager.data.shortcuts

import android.content.Context
import com.originsu.manager.data.AppSettingsRepository
import com.originsu.manager.data.appearance.AppIconRepository
import com.originsu.manager.domain.usecase.THEMED_SHORTCUTS_PREF_KEY
import com.originsu.manager.ui.util.refreshAppShortcuts

class AppShortcutsRepository(
    private val context: Context,
    private val appSettings: AppSettingsRepository,
    private val appIconRepository: AppIconRepository,
) {
    fun isThemed(): Boolean = appSettings.getBoolean(THEMED_SHORTCUTS_PREF_KEY, false)

    fun setThemed(enabled: Boolean): Boolean {
        val applied = refreshAppShortcuts(
            context.applicationContext,
            enabled,
            appIconRepository.launcherComponent(),
        )
        if (applied) {
            appSettings.putBoolean(THEMED_SHORTCUTS_PREF_KEY, enabled)
        }
        return applied
    }

    fun applySaved(): Boolean =
        refreshAppShortcuts(
            context.applicationContext,
            isThemed(),
            appIconRepository.launcherComponent(),
        )
}
