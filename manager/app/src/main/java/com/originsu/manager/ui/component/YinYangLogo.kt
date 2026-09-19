package com.originsu.manager.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/**
 * Procedural taijitu (yin-yang) mark drawn with Canvas, so it follows the
 * theme instead of shipping a raster asset. Rotate it (e.g. via
 * `Modifier.graphicsLayer { rotationZ = ... }`) for the animated home logo.
 *
 * @param dark color of the base disc, top eye and bottom lobe.
 * @param light color of the right half, top lobe and bottom eye.
 */
@Composable
fun YinYangLogo(
    modifier: Modifier = Modifier,
    dark: Color = MaterialTheme.colorScheme.primary,
    light: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val lobe = r / 2f
        val eye = r / 8f

        // Base disc.
        drawCircle(color = dark, radius = r, center = center)
        // Light right half of the disc.
        drawArc(
            color = light,
            startAngle = 270f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(cx - r, cy - r),
            size = Size(2f * r, 2f * r),
        )
        // Lobes forming the S-curve.
        drawCircle(color = light, radius = lobe, center = Offset(cx, cy - lobe))
        drawCircle(color = dark, radius = lobe, center = Offset(cx, cy + lobe))
        // Eyes.
        drawCircle(color = dark, radius = eye, center = Offset(cx, cy - lobe))
        drawCircle(color = light, radius = eye, center = Offset(cx, cy + lobe))
    }
}
