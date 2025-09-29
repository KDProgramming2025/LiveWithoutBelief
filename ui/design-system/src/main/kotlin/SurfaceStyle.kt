/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
@file:Suppress("FunctionName")

package info.lwb.ui.designsystem

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Immutable
data class SurfaceStyleColors(
    val bgTop: Color,
    val bgBottom: Color,
    val surface: Color,
    val shadowDark: Color,
    val shadowLight: Color,
    val textPrimary: Color,
    val textMuted: Color,
    // Subtle tint applied to the top-left external light reflection
    val reflectionTint: Color,
)

@Immutable
data class SurfaceStyleMetrics(
    val cornerRadius: Dp,
    val shadowBlur: Dp,
    val shadowOffset: Dp,
    val shadowPadding: Dp,
    val rimLightAlpha: Float,
    val rimDarkAlpha: Float,
    val shadowDarkAlpha: Float,
    val shadowLightAlpha: Float,
    val strokeLightWidth: Dp,
    val strokeDarkWidth: Dp,
    // Strength (0..1) of color tint mixed into the external light reflection
    val reflectionTintStrength: Float,
)

object SurfaceStyleDefaults {
    val DarkMetrics = SurfaceStyleMetrics(
        cornerRadius = 16.dp,
        shadowBlur = 7.dp,
        shadowOffset = 3.dp,
        shadowPadding = 10.dp,
        rimLightAlpha = 0.68f,
        rimDarkAlpha = 0.52f,
        shadowDarkAlpha = 1.00f,
        shadowLightAlpha = 1.00f,
        strokeLightWidth = 1.4.dp,
        strokeDarkWidth = 1.0.dp,
        reflectionTintStrength = 0.26f,
    )
    val LightMetrics = SurfaceStyleMetrics(
        cornerRadius = 16.dp,
        shadowBlur = 6.dp,
        shadowOffset = 3.dp,
        shadowPadding = 10.dp,
        rimLightAlpha = 0.58f,
        rimDarkAlpha = 0.30f,
        shadowDarkAlpha = 0.95f,
        shadowLightAlpha = 1.00f,
        strokeLightWidth = 1.2.dp,
        strokeDarkWidth = 0.8.dp,
        reflectionTintStrength = 0.22f,
    )
}

private val DarkColors = SurfaceStyleColors(
    // Warm dark background and surface
    bgTop = Color(0xFF1F1A17),
    bgBottom = Color(0xFF191410),
    surface = Color(0xFF24201C),
    // Colder-tinted highlights for a cooler dark theme
    shadowDark = Color(0xFF0E0B09),
    shadowLight = Color(0xFF34373D),
    // Readable, slightly warm text
    textPrimary = Color(0xFFEEE7DF),
    textMuted = Color(0xFFB9AEA6),
    // Colder tint for subtle cool reflection
    reflectionTint = Color(0xFFE0F0FF),
)

private val LightColors = SurfaceStyleColors(
    // Calming light warm background and surface (darkened)
    // slightly darker than FFF7F0
    bgTop = Color(0xFFFAEFE6),
    // slightly darker than FDEFE6
    bgBottom = Color(0xFFF3E6DB),
    // slightly darker than FFF5EC
    surface = Color(0xFFF7EDE3),
    // Warm-tinted shadows/highlights
    shadowDark = Color(0xFFD8C9BF),
    shadowLight = Color(0xFFFDF6F0),
    // Readable warm-ish text
    textPrimary = Color(0xFF221A16),
    textMuted = Color(0xFF7A6B63),
    // Keep a subtle cool tint for contrast
    reflectionTint = Color(0xFFE6F2FF),
)

val LocalSurfaceStyle = staticCompositionLocalOf { DarkColors }
val LocalSurfaceMetrics = staticCompositionLocalOf { SurfaceStyleDefaults.DarkMetrics }
val LocalIsDarkTheme = staticCompositionLocalOf { true }

@Composable
fun ProvideSurfaceStyle(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) DarkColors else LightColors
    val metrics = if (dark) SurfaceStyleDefaults.DarkMetrics else SurfaceStyleDefaults.LightMetrics
    CompositionLocalProvider(
        LocalSurfaceStyle provides colors,
        LocalSurfaceMetrics provides metrics,
        LocalIsDarkTheme provides dark,
        content = content,
    )
}

@Composable
fun GrainyBackground(modifier: Modifier = Modifier) {
    val c = LocalSurfaceStyle.current
    val gradient = Brush.verticalGradient(listOf(c.bgTop, c.bgBottom))
    Box(
        modifier
            .drawWithCache {
                onDrawBehind {
                    drawRect(brush = gradient)
                }
            },
    )
}

