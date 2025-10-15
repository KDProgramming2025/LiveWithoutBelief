/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.ui.designsystem

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb

/** Internal holder for pre-rendered shadow bitmaps.
 *  Named to match file for Detekt MatchingDeclarationName compliance.
 */
internal data class SurfaceRendering(val dark: ImageBitmap, val light: ImageBitmap)

/**
 * Pre-renders blurred shadow bitmaps for the given size and metrics so that the main composable draw phase
 * can simply draw these images with appropriate blend modes.
 */
internal fun renderShadowBitmaps(
    widthPx: Int,
    heightPx: Int,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float,
    offset: Float,
    blur: Float,
    colors: SurfaceStyleColors,
    metrics: SurfaceStyleMetrics,
): SurfaceRendering {
    fun render(color: Color, dx: Float, dy: Float): ImageBitmap {
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val cnv = android.graphics.Canvas(bmp)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
            this.color = color.toArgb()
            maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        }
        cnv.save()
        cnv.translate(dx, dy)
        cnv.drawRoundRect(left, top, right, bottom, radius, radius, p)
        cnv.restore()
        return bmp.asImageBitmap()
    }

    val darkImg = render(
        colors.shadowDark.copy(alpha = metrics.shadowDarkAlpha),
        offset,
        offset,
    )
    val mixedLight = androidx.compose.ui.graphics.lerp(
        colors.shadowLight,
        colors.reflectionTint,
        metrics.reflectionTintStrength,
    )
    val lightImg = render(
        mixedLight.copy(alpha = metrics.shadowLightAlpha),
        -offset,
        -offset,
    )
    return SurfaceRendering(dark = darkImg, light = lightImg)
}
