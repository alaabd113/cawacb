package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlightTakeoff
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.ActiveDrawer
import com.example.domain.AstrophysicsEngine
import com.example.domain.BlackHoleConfig
import com.example.domain.CityCatalog
import com.example.domain.CityLocation
import com.example.domain.CosmosUiState
import com.example.domain.EngineSettings
import com.example.domain.RenderQuality
import com.example.domain.StudioConfig
import com.example.domain.StudioPreset
import com.example.ui.theme.AccretionAmber
import com.example.ui.theme.AccretionGlow
import com.example.ui.theme.AstralCyan
import com.example.ui.theme.AstralCyanSoft
import com.example.ui.theme.ChampagneBright
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.HairlineAmber
import com.example.ui.theme.HairlineGold
import com.example.ui.theme.HairlineLight
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StarlightInk
import java.util.Locale

@Composable
fun LuxuryDrawerHost(
    state: CosmosUiState,
    onClose: () -> Unit,
    onApplyStudioPreset: (StudioPreset) -> Unit,
    onUpdateStudio: ((StudioConfig) -> StudioConfig) -> Unit,
    onSelectCity: (CityLocation) -> Unit,
    onFlyToCity: () -> Unit,
    onViewCitySky: () -> Unit,
    onUpdateBlackHole: ((BlackHoleConfig) -> BlackHoleConfig) -> Unit,
    onUpdateSettings: ((EngineSettings) -> EngineSettings) -> Unit,
    onSelectQuality: (RenderQuality) -> Unit,
    modifier: Modifier = Modifier
) {
    val visible = state.activeDrawer != ActiveDrawer.NONE && !state.cinemaMode
    BackHandler(enabled = visible) { onClose() }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it / 2 } + fadeIn(),
        exit = slideOutVertically { it / 2 } + fadeOut(),
        modifier = modifier
    ) {
        val isBh = state.activeDrawer == ActiveDrawer.BLACK_HOLE
        LuxuryGlassPanel(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .heightIn(max = 520.dp),
            shape = RoundedCornerShape(24.dp),
            borderColor = if (isBh) HairlineAmber else HairlineGold,
            sheetTone = true,
            contentPadding = PaddingValues(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                val title = when (state.activeDrawer) {
                    ActiveDrawer.STUDIO -> stringResource(R.string.sheet_studio_title)
                    ActiveDrawer.PLACES -> stringResource(R.string.sheet_places_title)
                    ActiveDrawer.BLACK_HOLE -> stringResource(R.string.sheet_bh_title)
                    ActiveDrawer.SETTINGS -> stringResource(R.string.sheet_settings_title)
                    ActiveDrawer.NONE -> ""
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isBh) AccretionGlow else ChampagneGold
                    )
                    Box(
                        modifier = Modifier
                            .testTag("close_drawer_button")
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0x16FFFFFF))
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = StarlightInk,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                HorizontalDivider(color = HairlineLight, thickness = 0.7.dp)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 6.dp)
                ) {
                    when (state.activeDrawer) {
                        ActiveDrawer.STUDIO -> StudioSheetContent(
                            studio = state.studio,
                            onApplyPreset = onApplyStudioPreset,
                            onUpdate = onUpdateStudio
                        )
                        ActiveDrawer.PLACES -> PlacesSheetContent(
                            state = state,
                            onSelectCity = onSelectCity,
                            onFlyToCity = onFlyToCity,
                            onViewCitySky = onViewCitySky
                        )
                        ActiveDrawer.BLACK_HOLE -> BlackHoleSheetContent(
                            bh = state.blackHole,
                            onUpdate = onUpdateBlackHole
                        )
                        ActiveDrawer.SETTINGS -> SettingsSheetContent(
                            settings = state.settings,
                            onUpdate = onUpdateSettings,
                            onSelectQuality = onSelectQuality
                        )
                        ActiveDrawer.NONE -> Unit
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudioSheetContent(
    studio: StudioConfig,
    onApplyPreset: (StudioPreset) -> Unit,
    onUpdate: ((StudioConfig) -> StudioConfig) -> Unit
) {
    LuxurySectionHeader("أنماط فاخرة جاهزة")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StudioPreset.entries.forEach { preset ->
            LuxuryPillButton(
                text = preset.labelAr,
                selected = studio.preset == preset,
                onClick = { onApplyPreset(preset) },
                tone = PillTone.GOLD,
                tag = "studio_preset_${preset.name.lowercase()}"
            )
        }
    }

    LuxurySectionHeader("التحكم بشكل وهندسة الكوكب")
    LuxurySliderRow(
        title = "حجم الكواكب البصري",
        subtitle = "تكبير وتصغير مجسمات الكواكب",
        value = studio.planetScale,
        valueRange = 0.5f..2.2f,
        valueLabel = String.format(Locale.US, "%.2f×", studio.planetScale),
        onValueChange = { v -> onUpdate { it.copy(planetScale = v) } }
    )
    LuxurySliderRow(
        title = "التفلطح القطبي (الشكل)",
        subtitle = "تشكيل تفلطح القطبين وتمدد الاستواء",
        value = studio.oblatenessOverride,
        valueRange = -0.12f..0.32f,
        valueLabel = if (kotlin.math.abs(studio.oblatenessOverride) < 0.01f) "واقعي"
        else String.format(Locale.US, "%+.2f", studio.oblatenessOverride),
        onValueChange = { v -> onUpdate { it.copy(oblatenessOverride = v) } }
    )
    LuxurySliderRow(
        title = "سرعة الدوران البصري",
        subtitle = "تدوير تفاعلي لرؤية كل الزوايا",
        value = studio.visualSpin,
        valueRange = 0f..1.2f,
        valueLabel = String.format(Locale.US, "%.2f", studio.visualSpin),
        onValueChange = { v -> onUpdate { it.copy(visualSpin = v) } }
    )

    LuxurySectionHeader("التفاصيل المجهرية والغلاف الجوي 8K")
    LuxurySliderRow(
        title = "عمق التضاريس والفوهات 8K",
        subtitle = "تفاصيل فائقة الحدة عند الاقتراب",
        value = studio.reliefDepth,
        valueRange = 0f..2.5f,
        valueLabel = String.format(Locale.US, "%.2f", studio.reliefDepth),
        onValueChange = { v -> onUpdate { it.copy(reliefDepth = v) } }
    )
    LuxurySliderRow(
        title = "كثافة الغلاف الجوي",
        subtitle = "تشتت رايلي ومي حول الكواكب",
        value = studio.atmosphereDensity,
        valueRange = 0f..2.4f,
        valueLabel = String.format(Locale.US, "%.2f", studio.atmosphereDensity),
        onValueChange = { v -> onUpdate { it.copy(atmosphereDensity = v) } }
    )
    LuxurySliderRow(
        title = "كثافة الغيوم الكوكبية",
        subtitle = "السحب المتحركة فوق الأرض",
        value = studio.cloudDensity,
        valueRange = 0f..2.0f,
        valueLabel = String.format(Locale.US, "%.2f", studio.cloudDensity),
        onValueChange = { v -> onUpdate { it.copy(cloudDensity = v) } }
    )
    LuxurySliderRow(
        title = "سطوع أضواء المدن الليلية",
        subtitle = "بريق المدن على الجانب المظلم",
        value = studio.nightLights,
        valueRange = 0f..2.5f,
        valueLabel = String.format(Locale.US, "%.2f", studio.nightLights),
        onValueChange = { v -> onUpdate { it.copy(nightLights = v) } }
    )
    LuxurySliderRow(
        title = "حجم ووضوح الحلقات",
        subtitle = "حلقات زحل وأورانوس",
        value = studio.ringIntensity,
        valueRange = 0f..1.8f,
        valueLabel = String.format(Locale.US, "%.2f", studio.ringIntensity),
        onValueChange = { v -> onUpdate { it.copy(ringIntensity = v) } }
    )
    LuxurySliderRow(
        title = "شدة إضاءة الشمس",
        subtitle = "تباين النهار والظلال",
        value = studio.sunIntensity,
        valueRange = 0.55f..1.9f,
        valueLabel = String.format(Locale.US, "%.2f", studio.sunIntensity),
        onValueChange = { v -> onUpdate { it.copy(sunIntensity = v) } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlacesSheetContent(
    state: CosmosUiState,
    onSelectCity: (CityLocation) -> Unit,
    onFlyToCity: () -> Unit,
    onViewCitySky: () -> Unit
) {
    LuxurySectionHeader("اختر المدينة للرصد الفلكي")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CityCatalog.cities.forEach { city ->
            LuxuryPillButton(
                text = city.nameAr,
                selected = state.selectedCity.id == city.id,
                onClick = { onSelectCity(city) },
                tone = PillTone.CYAN,
                tag = "city_${city.id}"
            )
        }
    }

    LuxurySectionHeader("الملاحة والرصد")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .testTag("fly_to_city_button")
                .defaultMinSize(minHeight = 46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(ChampagneGold.copy(alpha = 0.22f))
                .border(0.9.dp, HairlineGold, RoundedCornerShape(14.dp))
                .clickable(onClick = onFlyToCity)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.FlightTakeoff, contentDescription = null, tint = ChampagneBright, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.action_fly_location), style = MaterialTheme.typography.labelLarge, color = ChampagneBright)
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .testTag("view_city_sky_button")
                .defaultMinSize(minHeight = 46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(AstralCyan.copy(alpha = 0.18f))
                .border(0.9.dp, AstralCyan.copy(alpha = 0.58f), RoundedCornerShape(14.dp))
                .clickable(onClick = onViewCitySky)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.TravelExplore, contentDescription = null, tint = AstralCyanSoft, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.action_view_sky), style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
    }

    val city = state.selectedCity
    val localTime = AstrophysicsEngine.formatLocalTime(state.simEpochMillis, city.tzId)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x4D000000))
            .border(0.6.dp, HairlineLight, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(
            text = String.format(
                Locale.US,
                "%s  ·  %.4f°N, %.4f°E  ·  %s (%s)",
                city.nameAr, city.lat, city.lon, localTime, city.tzId
            ),
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 11.sp,
            color = AstralCyanSoft
        )
    }
}