@Composable
fun RaisedSurface(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = LocalSurfaceStyle.current
    val m = LocalSurfaceMetrics.current
    val isDark = LocalIsDarkTheme.current
    // Container shape is always tied to metrics to match highlights/shadows
    val shape = RoundedCornerShape(m.cornerRadius)
    // Ensure there's enough padding so external blurred shadows are not clipped
    val computedPad = (m.shadowBlur + m.shadowOffset + (m.strokeLightWidth * 2))
    val shadowPadding = if (m.shadowPadding >= computedPad) m.shadowPadding else computedPad

    Box(
        modifier
            .drawWithCache {
                val blur = m.shadowBlur.toPx()
                val offset = m.shadowOffset.toPx()
                val radius = m.cornerRadius.toPx()
                val padPx = shadowPadding.toPx()
                val left = padPx
                val top = padPx
                val right = size.width - padPx
                val bottom = size.height - padPx

                val w = size.width.roundToInt().coerceAtLeast(2)
                val h = size.height.roundToInt().coerceAtLeast(2)

                fun renderShadow(color: Color, dx: Float, dy: Float): androidx.compose.ui.graphics.ImageBitmap {
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
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

                val darkImg = renderShadow(c.shadowDark.copy(alpha = m.shadowDarkAlpha), offset, offset)
                val mixedLight = lerp(c.shadowLight, c.reflectionTint, m.reflectionTintStrength)
                val lightImg = renderShadow(mixedLight.copy(alpha = m.shadowLightAlpha), -offset, -offset)

                onDrawBehind {
                    // Apply requested blend modes per theme
                    val shadowMode = if (isDark) BlendMode.Darken else BlendMode.Multiply
                    val lightMode = if (isDark) BlendMode.Lighten else BlendMode.Screen
                    drawImage(darkImg, blendMode = shadowMode)
                    drawImage(lightImg, blendMode = lightMode)
                }
            },
    ) {
        // Inner content box is separate from the shadow area so we don't cover shadows
        Box(
            Modifier
                .padding(shadowPadding)
                .clip(shape)
                .background(c.surface)
                .drawWithContent {
                    drawContent()
                    val r = m.cornerRadius.toPx()
                    drawRoundRect(
                        color = c.shadowLight.copy(alpha = m.rimLightAlpha),
                        style = Stroke(width = m.strokeLightWidth.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                    )
                    // Compute inset in the DrawScope where toPx() is available
                    val inset = m.strokeLightWidth.toPx()
                    withTransform({ translate(inset, inset) }) {
                        drawRoundRect(
                            color = c.shadowDark.copy(alpha = m.rimDarkAlpha),
                            style = Stroke(width = m.strokeDarkWidth.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                        )
                    }
                },
        ) { content() }
    }
}

@Composable
fun RaisedButton(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    RaisedSurface(modifier.clickable(onClick = onClick)) { content() }
}

@Composable
fun InsetIconWell(modifier: Modifier = Modifier, wellSize: Dp = 56.dp, content: @Composable BoxScope.() -> Unit) {
    val c = LocalSurfaceStyle.current
    val m = LocalSurfaceMetrics.current
    val wellCorner = (m.cornerRadius - 4.dp).coerceAtLeast(8.dp)
    Box(
        modifier
            .clip(RoundedCornerShape(wellCorner))
            .background(c.surface)
            .drawBehind {
                val corner = wellCorner.toPx()
                drawIntoCanvas { canvas ->
                    val paint = Paint()
                    val fp = paint.asFrameworkPaint()
                    fp.isAntiAlias = true
                    fp.style = android.graphics.Paint.Style.STROKE
                    fp.strokeWidth = 8f
                    fp.color = c.shadowDark.copy(alpha = m.shadowDarkAlpha * 0.6f).toArgb()
                    fp.maskFilter = BlurMaskFilter(m.shadowBlur.toPx() * 0.45f, BlurMaskFilter.Blur.NORMAL)
                    canvas.save()
                    canvas.translate(m.shadowOffset.toPx() * 0.14f, m.shadowOffset.toPx() * 0.2f)
                    canvas.drawRoundRect(0f, 0f, this.size.width, this.size.height, corner, corner, paint)
                    canvas.restore()

                    fp.color = c.shadowLight.copy(alpha = m.shadowLightAlpha * 0.6f).toArgb()
                    fp.maskFilter = BlurMaskFilter(m.shadowBlur.toPx() * 0.4f, BlurMaskFilter.Blur.NORMAL)
                    canvas.save()
                    canvas.translate(-m.shadowOffset.toPx() * 0.12f, -m.shadowOffset.toPx() * 0.12f)
                    canvas.drawRoundRect(0f, 0f, this.size.width, this.size.height, corner, corner, paint)
                    canvas.restore()
                }
            }
            .then(Modifier.size(wellSize)),
    ) { content() }
}

@Composable
fun RaisedIconWell(
    modifier: Modifier = Modifier,
    wellSize: Dp = 56.dp,
    innerPadding: Dp = 6.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val m = LocalSurfaceMetrics.current
    // Match RaisedSurface's internal computed padding so inner area equals wellSize
    val computedPad = (m.shadowBlur + m.shadowOffset + (m.strokeLightWidth * 2))
    val shadowPadding = if (m.shadowPadding >= computedPad) m.shadowPadding else computedPad
    val total = wellSize + shadowPadding * 2
    RaisedSurface(
        modifier = modifier.size(total),
    ) {
        // Fill the inner area (which will be wellSize x wellSize) with a small inner padding
        Box(
            Modifier
                .size(wellSize)
                .padding(innerPadding),
            content = content,
        )
    }
}
