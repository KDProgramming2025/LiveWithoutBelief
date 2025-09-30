/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.ui.designsystem

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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// Constants for inset well rendering factors (extracted from magic numbers)
private const val INSET_WELL_STROKE_WIDTH = 8f
private const val DARK_WELL_ALPHA_MULT = 0.6f
private const val DARK_WELL_BLUR_MULT = 0.45f
private const val DARK_WELL_TRANSLATE_X_MULT = 0.14f
private const val DARK_WELL_TRANSLATE_Y_MULT = 0.2f
private const val LIGHT_WELL_ALPHA_MULT = 0.6f
private const val LIGHT_WELL_BLUR_MULT = 0.4f
private const val LIGHT_WELL_TRANSLATE_X_MULT = 0.12f
private const val LIGHT_WELL_TRANSLATE_Y_MULT = 0.12f
private const val STROKE_PADDING_MULTIPLIER = 2
private const val MIN_BITMAP_DIMENSION = 2
private val CORNER_ADJUST = 4.dp
private val MIN_WELL_CORNER = 8.dp

/**
 * Immutable set of tonal and shadow colors that define the raised/inset surface aesthetic.
 *
 * These colors are combined at runtime to draw layered soft shadows, rim lights and


 * subtle reflections that emulate a tactile, pseudo-neumorphic style while preserving
 * sufficient contrast for readability.
 *
 * @property bgTop Top color of the vertical background gradient.
 * @property bgBottom Bottom color of the vertical background gradient.
 * @property surface Primary fill color for raised components.
 * @property shadowDark Dark shadow tint used for lower-right depth.
 * @property shadowLight Light shadow/highlight tint used for upper-left light.
 * @property textPrimary High contrast text color.
 * @property textMuted Muted/secondary text color.
 * @property reflectionTint Cool tint blended into the light shadow for subtle reflection.
 */
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

/**
 * Metrics controlling geometry, blur radii and alpha blending for surface rendering.
 *
 * @property cornerRadius Corner radius applied to raised/inset surfaces.
 * @property shadowBlur Blur radius applied to external soft shadows.
 * @property shadowOffset Positional offset (both x/y) for shadow translation.
 * @property shadowPadding Extra outer padding reserved so blurred shadows are not clipped.
 * @property rimLightAlpha Opacity of the outer light rim stroke.
 * @property strokeLightWidth Width of the outer light stroke.
 * @property strokeDarkWidth Width of the inner dark stroke.
 * @property reflectionTintStrength Lerp fraction mixing [SurfaceStyleColors.shadowLight] with [SurfaceStyleColors.reflectionTint].
 * @property rimDarkAlpha Opacity of the inner (dark) rim stroke.
 * @property shadowDarkAlpha Alpha multiplier applied to the dark external shadow bitmap.
 */
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

/**
 * Theme default metric collections for dark and light modes. These presets ensure
 * consistent spacing and lighting ratios across components.
 * Prefer cloning and adjusting at the call site only for experimental themes; otherwise
 * use as-is through the composition locals.
 */
