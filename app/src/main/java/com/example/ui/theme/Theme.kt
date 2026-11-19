package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CosmosDarkColorScheme = darkColorScheme(
    primary = ChampagneGold,
    onPrimary = ObsidianVoid,
    primaryContainer = ObsidianElevated,
    onPrimaryContainer = ChampagneBright,
    secondary = AstralCyan,
    onSecondary = ObsidianVoid,
    secondaryContainer = ObsidianElevated,
    onSecondaryContainer = AstralCyanSoft,
    tertiary = AccretionAmber,
    onTertiary = ObsidianVoid,
    background = ObsidianVoid,
    onBackground = StarlightInk,
    surface = ObsidianDeep,
    onSurface = StarlightInk,
    surfaceVariant = ObsidianElevated,
    onSurfaceVariant = NebulaSlate,
    outline = HairlineLight,
    outlineVariant = HairlineGold
)

@Composable
fun CosmosTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CosmosDarkColorScheme,
        typography = CosmosTypography,
        content = content
    )
}
