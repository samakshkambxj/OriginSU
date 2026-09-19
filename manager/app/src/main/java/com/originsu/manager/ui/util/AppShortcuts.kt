package com.originsu.manager.ui.util

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.util.Log
import com.originsu.manager.R
import com.originsu.manager.ui.MainActivity

const val SHORTCUT_TYPE_EXTRA = "shortcut_type"
const val SHORTCUT_TYPE_SUPERUSER = "tab_superuser"
const val SHORTCUT_TYPE_MODULES = "tab_modules"
const val SHORTCUT_TYPE_SETTINGS = "tab_settings"

const val ACTION_SHORTCUT_SUPERUSER = "com.originsu.manager.action.SHORTCUT_SUPERUSER"
const val ACTION_SHORTCUT_MODULES = "com.originsu.manager.action.SHORTCUT_MODULES"
const val ACTION_SHORTCUT_SETTINGS = "com.originsu.manager.action.SHORTCUT_SETTINGS"

/**
 * App-shortcut icon set switch.
 *
 * When [themed] is true the Superuser/Modules/Settings shortcuts use the
 * WildKSU-style themed icons, otherwise the stock launcher icon. Implemented
 * with dynamic shortcuts so no package name is hardcoded and all build
 * flavors keep working.
 *
 * @return true when the shortcuts were applied, false when the system
 * rejected them (e.g. rate limiting) or an error occurred.
 */
fun refreshAppShortcuts(context: Context, themed: Boolean): Boolean {
    val shortcutManager = context.getSystemService(ShortcutManager::class.java)
        ?: return false
    if (shortcutManager.isRateLimitingActive) {
        Log.w(TAG, "refreshAppShortcuts: rate limited, themed=$themed")
        return false
    }
    return runCatching {
        val iconRes = { themedRes: Int ->
            if (themed) themedRes else R.mipmap.ic_launcher
        }
        val shortcuts = listOf(
            ShortcutInfo.Builder(context, SHORTCUT_TYPE_SUPERUSER)
                .setShortLabel(context.getString(R.string.superuser))
                .setLongLabel(context.getString(R.string.superuser))
                .setIcon(Icon.createWithResource(context, iconRes(R.mipmap.ic_superuser)))
                .setIntent(
                    Intent(ACTION_SHORTCUT_SUPERUSER, null, context, MainActivity::class.java)
                        .putExtra(SHORTCUT_TYPE_EXTRA, SHORTCUT_TYPE_SUPERUSER),
                )
                .build(),
            ShortcutInfo.Builder(context, SHORTCUT_TYPE_MODULES)
                .setShortLabel(context.getString(R.string.module))
                .setLongLabel(context.getString(R.string.module))
                .setIcon(Icon.createWithResource(context, iconRes(R.mipmap.ic_modules)))
                .setIntent(
                    Intent(ACTION_SHORTCUT_MODULES, null, context, MainActivity::class.java)
                        .putExtra(SHORTCUT_TYPE_EXTRA, SHORTCUT_TYPE_MODULES),
                )
                .build(),
            ShortcutInfo.Builder(context, SHORTCUT_TYPE_SETTINGS)
                .setShortLabel(context.getString(R.string.settings))
                .setLongLabel(context.getString(R.string.settings))
                .setIcon(Icon.createWithResource(context, iconRes(R.mipmap.ic_settings)))
                .setIntent(
                    Intent(ACTION_SHORTCUT_SETTINGS, null, context, MainActivity::class.java)
                        .putExtra(SHORTCUT_TYPE_EXTRA, SHORTCUT_TYPE_SETTINGS),
                )
                .build(),
        )
        shortcutManager.setDynamicShortcuts(shortcuts)
        true
    }.onFailure { error ->
        Log.w(TAG, "refreshAppShortcuts failed, themed=$themed", error)
    }.getOrDefault(false)
}

private const val TAG = "AppShortcuts"