object SurfaceStyleDefaults {
    // Metric base constants (internal naming uses screaming snake per static constant rule)
    private val DEFAULT_CORNER = 16.dp
    private val DARK_SHADOW_BLUR = 7.dp
    private val LIGHT_SHADOW_BLUR = 6.dp
    private val DEFAULT_SHADOW_OFFSET = 3.dp
    private val DEFAULT_SHADOW_PADDING = 10.dp
    private const val DARK_RIM_LIGHT_ALPHA = 0.68f
    private const val DARK_RIM_DARK_ALPHA = 0.52f
    private const val LIGHT_RIM_LIGHT_ALPHA = 0.58f
    private const val LIGHT_RIM_DARK_ALPHA = 0.30f
    private const val DARK_SHADOW_LIGHT_ALPHA = 1.00f
    private const val LIGHT_SHADOW_DARK_ALPHA = 0.95f
    private const val LIGHT_SHADOW_LIGHT_ALPHA = 1.00f
    private val DARK_STROKE_LIGHT_WIDTH = 1.4.dp
    private val DARK_STROKE_DARK_WIDTH = 1.0.dp
    private val LIGHT_STROKE_LIGHT_WIDTH = 1.2.dp
    private val LIGHT_STROKE_DARK_WIDTH = 0.8.dp
    private const val DARK_REFLECTION_TINT_STRENGTH = 0.26f
    private const val LIGHT_REFLECTION_TINT_STRENGTH = 0.22f
    // Dark shadow alpha constant (missing earlier) for consistency with light metrics
    private const val DARK_SHADOW_DARK_ALPHA = 0.90f
    /** Default metrics for dark theme raised and inset surfaces. */
    val DarkMetrics =
        SurfaceStyleMetrics(
            cornerRadius = DEFAULT_CORNER,
            shadowBlur = DARK_SHADOW_BLUR,
            shadowOffset = DEFAULT_SHADOW_OFFSET,
            shadowPadding = DEFAULT_SHADOW_PADDING,
            rimLightAlpha = DARK_RIM_LIGHT_ALPHA,
            rimDarkAlpha = DARK_RIM_DARK_ALPHA,
            shadowDarkAlpha = DARK_SHADOW_DARK_ALPHA,
            shadowLightAlpha = DARK_SHADOW_LIGHT_ALPHA,
            strokeLightWidth = DARK_STROKE_LIGHT_WIDTH,
            strokeDarkWidth = DARK_STROKE_DARK_WIDTH,
            reflectionTintStrength = DARK_REFLECTION_TINT_STRENGTH,
        )
    /** Default metrics for light theme raised and inset surfaces. */
    val LightMetrics =
        SurfaceStyleMetrics(
            cornerRadius = DEFAULT_CORNER,
            shadowBlur = LIGHT_SHADOW_BLUR,
            shadowOffset = DEFAULT_SHADOW_OFFSET,
            shadowPadding = DEFAULT_SHADOW_PADDING,
            rimLightAlpha = LIGHT_RIM_LIGHT_ALPHA,
            rimDarkAlpha = LIGHT_RIM_DARK_ALPHA,
            shadowDarkAlpha = LIGHT_SHADOW_DARK_ALPHA,
            shadowLightAlpha = LIGHT_SHADOW_LIGHT_ALPHA,
            strokeLightWidth = LIGHT_STROKE_LIGHT_WIDTH,
            strokeDarkWidth = LIGHT_STROKE_DARK_WIDTH,
            reflectionTintStrength = LIGHT_REFLECTION_TINT_STRENGTH,
        )
}

// Extracted color hex constants to satisfy MagicNumber rule
private const val COLOR_DARK_BG_TOP = 0xFF1F1A17
private const val COLOR_DARK_BG_BOTTOM = 0xFF191410
private const val COLOR_DARK_SURFACE = 0xFF24201C
private const val COLOR_DARK_SHADOW_DARK = 0xFF0E0B09
private const val COLOR_DARK_SHADOW_LIGHT = 0xFF34373D
private const val COLOR_DARK_TEXT_PRIMARY = 0xFFEEE7DF
private const val COLOR_DARK_TEXT_MUTED = 0xFFB9AEA6
private const val COLOR_DARK_REFLECTION_TINT = 0xFFE0F0FF

private val DarkColors =
    SurfaceStyleColors(
        bgTop = Color(COLOR_DARK_BG_TOP.toInt()),
        bgBottom = Color(COLOR_DARK_BG_BOTTOM.toInt()),
        surface = Color(COLOR_DARK_SURFACE.toInt()),
        shadowDark = Color(COLOR_DARK_SHADOW_DARK.toInt()),
        shadowLight = Color(COLOR_DARK_SHADOW_LIGHT.toInt()),
        textPrimary = Color(COLOR_DARK_TEXT_PRIMARY.toInt()),
        textMuted = Color(COLOR_DARK_TEXT_MUTED.toInt()),
        reflectionTint = Color(COLOR_DARK_REFLECTION_TINT.toInt()),
    )

private const val COLOR_LIGHT_BG_TOP = 0xFFFAEFE6
private const val COLOR_LIGHT_BG_BOTTOM = 0xFFF3E6DB
private const val COLOR_LIGHT_SURFACE = 0xFFF7EDE3
private const val COLOR_LIGHT_SHADOW_DARK = 0xFFD8C9BF
private const val COLOR_LIGHT_SHADOW_LIGHT = 0xFFFDF6F0
private const val COLOR_LIGHT_TEXT_PRIMARY = 0xFF221A16
private const val COLOR_LIGHT_TEXT_MUTED = 0xFF7A6B63
private const val COLOR_LIGHT_REFLECTION_TINT = 0xFFE6F2FF

private val LightColors =
    SurfaceStyleColors(
        bgTop = Color(COLOR_LIGHT_BG_TOP.toInt()),
        bgBottom = Color(COLOR_LIGHT_BG_BOTTOM.toInt()),
        surface = Color(COLOR_LIGHT_SURFACE.toInt()),
        shadowDark = Color(COLOR_LIGHT_SHADOW_DARK.toInt()),
        shadowLight = Color(COLOR_LIGHT_SHADOW_LIGHT.toInt()),
        textPrimary = Color(COLOR_LIGHT_TEXT_PRIMARY.toInt()),
        textMuted = Color(COLOR_LIGHT_TEXT_MUTED.toInt()),
        reflectionTint = Color(COLOR_LIGHT_REFLECTION_TINT.toInt()),
    )

