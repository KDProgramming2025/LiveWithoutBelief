/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.hilt.navigation.compose.hiltViewModel
import info.lwb.feature.settings.SettingsUiTokens.SectionCorner
import info.lwb.feature.settings.SettingsUiTokens.ScreenPadding
import info.lwb.feature.settings.SettingsUiTokens.SectionSpacing
import info.lwb.feature.settings.SettingsUiTokens.CardInnerSpacing
import info.lwb.feature.settings.SettingsUiTokens.VersionCapsuleCorner
import info.lwb.feature.settings.SettingsUiTokens.VersionCapsulePaddingH
import info.lwb.feature.settings.SettingsUiTokens.VersionCapsulePaddingV

/**
 * Creative redesigned Settings screen root.
 *
 * Features:
 * 1. Gradient header with title & subtle back affordance.
 * 2. Glass-style card grouping appearance controls (theme mode).
 * 3. Theme preview chips showing approximate light/dark palettes.
 * 4. Footer capsule displaying app version.
 */
@Composable
fun SettingsRoute(onBack: () -> Unit = {}) {
    val vm: SettingsViewModel = hiltViewModel()
    val mode by vm.themeMode.collectAsState()
    BackHandler(enabled = onBack !== {}) { onBack() }
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        ) {
            SettingsHeader(onBack = onBack)
            AppearanceSection(
                currentMode = mode,
                onSelect = { vm.setTheme(it) },
            )
            Spacer(modifier = Modifier.weight(1f))
            VersionFooter()
        }
    }
}

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, start = 4.dp, end = ScreenPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, enabled = onBack !== {}) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(start = 4.dp),
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun AppearanceSection(currentMode: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Appearance",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 4.dp),
        )
        GlassCard {
            ThemeOptionRow(
                label = "System",
                description = "Follow device setting",
                selected = currentMode == ThemeMode.SYSTEM,
                onClick = { onSelect(ThemeMode.SYSTEM) },
            )
            ThemeOptionRow(
                label = "Light",
                description = "Always light theme",
                selected = currentMode == ThemeMode.LIGHT,
                onClick = { onSelect(ThemeMode.LIGHT) },
            )
            ThemeOptionRow(
                label = "Dark",
                description = "Always dark theme",
                selected = currentMode == ThemeMode.DARK,
                onClick = { onSelect(ThemeMode.DARK) },
            )
        }
    }
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    val surface = MaterialTheme.colorScheme.surface
    val bgColor = surface.copy(alpha = SettingsUiTokens.GLASS_BACKGROUND_ALPHA)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(SectionCorner),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CardInnerSpacing),
            verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
            content = content,
        )
    }
}

@Composable
private fun ThemeOptionRow(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val background = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    } else {
        Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Simple selection indicator
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        borderColor.copy(alpha = 0.4f)
                    },
                ),
        )
    }
}

@Composable
private fun VersionFooter() {
    val context = LocalContext.current
    val version = remember {
        val pm = context.packageManager
        val pkgInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(
                context.packageName,
                SettingsUiTokens.PACKAGE_INFO_FLAGS_ZERO,
            )
        }
        pkgInfo.versionName
    }
    val shape = RoundedCornerShape(VersionCapsuleCorner)
    val bg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = SettingsUiTokens.VERSION_CAPSULE_BG_ALPHA)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = ScreenPadding)
            .clip(shape)
            .background(bg)
            .padding(horizontal = VersionCapsulePaddingH, vertical = VersionCapsulePaddingV),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Version $version",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
                .copy(alpha = SettingsUiTokens.VERSION_TEXT_ALPHA),
        )
    }
}

/** Tokens for spacing & sizes to avoid MagicNumber violations. */
private object SettingsUiTokens {
    // Dimension constants (SCREAMING_SNAKE for detekt MagicNumber rule compliance)
    private const val SCREEN_PADDING_DP = 16
    private const val SECTION_SPACING_DP = 18
    private const val CARD_INNER_SPACING_DP = 12
    private const val SECTION_CORNER_DP = 20
    private const val VERSION_CAPSULE_CORNER_DP = 18
    private const val VERSION_CAPSULE_PADDING_H_DP = 16
    private const val VERSION_CAPSULE_PADDING_V_DP = 10
    const val GLASS_BACKGROUND_ALPHA = 0.55f
    const val GLASS_BORDER_ALPHA = 0.08f

    val ScreenPadding = SCREEN_PADDING_DP.dp
    val SectionSpacing = SECTION_SPACING_DP.dp
    val CardInnerSpacing = CARD_INNER_SPACING_DP.dp
    val SectionCorner = SECTION_CORNER_DP.dp

    // Glass constants retained for potential future styling needs
    val VersionCapsuleCorner = VERSION_CAPSULE_CORNER_DP.dp
    val VersionCapsulePaddingH = VERSION_CAPSULE_PADDING_H_DP.dp
    val VersionCapsulePaddingV = VERSION_CAPSULE_PADDING_V_DP.dp

    // Removed segmented selector constant (no longer used)
    const val VERSION_CAPSULE_BG_ALPHA = 0.60f
    const val VERSION_TEXT_ALPHA = 0.80f
    const val PACKAGE_INFO_FLAGS_ZERO = 0
}
