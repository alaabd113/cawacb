package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.BlackHolePreset
import com.example.domain.CosmosMode
import com.example.domain.CosmosUiState
import com.example.domain.ProjectedLabel
import com.example.ui.theme.AccretionAmber
import com.example.ui.theme.AccretionGlow
import com.example.ui.theme.AstralCyan
import com.example.ui.theme.AstralCyanSoft
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.HairlineAmber
import com.example.ui.theme.HairlineCyan
import com.example.ui.theme.HairlineLight
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NebulaSlate
import com.example.ui.theme.StarlightInk
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ProjectedLabelsOverlay(
    labels: List<ProjectedLabel>,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!visible || labels.isEmpty()) return
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val maxPxW = with(density) { maxWidth.toPx() }
        val maxPxH = with(density) { maxHeight.toPx() }

        labels.forEach { lbl ->
            val pxX = (lbl.xNorm * maxPxW).roundToInt()
            val pxY = (lbl.yNorm * maxPxH).roundToInt()
            Box(
                modifier = Modifier
                    .offset { IntOffset((pxX - 54).coerceIn(8, (maxPxW - 140).toInt().coerceAtLeast(8)), pxY.coerceIn(24, (maxPxH - 60).toInt().coerceAtLeast(24))) }
                    .then(
                        if (!lbl.isSubtle) {
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (lbl.isBlackHoleTag) Color(0xBF1A0C05) else Color(0x9E050914))
                                .border(
                                    0.7.dp,
                                    if (lbl.isBlackHoleTag) HairlineAmber else Color(0x28FFFFFF),
                                    RoundedCornerShape(999.dp)
                                )
                                .padding(horizontal = 9.dp, vertical = 3.dp)
                        } else {
                            Modifier
                        }
                    )
            ) {
                Text(
                    text = lbl.text,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = if (lbl.isSubtle) 10.5.sp else 11.sp),
                    color = lbl.color
                )
            }
        }
    }
}

@Composable
fun TelemetryInfoCard(
    state: CosmosUiState,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chevronAngle by animateFloatAsState(
        targetValue = if (state.infoExpanded) 180f else 0f,
        label = "info_chev"
    )
    val isBh = state.mode == CosmosMode.BLACK_HOLE
    val isSky = state.mode == CosmosMode.EARTH_SKY
    val isGalaxy = state.mode == CosmosMode.GALAXY

    val title = when {
        isBh -> state.blackHole.preset.titleAr
        isSky -> "سماء ${state.selectedCity.nameAr}"
        isGalaxy -> "مجرة درب التبانة"
        else -> state.bodyTelemetry.titleAr
    }
    val subtitle = when {
        isBh -> state.blackHole.preset.titleEn
        isSky -> String.format(Locale.US, "%.2f°N · %.2f°E", state.selectedCity.lat, state.selectedCity.lon)
        isGalaxy -> "Milky Way · 200+ مليار نجم"
        else -> state.bodyTelemetry.subtitle
    }

    LuxuryGlassPanel(
        modifier = modifier.widthIn(min = 210.dp, max = 280.dp),
        shape = RoundedCornerShape(18.dp),
        borderColor = if (isBh) HairlineAmber else HairlineLight,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("info_card_header")
                    .clickable(onClick = onToggleExpand),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isBh) AccretionGlow else ChampagneGold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = NebulaSlate
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(0x14FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = ChampagneGold,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(chevronAngle)
                    )
                }
            }

            AnimatedVisibility(
                visible = state.infoExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    HorizontalDivider(color = HairlineLight, thickness = 0.6.dp)
                    Spacer(Modifier.height(2.dp))
                    if (isBh) {
                        TelemetryRow("الكتلة", state.blackHole.preset.massAr, AccretionGlow)
                        TelemetryRow("البعد عنا", state.blackHole.preset.distanceAr, AstralCyanSoft)
                        TelemetryRow("نصف قطر شوارتزشيلد", state.blackHole.preset.schwarzschildAr, ChampagneGold)
                        TelemetryRow("أفق الحدث (Kerr)", String.format(Locale.US, "%.3f r_s", state.bhTelemetry.eventHorizonRs), AccretionAmber)
                        TelemetryRow("أقرب مدار مستقر ISCO", String.format(Locale.US, "%.3f r_s", state.bhTelemetry.iscoRs), ChampagneGold)
                    } else if (isGalaxy) {
                        TelemetryRow("القطر المجري", "105,700 سنة ضوئية", AstralCyanSoft)
                        TelemetryRow("بعد الشمس عن المركز", "26,670 سنة ضوئية", ChampagneGold)
                        TelemetryRow("سرعة الدوران", "220 كم/ث", AstralCyan)
                        TelemetryRow("السنة المجرية", "230 مليون سنة", ChampagneGold)
                    } else {
                        TelemetryRow("البعد عن الشمس", state.bodyTelemetry.distanceFromSunKm, AstralCyanSoft)
                        TelemetryRow("البعد عن الأرض", state.bodyTelemetry.distanceFromEarthKm, StarlightInk)
                        TelemetryRow("السرعة المدارية", state.bodyTelemetry.orbitalSpeedKms, AstralCyan)
                        TelemetryRow("زمن وصول الضوء", state.bodyTelemetry.lightTimeFromSun, ChampagneGold)
                        TelemetryRow("حرارة السطح", state.bodyTelemetry.surfaceTemp, AccretionGlow)
                        TelemetryRow("الجاذبية", state.bodyTelemetry.gravity, StarlightInk)
                        TelemetryRow("القدر والإضاءة", state.bodyTelemetry.apparentMag, AstralCyanSoft)
                        TelemetryRow("النسبية الخاصة", state.bodyTelemetry.extraRelativistic, ChampagneGold)
                    }
                }
            }
        }
    }
}

