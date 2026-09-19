package com.originsu.manager.data.appearance

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.originsu.manager.data.AppSettingsRepository

enum class AppIcon(
    val aliasSuffix: String?,
) {
    ORIGIN_BLACK(null),
    ORIGIN_LIGHT("MainActivityLight"),
    ORIGIN_DEFAULT("MainActivityClassic"),
    KSU_OFFICIAL("MainActivityKsu"),
    RESUKISU("MainActivityResukisu"),
    RESUKISU_ALT("MainActivityResukisuAlt"),
    ;

    companion object {
        fun fromName(name: String?): AppIcon =
            entries.firstOrNull { it.name == name } ?: ORIGIN_BLACK
    }
}

class AppIconRepository(
    private val context: Context,
    private val appSettings: AppSettingsRepository,
) {
    fun getAppIcon(): AppIcon = AppIcon.fromName(appSettings.getString(PREF_APP_ICON))

    fun setAppIcon(icon: AppIcon): Boolean = runCatching {
        applyIcon(icon)
        appSettings.putString(PREF_APP_ICON, icon.name)
        true
    }.getOrDefault(false)

    /** Re-applies the saved icon (e.g. after process restart). */
    fun applySaved() {
        runCatching { applyIcon(getAppIcon()) }
    }

    /** Component the launcher shortcuts should target. */
    fun launcherComponent(): ComponentName {
        val alias = getAppIcon().aliasSuffix
        return if (alias == null) {
            ComponentName(context.packageName, "$uiPackage.MainActivity")
        } else {
            ComponentName(context.packageName, "$uiPackage.$alias")
        }
    }

    private val uiPackage: String
        get() = "${context.packageName}.ui"

    private fun applyIcon(icon: AppIcon) {
        val pm = context.packageManager
        val states = mapOf(
            ComponentName(context.packageName, "$uiPackage.MainActivity") to
                    (icon == AppIcon.ORIGIN_BLACK),
            ComponentName(context.packageName, "$uiPackage.MainActivityLight") to
                    (icon == AppIcon.ORIGIN_LIGHT),
            ComponentName(context.packageName, "$uiPackage.MainActivityClassic") to
                    (icon == AppIcon.ORIGIN_DEFAULT),
            ComponentName(context.packageName, "$uiPackage.MainActivityKsu") to
                    (icon == AppIcon.KSU_OFFICIAL),
            ComponentName(context.packageName, "$uiPackage.MainActivityResukisu") to
                    (icon == AppIcon.RESUKISU),
            ComponentName(context.packageName, "$uiPackage.MainActivityResukisuAlt") to
                    (icon == AppIcon.RESUKISU_ALT),
        )
        for ((component, enabled) in states) {
            pm.setComponentEnabledSetting(
                component,
                if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                },
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    companion object {
        const val PREF_APP_ICON = "app_icon"
    }
}