/** CompositionLocal exposing the active [SurfaceStyleColors]. */
val LocalSurfaceStyle = staticCompositionLocalOf { DarkColors }

/** CompositionLocal exposing the active [SurfaceStyleMetrics]. */
val LocalSurfaceMetrics = staticCompositionLocalOf { SurfaceStyleDefaults.DarkMetrics }

/** CompositionLocal flag indicating whether dark surface styling is active. */
val LocalIsDarkTheme = staticCompositionLocalOf { true }

/**
 * Provides surface styling composition locals for descendants.
 *
 * Chooses between dark and light color/metric presets and exposes them through
 * [LocalSurfaceStyle], [LocalSurfaceMetrics] and [LocalIsDarkTheme]. Wrap any subtree that
 * renders raised / inset surfaces so the correct metrics propagate. This does not apply a
 * MaterialTheme; combine with your higher-level theme as needed.
 *
 * @param dark Whether to use dark style defaults.
 * @param content Composable content that can read the provided locals.
 */
@Composable
fun ProvideSurfaceStyle(
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    val colors =
        if (dark) {
            DarkColors
        } else {
            LightColors
        }
    val metrics =
        if (dark) {
            SurfaceStyleDefaults.DarkMetrics
        } else {
            SurfaceStyleDefaults.LightMetrics
        }
    CompositionLocalProvider(
        LocalSurfaceStyle provides colors,
        LocalSurfaceMetrics provides metrics,
        LocalIsDarkTheme provides dark,
        content = content,
    )
}

/**
 * Full‑area vertical background gradient using the provided surface style colors.
 *
 * Typically placed at a top-level container beneath all raised surfaces. A subtle noise /
 * grain layer can be added later if desired by extending the draw cache.
 *
 * @param modifier Modifier chain for size / positioning.
 */
@Composable
fun GrainyBackground(modifier: Modifier = Modifier) {
    val c = LocalSurfaceStyle.current
    val gradient = Brush.verticalGradient(listOf(c.bgTop, c.bgBottom))
    Box(
        modifier =
            .drawWithCache {
                onDrawBehind {
                    drawRect(brush = gradient)
                }
            },
    )
}

/**
 * Elevated surface container that renders soft bilateral shadows and rim strokes.
 *
 * Internally allocates cached bitmaps for blurred shadows sized to the composable's
 * layout space. Keep usage to moderately sized elements to avoid large bitmap allocations.
 * The inner content is clipped to the rounded shape and receives an inner highlight +
 * dark rim for subtle depth.
 *
 * @param modifier Modifier for layout / interaction.
 * @param content BoxScope content inside the elevated surface.
 */
@Composable
fun RaisedSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalSurfaceStyle.current
    val metrics = LocalSurfaceMetrics.current
    val isDark = LocalIsDarkTheme.current
    val shape = RoundedCornerShape(metrics.cornerRadius)
    val shadowPadding = calculateShadowPadding(metrics)
    Box(
        modifier
            .drawWithCache {
                val padPx = shadowPadding.toPx()
                val left = padPx
                val top = padPx
                val right = size.width - padPx
                val bottom = size.height - padPx
                val w = size.width.roundToInt().coerceAtLeast(MIN_BITMAP_DIMENSION)
                val h = size.height.roundToInt().coerceAtLeast(MIN_BITMAP_DIMENSION)
                val bitmaps = renderShadowBitmaps(
                    widthPx = w,
                    heightPx = h,
                    left = left,
                    top = top,
                    right = right,
                    bottom = bottom,
                    radius = metrics.cornerRadius.toPx(),
                    offset = metrics.shadowOffset.toPx(),
                    blur = metrics.shadowBlur.toPx(),
                    colors = colors,
                    metrics = metrics,
                )
                onDrawBehind {
                    val shadowMode =
                        if (isDark) {
                            BlendMode.Darken
                        } else {
                            BlendMode.Multiply
                        }
                    val lightMode =
                        if (isDark) {
                            BlendMode.Lighten
                        } else {
                            BlendMode.Screen
                        }
                    drawImage(bitmaps.dark, blendMode = shadowMode)
                    drawImage(bitmaps.light, blendMode = lightMode)
                }
            },
    ) {
        Box(
            Modifier =
                .padding(shadowPadding)
                .surfaceRim(colors, metrics, shape),
        ) { content() }
    }
}

private fun calculateShadowPadding(metrics: SurfaceStyleMetrics): Dp {
    val computed =
        metrics.shadowBlur +
            metrics.shadowOffset +
            (metrics.strokeLightWidth * STROKE_PADDING_MULTIPLIER)
    return if (metrics.shadowPadding >= computed) {
        metrics.shadowPadding
    } else {
        computed
    }
}

