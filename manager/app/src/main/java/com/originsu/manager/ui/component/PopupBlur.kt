package com.originsu.manager.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.originsu.manager.ui.theme.CardConfig
import com.originsu.manager.ui.theme.ThemeConfig
import com.originsu.manager.ui.theme.renderBackgroundBlur
import org.koin.compose.koinInject

/**
 * Container color for popup surfaces (dialogs, menus, bottom sheets) that
 * matches the Apple-style blurred navbar: translucent when blur is enabled,
 * opaque otherwise.
 */
@Composable
fun popupContainerColor(): Color {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    return if (themeConfig.isEnableBlurExp) {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(
            alpha = if (cardConfig.isCustomBackgroundEnabled) {
                cardConfig.cardAlpha
            } else {
                0.8f
            }
        )
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
}

/**
 * Samples the shared blur backdrop behind popup content. No-op when blur is
 * disabled; safe to apply unconditionally. Coordinates map via the screen so
 * it also works inside dialog/popup windows.
 */
fun Modifier.popupBlur(): Modifier = renderBackgroundBlur()
