/*
 * SPDX-License-Identifier: Apache-2.0
 * Deprecated: Legacy skeuomorphic components. Do not use.
 */
@file:Suppress("FunctionName")

package info.lwb.ui.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Skeuomorphic design tokens inspired by the admin web panel (admin/web/assets/css/tokens.css).
 * Provides colors and shapes used by custom components without relying on Material 3 look.
 *
 * Usage:
 * - Wrap screens with [ProvideSkeuoTheme] and read colors via [LocalSkeuoColors].
 * - Build UI using [SkeuoCard], [IconWell], and [SkeuoPrimaryButton] for a cohesive look.
 */
@Immutable
data class SkeuoColors(
    val bg: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val inset: Color,
    val border: Color,
    val borderStrong: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val textSubdued: Color,
    val accent: Color,
)

private val DarkSkeuoColors = SkeuoColors(
    bg = Color(0xFF161718),
    surface1 = Color(0xFF1C1E20),
    surface2 = Color(0xFF232529),
    surface3 = Color(0xFF2A2D31),
    inset = Color(0xFF131415),
    border = Color(0xFF2C2F33),
    borderStrong = Color(0xFF383C41),
    textPrimary = Color(0xFFEAE7E0),
    textMuted = Color(0xFFBBB8B2),
    textSubdued = Color(0xFF95928E),
    accent = Color(0xFFCFAE67),
)

private val LightSkeuoColors = SkeuoColors(
    // Light approximation maintaining similar contrast
    bg = Color(0xFFF4F3F1),
    surface1 = Color(0xFFEDEBE8),
    surface2 = Color(0xFFE5E1DC),
    surface3 = Color(0xFFDCD7D0),
    inset = Color(0xFFF7F6F4),
    border = Color(0xFFD0CBC4),
    borderStrong = Color(0xFFB7B1A8),
    textPrimary = Color(0xFF2A2A2A),
    textMuted = Color(0xFF55524B),
    textSubdued = Color(0xFF777368),
    accent = Color(0xFF8C6C2B),
)

val LocalSkeuoColors = staticCompositionLocalOf { DarkSkeuoColors }

@Composable
fun ProvideSkeuoTheme(
    dark: Boolean,
    content: @Composable () -> Unit,
){
    val colors = if (dark) DarkSkeuoColors else LightSkeuoColors
    CompositionLocalProvider(LocalSkeuoColors provides colors, content = content)
}

/** Default card shape matches admin radius-lg (16px). */
val SkeuoCardShape: Shape = RoundedCornerShape(16.dp)
val SkeuoBevelShape: Shape = androidx.compose.foundation.shape.CutCornerShape(10.dp)

/**
 * Raised skeuomorphic card with subtle glossy shadow and 1dp border.
 * Simulates --shadow-raised-1 and hover translate by animating elevation when pressed.
 */
@Composable
fun SkeuoCard(
    modifier: Modifier = Modifier,
    shape: Shape = SkeuoCardShape,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalSkeuoColors.current
    val interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource()
    val isPressed by interactionSource.collectIsPressedAsStateCompat()
    val elevation by animateFloatAsState(if (isPressed) 18f else 12f, label = "skeuo-elev")

    val clickableMod = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            role = Role.Button,
            onClick = onClick,
        )
    } else Modifier

    // Admin .button gradient look
    val top = if (c.bg.luminance() < 0.5f) Color(0xFF2D2F34) else Color(0xFFEDEBE8)
    val bot = if (c.bg.luminance() < 0.5f) Color(0xFF24272B) else Color(0xFFE3DFDA)
    val gradient = Brush.verticalGradient(listOf(top, bot))

    Box(
        modifier
            .shadow(elevation.dp, shape, clip = false)
            .clip(shape)
            .background(gradient)
            .border(BorderStroke(1.dp, c.border), shape)
            .drawGloss()
            .drawNeonRim(alpha = if (isPressed) 0.28f else 0.12f)
            .then(clickableMod),
        contentAlignment = Alignment.TopStart,
        content = content,
    )
}