@Composable
private fun BlackHoleSheetContent(
    bh: BlackHoleConfig,
    onUpdate: ((BlackHoleConfig) -> BlackHoleConfig) -> Unit
) {
    LuxurySliderRow(
        title = "الدوران المغزلي (Spin a*)",
        subtitle = "يحدد نصف قطر أفق الحدث وسحب الإطار الزمكاني",
        value = bh.spinA,
        valueRange = 0f..0.99f,
        valueLabel = String.format(Locale.US, "%.2f", bh.spinA),
        accentColor = AccretionAmber,
        onValueChange = { v -> onUpdate { it.copy(spinA = v) } }
    )
    LuxurySliderRow(
        title = "حرارة وإشعاع قرص التراكم",
        value = bh.accretionTemp,
        valueRange = 0f..2.4f,
        valueLabel = String.format(Locale.US, "%.2f", bh.accretionTemp),
        accentColor = AccretionAmber,
        onValueChange = { v -> onUpdate { it.copy(accretionTemp = v) } }
    )
    LuxurySliderRow(
        title = "عدسة الجاذبية وانحناء الضوء",
        value = bh.lensingStrength,
        valueRange = 0f..1.6f,
        valueLabel = String.format(Locale.US, "%.2f", bh.lensingStrength),
        accentColor = ChampagneGold,
        onValueChange = { v -> onUpdate { it.copy(lensingStrength = v) } }
    )
    LuxuryToggleRow(
        title = "انزياح دوبلر النسبوي (Doppler Beaming)",
        checked = bh.dopplerShift,
        accentColor = AccretionAmber,
        onCheckedChange = { c -> onUpdate { it.copy(dopplerShift = c) } }
    )
    LuxuryToggleRow(
        title = "النفثات البلازمية القطبية (Relativistic Jets)",
        checked = bh.polarJets,
        accentColor = AstralCyan,
        onCheckedChange = { c -> onUpdate { it.copy(polarJets = c) } }
    )
    LuxuryToggleRow(
        title = "مدار النجم S2 النسبوي المتقدم",
        checked = bh.showS2Star,
        accentColor = AstralCyan,
        onCheckedChange = { c -> onUpdate { it.copy(showS2Star = c) } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsSheetContent(
    settings: EngineSettings,
    onUpdate: ((EngineSettings) -> EngineSettings) -> Unit,
    onSelectQuality: (RenderQuality) -> Unit
) {
    LuxurySectionHeader("دقة التصيير والجودة الفائقة")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RenderQuality.entries.forEach { q ->
            LuxuryPillButton(
                text = q.labelAr,
                selected = settings.quality == q,
                onClick = { onSelectQuality(q) },
                tone = PillTone.GOLD,
                tag = "quality_${q.name.lowercase()}"
            )
        }
    }

    LuxurySliderRow(
        title = "التعريض السينمائي (HDR Exposure)",
        value = settings.exposure,
        valueRange = 0.55f..1.95f,
        valueLabel = String.format(Locale.US, "%.2f", settings.exposure),
        onValueChange = { v -> onUpdate { it.copy(exposure = v) } }
    )

    LuxurySectionHeader("الفيزياء والطبقات الكونية")
    LuxuryToggleRow("الحركة الحلزونية للمنظومة الشمسية", checked = settings.helicalMotion, onCheckedChange = { c -> onUpdate { it.copy(helicalMotion = c) } })
    LuxuryToggleRow("نسيج انحناء الزمكان (أينشتاين)", checked = settings.spacetimeGrid, onCheckedChange = { c -> onUpdate { it.copy(spacetimeGrid = c) } })
    LuxuryToggleRow("الغيوم الحية في سماؤك", checked = settings.liveClouds, onCheckedChange = { c -> onUpdate { it.copy(liveClouds = c) } })
    LuxuryToggleRow("الغبار الكوني ثلاثي الأبعاد", checked = settings.cosmicDust, onCheckedChange = { c -> onUpdate { it.copy(cosmicDust = c) } })
    LuxuryToggleRow("حزام الكويكبات (5,000 جرم)", checked = settings.asteroidBelt, onCheckedChange = { c -> onUpdate { it.copy(asteroidBelt = c) } })
    LuxuryToggleRow("الشفق القطبي (Aurora Borealis)", checked = settings.auroras, onCheckedChange = { c -> onUpdate { it.copy(auroras = c) } })
    LuxuryToggleRow("المدارات والمسارات المضيئة", checked = settings.orbits, onCheckedChange = { c -> onUpdate { it.copy(orbits = c) } })
    LuxuryToggleRow("التسميات الفلكية الحية", checked = settings.labels, onCheckedChange = { c -> onUpdate { it.copy(labels = c) } })
    LuxuryToggleRow("خطوط الأبراج السماوية", checked = settings.constellations, onCheckedChange = { c -> onUpdate { it.copy(constellations = c) } })
    LuxuryToggleRow("مقياس مسافات حقيقي 1:1", checked = settings.trueScale, onCheckedChange = { c -> onUpdate { it.copy(trueScale = c) } })
    LuxuryToggleRow("توهّج HDR السينمائي (Bloom)", checked = settings.hdrBloom, onCheckedChange = { c -> onUpdate { it.copy(hdrBloom = c) } })
}