private fun Modifier.surfaceRim(colors: SurfaceStyleColors, metrics: SurfaceStyleMetrics, shape: RoundedCornerShape): Modifier =
    clip(shape)
        .background(colors.surface)
        .drawWithContent {
            drawContent()
            val r = metrics.cornerRadius.toPx()
            drawRoundRect(
                color = colors.shadowLight.copy(alpha = metrics.rimLightAlpha),
                style = Stroke(width = metrics.strokeLightWidth.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
            )
            val inset = metrics.strokeLightWidth.toPx()
            withTransform({ translate(inset, inset) }) {
                drawRoundRect(
                    color = colors.shadowDark.copy(alpha = metrics.rimDarkAlpha),
                    style = Stroke(width = metrics.strokeDarkWidth.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                )
            }
        }

/**
 * Convenience wrapper around [RaisedSurface] that applies a clickable modifier.
 *
 * @param modifier Modifier chain.
 * @param onClick Click callback.
 * @param content Button content (icon / text) aligned within the raised surface.
 */
@Composable
fun RaisedButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    RaisedSurface(
        modifier
            .clickable(onClick = onClick),
    ) { content() }
}

/**
 * Inset (recessed) square well used for housing an icon or small glyph.
 *
 * Creates an illusion of an inward pressed shape by drawing opposing blurred strokes.
 * Prefer square sizes. For very small sizes (<40.dp) consider reducing stroke/blur metrics.
 *
 * @param modifier Modifier chain.
 * @param wellSize Outer size of the well (width == height).
 * @param content Content (typically an Icon) centered inside.
 */
@Composable
fun InsetIconWell(
    modifier: Modifier = Modifier,
    wellSize: Dp = 56.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalSurfaceStyle.current
    val m = LocalSurfaceMetrics.current
    val wellCorner = (m.cornerRadius - CORNER_ADJUST).coerceAtLeast(MIN_WELL_CORNER)
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
                    fp.strokeWidth = INSET_WELL_STROKE_WIDTH
                    fp.color = c.shadowDark.copy(alpha = m.shadowDarkAlpha * DARK_WELL_ALPHA_MULT).toArgb()
                    fp.maskFilter = BlurMaskFilter(m.shadowBlur.toPx() * DARK_WELL_BLUR_MULT, BlurMaskFilter.Blur.NORMAL)
                    canvas.save()
                    canvas.translate(
                        m.shadowOffset.toPx() * DARK_WELL_TRANSLATE_X_MULT,
                        m.shadowOffset.toPx() * DARK_WELL_TRANSLATE_Y_MULT,
                    )
                    canvas.drawRoundRect(0f, 0f, this.size.width, this.size.height, corner, corner, paint)
                    canvas.restore()

                    fp.color = c.shadowLight.copy(alpha = m.shadowLightAlpha * LIGHT_WELL_ALPHA_MULT).toArgb()
                    fp.maskFilter = BlurMaskFilter(m.shadowBlur.toPx() * LIGHT_WELL_BLUR_MULT, BlurMaskFilter.Blur.NORMAL)
                    canvas.save()
                    canvas.translate(
                        -m.shadowOffset.toPx() * LIGHT_WELL_TRANSLATE_X_MULT,
                        -m.shadowOffset.toPx() * LIGHT_WELL_TRANSLATE_Y_MULT,
                    )
                    canvas.drawRoundRect(0f, 0f, this.size.width, this.size.height, corner, corner, paint)
                    canvas.restore()
                }
            }
            .then(Modifier.size(wellSize)),
    ) { content() }
}

/**
 * Hybrid raised container sized precisely so that the inner clipped region is `wellSize`.
 *
 * Allocates outer shadow padding to keep soft shadows visible without clipping and centers
 * a square inner area that can host an icon with configurable [innerPadding].
 *
 * @param modifier Modifier chain.
 * @param wellSize Target size of the inner square content area.
 * @param innerPadding Padding applied inside the inner square before content.
 * @param content Icon or custom content.
 */
@Composable
fun RaisedIconWell(
    modifier: Modifier = Modifier,
    wellSize: Dp = 56.dp,
    innerPadding: Dp = 6.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val m = LocalSurfaceMetrics.current
    // Match RaisedSurface's internal computed padding so inner area equals wellSize
    val computedPad =
        m.shadowBlur +
            m.shadowOffset +
            (m.strokeLightWidth * 2)
    val shadowPadding =
        if (m.shadowPadding >= computedPad) {
            m.shadowPadding
        } else {
            computedPad
        }
    val total = wellSize + shadowPadding * 2
    RaisedSurface(
        modifier =
            modifier
                .size(total),
    ) {
        // Fill the inner area (which will be wellSize x wellSize) with a small inner padding
        Box(
            modifier =
                Modifier
                    .size(wellSize)
                    .padding(innerPadding),
            content = content,
        )
    }
}
