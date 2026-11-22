package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.AstrophysicsEngine
import com.example.domain.CosmosMode
import com.example.domain.CosmosUiState
import com.example.ui.theme.ChampagneBright
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.EmeraldStatus
import com.example.ui.theme.HairlineGold
import com.example.ui.theme.HairlineLight
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NebulaSlate
import com.example.ui.theme.ObsidianGlass
import com.example.ui.theme.SpaceGroteskFontFamily

@Composable
fun TopCommandBar(
    state: CosmosUiState,
    isWideScreen: Boolean,
    isCompactWidth: Boolean = false,
    onSelectMode: (CosmosMode) -> Unit,
    onToggleAudio8D: () -> Unit,
    onTakeSnapshot: () -> Unit,
    onToggleCinema: () -> Unit,
    modifier: Modifier = Modifier
) {
    val inf = rememberInfiniteTransition(label = "brand_emblem")
    val emblemPulse by inf.animateFloat(
        initialValue = 0.78f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "emblem_scale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Luxury Brand Pill with Live UTC Clock & 8K HDR Chip
            LuxuryGlassPanel(
                shape = RoundedCornerShape(999.dp),
                borderColor = HairlineGold,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .border(1.dp, HairlineGold, CircleShape)
                            .background(Brush.radialGradient(listOf(ChampagneGold.copy(alpha = 0.32f), Color.Transparent))),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .scale(emblemPulse)
                                .clip(CircleShape)
                                .background(ChampagneGold)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.brand_title),
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                letterSpacing = 1.4.sp,
                                color = ChampagneGold
                            )
                            Spacer(Modifier.width(6.dp))
                            val texChip = if (state.texturesLoadedCount < state.texturesTotalCount && state.texturesLoadedCount > 0) {
                                "${state.settings.quality.badge} ${state.texturesLoadedCount}/${state.texturesTotalCount}"
                            } else {
                                "${state.settings.quality.badge} ✓"
                            }
                            Text(
                                text = texChip,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = EmeraldStatus,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(EmeraldStatus.copy(alpha = 0.14f))
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                        Text(
                            text = AstrophysicsEngine.formatUtcClock(state.simEpochMillis),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 9.5.sp,
                            color = NebulaSlate
                        )
                    }
                }
            }

            if (isWideScreen) {
                ModeSelectorBar(
                    currentMode = state.mode,
                    onSelectMode = onSelectMode,
                    modifier = Modifier.weight(1f, fill = false).padding(horizontal = 8.dp)
                )
            }

            // Tools: 8D Audio + Snapshot + Cinema Mode
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .testTag("audio_8d_button")
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (state.audio8DEnabled) Brush.linearGradient(
                                listOf(ChampagneGold.copy(alpha = 0.28f), ChampagneGold.copy(alpha = 0.10f))
                            ) else Brush.linearGradient(listOf(ObsidianGlass, ObsidianGlass))
                        )
                        .border(
                            0.9.dp,
                            if (state.audio8DEnabled) HairlineGold else HairlineLight,
                            RoundedCornerShape(999.dp)
                        )
                        .clickable(onClick = onToggleAudio8D)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (state.audio8DEnabled) Icons.Rounded.VolumeUp else Icons.Rounded.Headphones,
                        contentDescription = stringResource(R.string.action_audio_8d),
                        tint = if (state.audio8DEnabled) ChampagneBright else ChampagneGold,
                        modifier = Modifier.size(18.dp)
                    )
                    if (!isCompactWidth) {
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (state.audio8DEnabled) stringResource(R.string.action_audio_8d_active)
                            else stringResource(R.string.action_audio_8d),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.audio8DEnabled) ChampagneBright else Color.White
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .testTag("snapshot_button")
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ObsidianGlass)
                        .border(0.85.dp, HairlineLight, CircleShape)
                        .clickable(onClick = onTakeSnapshot),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CameraAlt,
                        contentDescription = stringResource(R.string.action_snapshot),
                        tint = ChampagneGold,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .testTag("cinema_button")
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ObsidianGlass)
                        .border(0.85.dp, HairlineLight, CircleShape)
                        .clickable(onClick = onToggleCinema),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Visibility,
                        contentDescription = stringResource(R.string.action_cinema),
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        if (!isWideScreen) {
            ModeSelectorBar(
                currentMode = state.mode,
                onSelectMode = onSelectMode,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ModeSelectorBar(
    currentMode: CosmosMode,
    onSelectMode: (CosmosMode) -> Unit,
    modifier: Modifier = Modifier
) {
    LuxuryGlassPanel(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        borderColor = HairlineLight,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CosmosMode.entries.forEach { mode ->
                val isBh = mode == CosmosMode.BLACK_HOLE
                LuxuryPillButton(
                    text = mode.labelAr,
                    selected = currentMode == mode,
                    onClick = { onSelectMode(mode) },
                    tone = if (isBh) PillTone.AMBER else PillTone.GOLD,
                    minHeight = 36.dp,
                    tag = "mode_${mode.name.lowercase()}"
                )
            }
        }
    }
}
