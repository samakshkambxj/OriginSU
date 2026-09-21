package com.originsu.manager.ui.util

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur

// Page-toned blur: both the backdrop base and the bar blend use the manager
// surfaceContainer so the MIUIX top bar melts into the page instead of
// rendering a darker miuix-surface band.
@Composable
fun rememberBlurBackdrop(enableBlur: Boolean): LayerBackdrop? {
    if (!enableBlur || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

@Composable
fun BlurredBar(
    backdrop: LayerBackdrop?,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = if (backdrop != null) {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = 25f * LocalDensity.current.density,
                colors = BlurColors(
                    blendColors = listOf(
                        BlendColorEntry(color = MaterialTheme.colorScheme.surfaceContainer.copy(0.87f)),
                    ),
                ),
            )
        } else {
            Modifier
        },
    ) {
        content()
    }
}

// Tile blur for MIUIX-mode cards: samples the page backdrop (custom
// background) behind each tile, mirroring Material's blurEffect. The tile's
// own background stays translucent via homeCardColors(); this paints only
// the blurred image with no extra tint, using the MIUIX card corner radius.
@Composable
fun Modifier.miuixTileBlur(backdrop: LayerBackdrop?): Modifier {
    if (backdrop == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    return textureBlur(
        backdrop = backdrop,
        shape = RoundedCornerShape(CardDefaults.CornerRadius),
        blurRadius = 25f * LocalDensity.current.density,
    )
}
