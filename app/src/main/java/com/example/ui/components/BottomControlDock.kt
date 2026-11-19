package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.ActiveDrawer
import com.example.domain.CelestialBodyId
import com.example.domain.CosmosMode
import com.example.domain.CosmosUiState
import com.example.domain.SolarCatalog
import com.example.domain.TimeSpeeds
import com.example.ui.theme.AccretionAmber
import com.example.ui.theme.AstralCyan
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.HairlineCyan
import com.example.ui.theme.HairlineGold
import com.example.ui.theme.HairlineLight
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NebulaSlate
import com.example.ui.theme.StarlightInk

@Composable
fun BottomControlDock(
    state: CosmosUiState,
    onSelectBody: (CelestialBodyId) -> Unit,
    onSelectBlackHole: () -> Unit,
    onTogglePlay: () -> Unit,
    onToggleReverse: () -> Unit,
    onResetNow: () -> Unit,
    onSelectSpeed: (Int) -> Unit,
    onOpenStudio: () -> Unit,
    onToggleSpacetime: () -> Unit,
    onOpenPlaces: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        // Planetary & Singularity Quick Selector Rail
        LuxuryGlassPanel(
            modifier = Modifier.widthIn(max = 880.dp).fillMaxWidth(),
            shape = RoundedCornerShape(999.dp),
            borderColor = HairlineLight,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 5.dp)
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SolarCatalog.planets.forEach { spec ->
                    val isSelected = state.mode != CosmosMode.BLACK_HOLE &&
                        state.mode != CosmosMode.GALAXY &&
                        state.selectedBody == spec.id
                    LuxuryPillButton(
                        text = spec.nameAr,
                        selected = isSelected,
                        onClick = { onSelectBody(spec.id) },
                        tone = PillTone.CYAN,
                        leadingDotColor = spec.accentColor,
                        minHeight = 36.dp,
                        tag = "body_${spec.id.name.lowercase()}"
                    )
                }
                LuxuryPillButton(
                    text = stringResource(R.string.mode_blackhole),
                    selected = state.mode == CosmosMode.BLACK_HOLE,
                    onClick = onSelectBlackHole,
                    tone = PillTone.AMBER,
                    leadingDotColor = AccretionAmber,
                    isBlackHoleDot = true,
                    minHeight = 36.dp,
                    tag = "body_blackhole"
                )
            }
        }

        // Main Luxury Action & Time Simulation Dock
        LuxuryGlassPanel(
            modifier = Modifier.widthIn(max = 880.dp).fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            borderColor = HairlineLight,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Row 1: Studio, Spacetime, Destinations, Settings
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DockActionButton(
                        icon = Icons.Rounded.AutoAwesome,
                        label = stringResource(R.string.action_studio),
                        active = state.activeDrawer == ActiveDrawer.STUDIO,
                        accent = ChampagneGold,
                        onClick = onOpenStudio,
                        tag = "open_studio_button"
                    )
                    DockActionButton(
                        icon = Icons.Rounded.GridOn,
                        label = stringResource(R.string.action_spacetime),
                        active = state.settings.spacetimeGrid,
                        accent = AstralCyan,
                        onClick = onToggleSpacetime,
                        tag = "toggle_spacetime_button"
                    )
                    DockActionButton(
                        icon = Icons.Rounded.Place,
                        label = stringResource(R.string.action_places),
                        active = state.activeDrawer == ActiveDrawer.PLACES,
                        accent = ChampagneGold,
                        onClick = onOpenPlaces,
                        tag = "open_places_button"
                    )
                    DockActionButton(
                        icon = Icons.Rounded.Settings,
                        label = stringResource(R.string.action_settings),
                        active = state.activeDrawer == ActiveDrawer.SETTINGS,
                        accent = AstralCyan,
                        onClick = onOpenSettings,
                        tag = "open_settings_button"
                    )
                }

                // Row 2: Time Transport + Speed Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactIconChip(
                        icon = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (state.isPlaying) R.string.action_pause else R.string.action_play),
                        active = state.isPlaying,
                        onClick = onTogglePlay,
                        tag = "play_pause_button"
                    )
                    CompactIconChip(
                        icon = Icons.Rounded.FastRewind,
                        contentDescription = stringResource(R.string.action_reverse),
                        active = state.isReversed,
                        onClick = onToggleReverse,
                        tag = "reverse_time_button"
                    )
                    LuxuryPillButton(
                        text = stringResource(R.string.action_now),
                        selected = false,
                        onClick = onResetNow,
                        tone = PillTone.NEUTRAL,
                        minHeight = 36.dp,
                        tag = "reset_now_button"
                    )
                    Spacer(Modifier.width(2.dp))
                    TimeSpeeds.options.forEachIndexed { idx, opt ->
                        val active = state.speedIndex == idx
                        Box(
                            modifier = Modifier
                                .testTag("speed_${opt.id}")
                                .defaultMinSize(minHeight = 36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) AstralCyan.copy(alpha = 0.22f) else Color(0x0DFFFFFF))
                                .border(
                                    0.8.dp,
                                    if (active) HairlineCyan else Color(0x14FFFFFF),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onSelectSpeed(idx) }
                                .padding(horizontal = 9.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = opt.label,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.5.sp,
                                color = if (active) Color.White else NebulaSlate
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DockActionButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .testTag(tag)
            .defaultMinSize(minHeight = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (active) accent.copy(alpha = 0.24f) else Color(0x10FFFFFF)
            )
            .border(
                0.85.dp,
                if (active) accent.copy(alpha = 0.72f) else HairlineLight,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) Color.White else accent,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) Color.White else StarlightInk
        )
    }
}

@Composable
private fun CompactIconChip(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Box(
        modifier = Modifier
            .testTag(tag)
            .size(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (active) AstralCyan.copy(alpha = 0.24f) else Color(0x10FFFFFF))
            .border(0.85.dp, if (active) HairlineCyan else HairlineLight, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) Color.White else ChampagneGold,
            modifier = Modifier.size(19.dp)
        )
    }
}
