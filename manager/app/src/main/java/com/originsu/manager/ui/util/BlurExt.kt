package com.originsu.manager.ui.util

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import com.originsu.manager.ui.theme.renderBackgroundBlur
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported

// Page-toned blur: both the backdrop base and the bar blend use the manager
// surfaceContainer so the MIUIX top bar melts into the page instead of
// rendering a darker miuix-surface band. Returns null when RenderEffect is
// unsupported so callers fall back to translucent surfaces instead of
// crashing RenderThread (same guard as rememberMaterial3BlurBackdrop).
@Composable
fun rememberBlurBackdrop(enableBlur: Boolean): LayerBackdrop? {
    if (!enableBlur || !isRenderEffectSupported()) return null
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

// Bitmap-based tile blur for MIUIX-mode cards (same mechanism as
// SettingsBaseWidget tiles): draws the pre-blurred background bitmap with the
// given tint. No RenderEffect, so no RenderThread risk. Pair with a
// transparent card background (see miuixTileColor) while blur is active.
fun Modifier.miuixHomeTileBlur(tint: Color): Modifier =
    renderBackgroundBlur(tint)