/** Inset icon well replicating admin .menu-card__icon look. Place an image or glyph as content. */
@Composable
fun IconWell(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    size: Dp = 63.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalSkeuoColors.current
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(c.inset)
            .border(1.dp, c.border, shape)
            .insetShadow()
    ) {
        content()
    }
}

/** Primary skeuo button used for prominent CTAs like Continue Reading. */
@Composable
fun SkeuoPrimaryButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalSkeuoColors.current
    val shape = RoundedCornerShape(12.dp)
    val top = if (c.bg.luminance() < 0.5f) Color(0xFF2D2F34) else Color(0xFFEDEBE8)
    val bot = if (c.bg.luminance() < 0.5f) Color(0xFF24272B) else Color(0xFFE3DFDA)
    val bg = Brush.verticalGradient(listOf(top, bot))
    val border = if (enabled) c.borderStrong else c.border
    val interactionSource = MutableInteractionSource()
    val isPressed by interactionSource.collectIsPressedAsStateCompat()
    val elev by animateFloatAsState(targetValue = if (isPressed) 10f else 6f, label = "skeuo-btn")

    Box(
        modifier
            .shadow(elev.dp, shape, clip = false)
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick)
            .drawButtonSheen(enabled),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.wrapContentHeight().padding(contentPadding)) {
            content()
        }
    }
}

// --- Private helpers to simulate glossy and inset effects ---

private fun Modifier.drawGloss(): Modifier = drawBehind {
    // Subtle top gloss
    val gloss = Brush.verticalGradient(
        colors = listOf(Color.White.copy(alpha = 0.04f), Color.Transparent),
        startY = 0f,
        endY = size.height * 0.4f,
    )
    drawRect(gloss)
}

private fun Modifier.insetShadow(): Modifier = drawBehind {
    // Inner shadow fake using gradients at top and bottom
    val top = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.06f), Color.Transparent), 0f, size.height * 0.35f
    )
    val bottom = Brush.verticalGradient(
        listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)), size.height * 0.65f, size.height
    )
    drawRect(top)
    drawRect(bottom)
}

private fun Modifier.drawButtonSheen(enabled: Boolean): Modifier = drawBehind {
    if (!enabled) return@drawBehind
    val sheen = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.05f), Color.Transparent), 0f, size.height * 0.6f
    )
    drawRect(sheen)
}

/**
 * Collects pressed state without importing experimental APIs.
 */
@Composable
private fun MutableInteractionSource.collectIsPressedAsStateCompat(): androidx.compose.runtime.State<Boolean> {
    val pressed = remember { mutableStateOf(false) }
    LaunchedEffect(this) {
        interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> pressed.value = true
                is PressInteraction.Release, is PressInteraction.Cancel -> pressed.value = false
            }
        }
    }
    return pressed
}

private fun Modifier.drawNeonRim(alpha: Float): Modifier = drawBehind {
    if (alpha <= 0f) return@drawBehind
    val glow = Color(0xFF00FFFF).copy(alpha = alpha)
    // Four thin gradients from edges to center to simulate outer glow
    val thickness = size.minDimension * 0.02f
    // Top
    drawRect(
        Brush.verticalGradient(listOf(glow, Color.Transparent)),
        size = androidx.compose.ui.geometry.Size(size.width, thickness)
    )
    // Bottom
    withTransform({ translate(0f, size.height - thickness) }) {
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, glow)), size = androidx.compose.ui.geometry.Size(size.width, thickness))
    }
    // Left
    drawRect(
        Brush.horizontalGradient(listOf(glow, Color.Transparent)),
        size = androidx.compose.ui.geometry.Size(thickness, size.height)
    )
    // Right
    withTransform({ translate(size.width - thickness, 0f) }) {
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, glow)), size = androidx.compose.ui.geometry.Size(thickness, size.height))
    }
}