@Composable
fun HelicalVortexHudCard(
    state: CosmosUiState,
    modifier: Modifier = Modifier
) {
    val ht = state.helicalTelemetry
    LuxuryGlassPanel(
        modifier = modifier
            .widthIn(min = 225.dp, max = 285.dp)
            .testTag("helical_vortex_hud"),
        shape = RoundedCornerShape(18.dp),
        borderColor = HairlineCyan,
        contentPadding = PaddingValues(11.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "الدوّامة الحلزونية الشمسية",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = ChampagneGold
                )
                Text(
                    text = "230 كم/ث",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AstralCyan
                )
            }
            TelemetryRow("السرعة المجرية", ht.galacticSpeedKmh, AstralCyanSoft)
            TelemetryRow("مسار الشمس", ht.apexTargetAr, ChampagneGold)
            TelemetryRow(
                "المسافة المقطوعة",
                com.example.domain.AstrophysicsEngine.formatKmAr(ht.distanceTraveledKm),
                AccretionGlow
            )
            TelemetryRow("السنة المجرية", ht.galacticYearAr, StarlightInk)
        }
    }
}

@Composable
fun EarthSkyHudCard(
    state: CosmosUiState,
    onToggleGyro: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sky = state.skyTelemetry
    val headingDeg = ((sky.cameraAzimuthDeg % 360f) + 360f) % 360f
    val cardinalAr = when {
        headingDeg < 22.5f || headingDeg >= 337.5f -> "شمال N"
        headingDeg < 67.5f -> "شمال شرق NE"
        headingDeg < 112.5f -> "شرق E"
        headingDeg < 157.5f -> "جنوب شرق SE"
        headingDeg < 202.5f -> "جنوب S"
        headingDeg < 247.5f -> "جنوب غرب SW"
        headingDeg < 292.5f -> "غرب W"
        else -> "شمال غرب NW"
    }

    LuxuryGlassPanel(
        modifier = modifier
            .widthIn(min = 225.dp, max = 282.dp)
            .testTag("earth_sky_hud"),
        shape = RoundedCornerShape(18.dp),
        borderColor = HairlineCyan,
        contentPadding = PaddingValues(11.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Live Golden Azimuth Compass Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x16EBD091))
                    .border(0.75.dp, Color(0x44EBD091), RoundedCornerShape(10.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "بوصلة ${state.selectedCity.nameAr}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = ChampagneGold
                )
                Text(
                    text = "$cardinalAr · ${headingDeg.toInt()}°",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AstralCyanSoft
                )
            }

            // Real Moon Phase & Illumination % from Astronomy Engine
            TelemetryRow(
                "طور القمر",
                "${sky.moonPhaseNameAr} · ${sky.moonIlluminationPct}%",
                ChampagneGold
            )
            TelemetryRow(
                "الشروق · الغروب",
                "${sky.nextSunriseLocal}  |  ${sky.nextSunsetLocal}",
                AccretionGlow
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Cloud, contentDescription = null, tint = AstralCyan, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("الغيوم الحية", style = MaterialTheme.typography.bodySmall, color = NebulaSlate)
                }
                Text(
                    text = "${state.weather.cloudCoverPercent}% · ${state.weather.descriptionAr}",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                    color = AstralCyanSoft
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gyro_toggle_button")
                    .defaultMinSize(minHeight = 40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (state.gyroscopeEnabled) AstralCyan.copy(alpha = 0.24f) else AstralCyan.copy(alpha = 0.12f)
                    )
                    .border(0.85.dp, HairlineCyan, RoundedCornerShape(12.dp))
                    .clickable(onClick = onToggleGyro)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.ScreenRotation, contentDescription = null, tint = AstralCyanSoft, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (state.gyroscopeEnabled) stringResource(R.string.action_gyro_active)
                    else stringResource(R.string.action_gyro_enable),
                    style = MaterialTheme.typography.labelMedium,
                    color = AstralCyanSoft
                )
            }
        }
    }
}

