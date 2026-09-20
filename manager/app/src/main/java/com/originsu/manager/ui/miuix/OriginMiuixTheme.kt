package com.originsu.manager.ui.miuix

import android.app.Activity
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
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

/**
 * Card colors lifted one tonal step so flat cards stay visible on the
 * manager-matched page background.
 */
@Composable
fun homeCardColors() = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
    color = MiuixTheme.colorScheme.surfaceContainerHigh,
)

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

    // NB: miuix only honors keyColor in Monet modes. Always use a Monet mode
    // so the home follows the manager accent even with dynamic color off.
    val resolvedKeyColor: Color = if (
        themeConfig.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ) {
        if (darkTheme) dynamicDarkColorScheme(context).primary
        else dynamicLightColorScheme(context).primary
    } else {
        Color(themeConfig.seedColor)
    }

    val controller = ThemeController(
        when (themeConfig.forceDarkMode) {
            true -> ColorSchemeMode.MonetDark
            false -> ColorSchemeMode.MonetLight
            null -> ColorSchemeMode.MonetSystem
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
