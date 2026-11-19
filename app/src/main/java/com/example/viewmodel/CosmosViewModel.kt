package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.SpatialAudio8DEngine
import com.example.data.PlanetTexKey
import com.example.data.PlanetTextureLoader
import com.example.data.PreferencesRepository
import com.example.data.WeatherRepository
import com.example.domain.ActiveDrawer
import com.example.domain.AstrophysicsEngine
import com.example.domain.BlackHoleConfig
import com.example.domain.BlackHolePreset
import com.example.domain.BlackHoleTelemetry
import com.example.domain.CelestialBodyId
import com.example.domain.CityCatalog
import com.example.domain.CityLocation
import com.example.domain.CosmosMode
import com.example.domain.CosmosUiState
import com.example.domain.EngineSettings
import com.example.domain.ProjectedLabel
import com.example.domain.RenderQuality
import com.example.domain.SolarCatalog
import com.example.domain.StudioConfig
import com.example.domain.StudioPreset
import com.example.domain.TimeSpeeds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CosmosViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val weatherRepo = WeatherRepository()
    private val textureLoader = PlanetTextureLoader()
    private val prefsRepo = PreferencesRepository(application.applicationContext)
    val audio8D = SpatialAudio8DEngine()

    private val _uiState = MutableStateFlow(
        CosmosUiState(
            bodyTelemetry = AstrophysicsEngine.computeBodyTelemetry(
                CelestialBodyId.EARTH,
                System.currentTimeMillis()
            ),
            skyTelemetry = AstrophysicsEngine.computeSkyTelemetry(
                CityCatalog.cities.first(),
                System.currentTimeMillis()
            )
        )
    )
    val uiState: StateFlow<CosmosUiState> = _uiState.asStateFlow()

    private val _projectedLabels = MutableStateFlow<List<ProjectedLabel>>(emptyList())
    val projectedLabels: StateFlow<List<ProjectedLabel>> = _projectedLabels.asStateFlow()

    private var toastJob: Job? = null
    private var texturesStarted = false
    private var lastSkyRefreshMs = 0L

    init {
        viewModelScope.launch {
            val (savedSettings, savedCityId) = prefsRepo.savedSettings.first()
            val city = CityCatalog.cities.find { it.id == savedCityId } ?: CityCatalog.cities.first()
            val now = System.currentTimeMillis()
            _uiState.update {
                it.copy(
                    settings = savedSettings,
                    selectedCity = city,
                    skyTelemetry = AstrophysicsEngine.computeSkyTelemetry(city, now)
                )
            }
            fetchWeatherForCity(city)
        }

        // Smooth splash reveal (fade out automatically after luxury intro)
        viewModelScope.launch {
            delay(1350L)
            _uiState.update { it.copy(splashVisible = false) }
        }

        // UI telemetry & clock readout loop (5 Hz — OpenGL renderer owns continuous 60/120 FPS motion!)
        viewModelScope.launch {
            var lastTickMs = System.currentTimeMillis()
            while (isActive) {
                delay(200L)
                val now = System.currentTimeMillis()
                val realDtSec = ((now - lastTickMs) / 1000.0).coerceIn(0.0, 0.5)
                lastTickMs = now

                val st = _uiState.value
                if (st.isPlaying) {
                    val speed = TimeSpeeds.options[st.speedIndex].multiplierSecondsPerSecond
                    val dir = if (st.isReversed) -1.0 else 1.0
                    val simDeltaSec = realDtSec * speed
                    val nextEpoch = st.simEpochMillis + (simDeltaSec * dir * 1000.0).toLong()
                    val telemetry = AstrophysicsEngine.computeBodyTelemetry(st.selectedBody, nextEpoch)

                    val nextHelical = if (st.mode == CosmosMode.HELICAL || st.settings.helicalMotion) {
                        st.helicalTelemetry.copy(
                            distanceTraveledKm = st.helicalTelemetry.distanceTraveledKm +
                                simDeltaSec * AstrophysicsEngine.GALACTIC_SPEED_KMS
                        )
                    } else st.helicalTelemetry

                    val nextSky = if (st.mode == CosmosMode.EARTH_SKY && now - lastSkyRefreshMs > 1200L) {
                        lastSkyRefreshMs = now
                        AstrophysicsEngine.computeSkyTelemetry(
                            st.selectedCity,
                            nextEpoch,
                            st.skyTelemetry.cameraAzimuthDeg,
                            st.skyTelemetry.cameraAltitudeDeg
                        )
                    } else st.skyTelemetry

                    _uiState.update {
                        it.copy(
                            simEpochMillis = nextEpoch,
                            bodyTelemetry = telemetry,
                            helicalTelemetry = nextHelical,
                            skyTelemetry = nextSky
                        )
                    }
                }
            }
        }
    }

    fun startStreamingNasaTextures(onTextureReady: (PlanetTexKey, Bitmap) -> Unit) {
        if (texturesStarted) return
        texturesStarted = true
        val keys = PlanetTexKey.entries
        _uiState.update { it.copy(texturesTotalCount = keys.size, texturesLoadedCount = 0) }
        viewModelScope.launch {
            for (key in keys) {
                launch {
                    val bmp = textureLoader.loadTextureBitmap(key)
                    if (bmp != null) {
                        onTextureReady(key, bmp)
                    }
                    _uiState.update { it.copy(texturesLoadedCount = it.texturesLoadedCount + 1) }
                }
            }
        }
    }

    fun onRendererTelemetry(
        bhTelemetry: BlackHoleTelemetry,
        labels: List<ProjectedLabel>,
        cameraYawRad: Float,
        skyAzDeg: Float = 180f,
        skyAltDeg: Float = 24f
    ) {
        _projectedLabels.value = labels
        val st = _uiState.value
        audio8D.updateTelemetry(st.mode, bhTelemetry.distanceRs.toFloat(), cameraYawRad)
        when (st.mode) {
            CosmosMode.BLACK_HOLE -> _uiState.update { it.copy(bhTelemetry = bhTelemetry) }
            CosmosMode.EARTH_SKY -> _uiState.update {
                it.copy(
                    skyTelemetry = it.skyTelemetry.copy(
                        cameraAzimuthDeg = skyAzDeg,
                        cameraAltitudeDeg = skyAltDeg
                    )
                )
            }
            else -> Unit
        }
    }

    fun selectMode(mode: CosmosMode) {
        audio8D.triggerChime(if (mode == CosmosMode.BLACK_HOLE) 396f else 587f)
        _uiState.update { cur ->
            val updatedSettings = cur.settings.copy(
                helicalMotion = if (mode == CosmosMode.HELICAL) true else if (mode == CosmosMode.SPACE) false else cur.settings.helicalMotion
            )
            // Automatically engage breathtaking 30d/sec vortex flow when entering Helical mode!
            val nextSpeedIdx = when {
                mode == CosmosMode.HELICAL && cur.speedIndex < 3 -> 4
                mode == CosmosMode.EARTH_SKY -> 0 // Real-time 1x for live city sky observatory
                mode == CosmosMode.SPACE && cur.mode == CosmosMode.HELICAL -> 2
                else -> cur.speedIndex
            }
            val nextSky = if (mode == CosmosMode.EARTH_SKY) {
                AstrophysicsEngine.computeSkyTelemetry(cur.selectedCity, cur.simEpochMillis)
            } else cur.skyTelemetry

            cur.copy(
                mode = mode,
                speedIndex = nextSpeedIdx,
                isPlaying = if (mode == CosmosMode.HELICAL) true else cur.isPlaying,
                settings = updatedSettings,
                skyTelemetry = nextSky,
                helicalTelemetry = if (mode == CosmosMode.HELICAL) cur.helicalTelemetry.copy(distanceTraveledKm = 0.0) else cur.helicalTelemetry,
                activeDrawer = ActiveDrawer.NONE,
                blackHole = cur.blackHole.copy(divingToHorizon = false)
            )
        }
        if (mode == CosmosMode.EARTH_SKY) {
            fetchWeatherForCity(_uiState.value.selectedCity)
        } else if (mode == CosmosMode.HELICAL) {
            showToast("الدوّامة الحلزونية الشمسية · 230 كم/ث عبر درب التبانة")
        }
    }

    fun selectBody(body: CelestialBodyId) {
        audio8D.triggerChime(640f)
        val epoch = _uiState.value.simEpochMillis
        val tele = AstrophysicsEngine.computeBodyTelemetry(body, epoch)
        _uiState.update { cur ->
            val targetMode = if (cur.mode == CosmosMode.BLACK_HOLE || cur.mode == CosmosMode.GALAXY || cur.mode == CosmosMode.EARTH_SKY) {
                CosmosMode.SPACE
            } else cur.mode
            cur.copy(
                mode = targetMode,
                selectedBody = body,
                bodyTelemetry = tele
            )
        }
        val spec = SolarCatalog.byId.getValue(body)
        showToast("التركيز على ${spec.nameAr} · ${spec.nameEn}")
    }

    fun togglePlay() {
        audio8D.triggerChime(520f)
        _uiState.update { it.copy(isPlaying = !it.isPlaying) }
    }

    fun toggleReverse() {
        audio8D.triggerChime(460f)
        _uiState.update { it.copy(isReversed = !it.isReversed) }
    }

    fun resetToNow() {
        audio8D.triggerChime(700f)
        val now = System.currentTimeMillis()
        val cur = _uiState.value
        val tele = AstrophysicsEngine.computeBodyTelemetry(cur.selectedBody, now)
        val sky = AstrophysicsEngine.computeSkyTelemetry(
            cur.selectedCity, now, cur.skyTelemetry.cameraAzimuthDeg, cur.skyTelemetry.cameraAltitudeDeg
        )
        _uiState.update {
            it.copy(
                simEpochMillis = now,
                isReversed = false,
                speedIndex = 0,
                bodyTelemetry = tele,
                skyTelemetry = sky,
                clockSyncVersion = it.clockSyncVersion + 1L
            )
        }
        showToast("تم ضبط التوقيت على اللحظة الحقيقية الآن")
    }

    fun selectSpeed(index: Int) {
        audio8D.triggerChime(540f + index * 35f)
        _uiState.update { it.copy(speedIndex = index.coerceIn(TimeSpeeds.options.indices), isPlaying = true) }
    }

    fun dismissSplash() {
        _uiState.update { it.copy(splashVisible = false) }
    }

    fun toggleCinemaMode() {
        _uiState.update { it.copy(cinemaMode = !it.cinemaMode, activeDrawer = ActiveDrawer.NONE) }
    }

    fun toggleInfoExpanded() {
        _uiState.update { it.copy(infoExpanded = !it.infoExpanded) }
    }

    fun toggleAudio8D() {
        val next = !_uiState.value.audio8DEnabled
        audio8D.setEnabled(next)
        if (next) audio8D.triggerChime(528f)
        _uiState.update { it.copy(audio8DEnabled = next) }
        showToast(if (next) "الصوت المكاني 8D (HRTF) نشط — يُنصح بالسماعات" else "تم إيقاف الصوت المكاني 8D")
    }

    fun setGyroAvailability(available: Boolean) {
        _uiState.update { it.copy(gyroAvailable = available) }
    }

    fun toggleGyroscope(onToggleResult: (Boolean) -> Boolean) {
        val desired = !_uiState.value.gyroscopeEnabled
        val actual = onToggleResult(desired)
        _uiState.update { it.copy(gyroscopeEnabled = actual) }
        showToast(
            if (actual) "مستشعر الجوال نشط — وجّه الهاتف نحو السماء"
            else if (desired) "مستشعر الجيروسكوب غير متاح — استخدم اللمس للاستكشاف"
            else "تم إيقاف مستشعر الجوال"
        )
    }

    fun openDrawer(drawer: ActiveDrawer) {
        audio8D.triggerChime(600f)
        _uiState.update {
            it.copy(activeDrawer = if (it.activeDrawer == drawer) ActiveDrawer.NONE else drawer)
        }
    }

    fun closeDrawer() {
        _uiState.update { it.copy(activeDrawer = ActiveDrawer.NONE) }
    }

    fun applyStudioPreset(preset: StudioPreset) {
        audio8D.triggerChime(660f)
        val cfg = when (preset) {
            StudioPreset.REALISTIC_8K -> StudioConfig(
                preset = preset, planetScale = 1.0f, oblatenessOverride = 0.0f, visualSpin = 0.06f,
                reliefDepth = 1.45f, atmosphereDensity = 1.25f, cloudDensity = 1.05f,
                nightLights = 1.35f, ringIntensity = 1.10f, sunIntensity = 1.08f
            )
            StudioPreset.CINEMA_HDR -> StudioConfig(
                preset = preset, planetScale = 1.18f, oblatenessOverride = 0.02f, visualSpin = 0.14f,
                reliefDepth = 1.85f, atmosphereDensity = 1.75f, cloudDensity = 1.28f,
                nightLights = 1.95f, ringIntensity = 1.40f, sunIntensity = 1.28f
            )
            StudioPreset.EXTREME_RELIEF -> StudioConfig(
                preset = preset, planetScale = 1.12f, oblatenessOverride = 0.0f, visualSpin = 0.04f,
                reliefDepth = 2.40f, atmosphereDensity = 0.95f, cloudDensity = 0.72f,
                nightLights = 1.45f, ringIntensity = 1.10f, sunIntensity = 1.18f
            )
            StudioPreset.STYLIZED_RINGS -> StudioConfig(
                preset = preset, planetScale = 1.35f, oblatenessOverride = 0.18f, visualSpin = 0.28f,
                reliefDepth = 1.60f, atmosphereDensity = 1.85f, cloudDensity = 1.35f,
                nightLights = 2.15f, ringIntensity = 1.75f, sunIntensity = 1.22f
            )
        }
        _uiState.update { it.copy(studio = cfg) }
        showToast("تم تطبيق نمط الاستوديو: ${preset.labelAr}")
    }

    fun updateStudio(transform: (StudioConfig) -> StudioConfig) {
        _uiState.update { it.copy(studio = transform(it.studio)) }
    }

    fun selectBlackHolePreset(preset: BlackHolePreset) {
        audio8D.triggerChime(396f)
        _uiState.update {
            it.copy(
                mode = CosmosMode.BLACK_HOLE,
                blackHole = it.blackHole.copy(
                    preset = preset,
                    spinA = preset.defaultSpin,
                    accretionTemp = preset.defaultAccretion,
                    polarJets = preset.defaultJets,
                    showS2Star = preset.defaultS2,
                    divingToHorizon = false
                )
            )
        }
        showToast("مختبر الثقب الأسود: ${preset.titleAr}")
    }

    fun updateBlackHole(transform: (BlackHoleConfig) -> BlackHoleConfig) {
        _uiState.update { it.copy(blackHole = transform(it.blackHole)) }
    }

    fun toggleDiveToHorizon() {
        val next = !_uiState.value.blackHole.divingToHorizon
        audio8D.triggerChime(if (next) 310f else 540f)
        _uiState.update { it.copy(blackHole = it.blackHole.copy(divingToHorizon = next)) }
        showToast(if (next) "بدء السقوط النسبوي نحو أفق الحدث..." else "تم تثبيت المدار حول الثقب الأسود")
    }

    fun selectCity(city: CityLocation) {
        audio8D.triggerChime(620f)
        _uiState.update {
            it.copy(
                selectedCity = city,
                skyTelemetry = AstrophysicsEngine.computeSkyTelemetry(
                    city, it.simEpochMillis, it.skyTelemetry.cameraAzimuthDeg, it.skyTelemetry.cameraAltitudeDeg
                )
            )
        }
        persistSettings()
        fetchWeatherForCity(city)
        showToast("تم تحديد الوجهة: ${city.nameAr}")
    }

    fun flyToSelectedCity() {
        audio8D.triggerChime(680f)
        val epoch = _uiState.value.simEpochMillis
        _uiState.update {
            it.copy(
                mode = CosmosMode.SPACE,
                selectedBody = CelestialBodyId.EARTH,
                bodyTelemetry = AstrophysicsEngine.computeBodyTelemetry(CelestialBodyId.EARTH, epoch),
                activeDrawer = ActiveDrawer.NONE,
                flyToTrigger = System.nanoTime()
            )
        }
        showToast("التحليق المداري فوق ${_uiState.value.selectedCity.nameAr}")
    }

    fun viewSelectedCitySky() {
        selectMode(CosmosMode.EARTH_SKY)
        showToast("سماء ${_uiState.value.selectedCity.nameAr} المباشرة الآن")
    }

    fun toggleSpacetimeGrid() {
        val next = !_uiState.value.settings.spacetimeGrid
        audio8D.triggerChime(if (next) 650f else 480f)
        _uiState.update {
            it.copy(
                mode = if (it.mode == CosmosMode.BLACK_HOLE || it.mode == CosmosMode.EARTH_SKY || it.mode == CosmosMode.GALAXY) CosmosMode.SPACE else it.mode,
                settings = it.settings.copy(spacetimeGrid = next)
            )
        }
        showToast(if (next) "تم تفعيل نسيج انحناء الزمكان (أينشتاين)" else "تم إخفاء نسيج الزمكان")
    }

    fun updateSettings(transform: (EngineSettings) -> EngineSettings) {
        _uiState.update { cur ->
            val next = transform(cur.settings)
            val nextMode = if (next.helicalMotion && cur.mode == CosmosMode.SPACE) CosmosMode.HELICAL
            else if (!next.helicalMotion && cur.mode == CosmosMode.HELICAL) CosmosMode.SPACE
            else cur.mode
            cur.copy(settings = next, mode = nextMode)
        }
        persistSettings()
    }

    fun selectQuality(quality: RenderQuality) {
        updateSettings { it.copy(quality = quality, hdrBloom = quality.bloomEnabled) }
        showToast("دقة التصيير: ${quality.labelAr}")
    }

    fun showToast(message: String) {
        toastJob?.cancel()
        _uiState.update { it.copy(toastMessage = message) }
        toastJob = viewModelScope.launch {
            delay(2600L)
            _uiState.update { it.copy(toastMessage = null) }
        }
    }

    private fun fetchWeatherForCity(city: CityLocation) {
        viewModelScope.launch {
            val w = weatherRepo.fetchLiveWeather(city)
            _uiState.update { it.copy(weather = w) }
        }
    }

    private fun persistSettings() {
        val st = _uiState.value
        viewModelScope.launch {
            prefsRepo.saveSettings(st.settings, st.selectedCity.id)
        }
    }

    override fun onCleared() {
        super.onCleared()
        audio8D.stop()
    }
}
