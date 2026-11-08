package com.originsu.manager.data.model

import android.content.pm.ApplicationInfo

/** One app that can hold a Sui (Shizuku) permission. */
data class SuiApp(
    val uid: Int,
    val packageName: String,
    val label: String,
    val mode: Int,
    val applicationInfo: ApplicationInfo? = null,
    /** True when the app asks for Shizuku in its manifest, whether or not Sui has a rule for it. */
    val requestsShizuku: Boolean = false,
    /** True for preinstalled/updated system apps. */
    val isSystem: Boolean = false,
) {
    val suiMode: SuiMode get() = SuiMode.of(mode)

    /** What Sui's own manager lists as a Shizuku app. */
    val isShizukuApp: Boolean get() = requestsShizuku || suiMode == SuiMode.ROOT || suiMode == SuiMode.SHELL

    companion object {
        const val MODE_DEFAULT = 0
        const val MODE_ROOT = 1 shl 1
        const val MODE_DENY = 1 shl 2
        const val MODE_HIDE = 1 shl 3
        const val MODE_SHELL = 1 shl 4
        const val MODE_MASK = MODE_ROOT or MODE_DENY or MODE_HIDE or MODE_SHELL

        /** The permission an app declares when it wants to talk to Shizuku. */
        const val PERMISSION_API_V23 = "moe.shizuku.manager.permission.API_V23"
    }
}

/** The permission Sui stores for one uid; a missing rule means [DEFAULT]. */
enum class SuiMode(val flags: Int) {
    DEFAULT(SuiApp.MODE_DEFAULT),
    ROOT(SuiApp.MODE_ROOT),
    SHELL(SuiApp.MODE_SHELL),
    DENY(SuiApp.MODE_DENY),
    HIDDEN(SuiApp.MODE_HIDE);

    val isExplicit: Boolean get() = this != DEFAULT

    companion object {
        /** An unknown word reads as "no rule" rather than a guessed permission. */
        fun of(flags: Int): SuiMode = entries.firstOrNull { it.flags == flags } ?: DEFAULT
    }
}
