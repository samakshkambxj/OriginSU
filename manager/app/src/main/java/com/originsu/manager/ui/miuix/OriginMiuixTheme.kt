package com.originsu.manager.ui.miuix

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import com.materialkolor.dynamiccolor.ColorSpec
import com.originsu.manager.ui.theme.ThemeConfig
import com.originsu.manager.ui.theme.isInDarkTheme
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

/**
 * Miuix theme wrapper for MIUIX-mode screens, adapted from rsuntk/KernelSU's
 * MiuixKernelSUTheme and driven by our [ThemeConfig].
 */
val LocalEnableBlur = staticCompositionLocalOf { false }

@Composable
fun OriginMiuixTheme(content: @Composable () -> Unit) {
    val themeConfig: ThemeConfig = koinInject()
    val context = LocalContext.current
    val darkTheme = isInDarkTheme(themeConfig.forceDarkMode)

    val miuixPaletteStyle = try {
        ThemePaletteStyle.valueOf(themeConfig.dynamicPaletteStyle.name)
    } catch (_: Exception) {
        ThemePaletteStyle.TonalSpot
    }

    val miuixColorSpec = if (themeConfig.dynamicColorSpec == ColorSpec.SpecVersion.SPEC_2025) {
        ThemeColorSpec.Spec2025
    } else {
        ThemeColorSpec.Spec2021
    }

    val resolvedKeyColor: Color = Color(themeConfig.seedColor)

    val controller = ThemeController(
        when {
            themeConfig.forceDarkMode == true ->
                if (themeConfig.useDynamicColor) ColorSchemeMode.MonetDark else ColorSchemeMode.Dark

            themeConfig.forceDarkMode == false ->
                if (themeConfig.useDynamicColor) ColorSchemeMode.MonetLight else ColorSchemeMode.Light

            else ->
                if (themeConfig.useDynamicColor) ColorSchemeMode.MonetSystem else ColorSchemeMode.System
        },
        keyColor = resolvedKeyColor,
        isDark = darkTheme,
        paletteStyle = miuixPaletteStyle,
        colorSpec = miuixColorSpec,
    )

    MiuixTheme(
        controller = controller,
        content = {
            LaunchedEffect(darkTheme) {
                val window = (context as? Activity)?.window ?: return@LaunchedEffect
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            CompositionLocalProvider(
                LocalContentColor provides MiuixTheme.colorScheme.onBackground,
                LocalEnableBlur provides themeConfig.isEnableBlur,
            ) {
                content()
            }
        }
    )
}