@Composable
fun BlackHoleLabHudCard(
    state: CosmosUiState,
    onSelectPreset: (BlackHolePreset) -> Unit,
    onToggleDive: () -> Unit,
    onOpenPhysicsSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    LuxuryGlassPanel(
        modifier = modifier.widthIn(min = 232.dp, max = 282.dp),
        shape = RoundedCornerShape(18.dp),
        borderColor = HairlineAmber,
        contentPadding = PaddingValues(11.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "مختبر الثقب الأسود",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = AccretionGlow
                )
                Text(
                    text = state.blackHole.preset.badge,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AstralCyanSoft
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BlackHolePreset.entries.forEach { preset ->
                    LuxuryPillButton(
                        text = preset.shortAr,
                        selected = state.blackHole.preset == preset,
                        onClick = { onSelectPreset(preset) },
                        tone = PillTone.AMBER,
                        minHeight = 32.dp,
                        modifier = Modifier.weight(1f),
                        tag = "bh_preset_${preset.name.lowercase()}"
                    )
                }
            }

            TelemetryRow("البعد النسبي", String.format(Locale.US, "%.2f r_s", state.bhTelemetry.distanceRs), AstralCyanSoft)
            TelemetryRow("سريان الزمن", String.format(Locale.US, "%.1f%%", state.bhTelemetry.timeDilationPercent), ChampagneGold)
            TelemetryRow("سرعة الإفلات", String.format(Locale.US, "%.3f c", state.bhTelemetry.escapeVelocityC), AccretionAmber)

            // Live Dual Relativistic Clocks (Spacecraft Proper Time vs Distant Earth Coordinate Time)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x18FF9442))
                    .border(0.7.dp, HairlineAmber, RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("ساعة المركبة", style = MaterialTheme.typography.labelSmall, color = NebulaSlate)
                    Text(
                        text = com.example.domain.AstrophysicsEngine.formatMissionClock(state.bhTelemetry.shipClockSec),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccretionGlow
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("ساعة الأرض", style = MaterialTheme.typography.labelSmall, color = NebulaSlate)
                    Text(
                        text = com.example.domain.AstrophysicsEngine.formatMissionClock(state.bhTelemetry.earthClockSec),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AstralCyanSoft
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("bh_dive_button")
                        .defaultMinSize(minHeight = 40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (state.blackHole.divingToHorizon) AccretionAmber.copy(alpha = 0.34f)
                            else AccretionAmber.copy(alpha = 0.16f)
                        )
                        .border(0.9.dp, HairlineAmber, RoundedCornerShape(12.dp))
                        .clickable(onClick = onToggleDive)
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.RocketLaunch, contentDescription = null, tint = AccretionGlow, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (state.blackHole.divingToHorizon) stringResource(R.string.action_abort_dive)
                        else stringResource(R.string.action_dive_horizon),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                        color = Color.White
                    )
                }

                Row(
                    modifier = Modifier
                        .testTag("bh_physics_button")
                        .defaultMinSize(minHeight = 40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x14FFFFFF))
                        .border(0.8.dp, HairlineLight, RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenPhysicsSheet)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = stringResource(R.string.action_bh_physics), tint = ChampagneGold, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.action_bh_physics), style = MaterialTheme.typography.labelMedium, color = StarlightInk)
                }
            }
        }
    }
}

@Composable
private fun TelemetryRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = NebulaSlate)
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.SemiBold
            ),
            color = valueColor
        )
    }
}
