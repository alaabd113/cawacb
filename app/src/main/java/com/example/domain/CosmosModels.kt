package com.example.domain

import androidx.compose.ui.graphics.Color

enum class CosmosMode(val labelAr: String, val labelEn: String) {
    SPACE("الفضاء", "Space"),
    HELICAL("الحلزوني", "Helical"),
    EARTH_SKY("سماؤك الآن", "Your Sky"),
    GALAXY("درب التبانة", "Milky Way"),
    BLACK_HOLE("الثقب الأسود", "Black Hole")
}

enum class BodyKind { STAR, ROCK, VENUS, EARTH, GAS }

data class PlanetSpec(
    val id: CelestialBodyId,
    val nameAr: String,
    val nameEn: String,
    val radiusKm: Double,
    val visualRadius: Float,
    val accentColor: Color,
    val kind: BodyKind,
    val gmKm3s2: Double,
    val oblateness: Float,
    val axialTiltDeg: Float,
    val rotationHours: Double,
    // J2000 Keplerian elements (heliocentric, except Moon which uses Earth-centric model)
    val semiMajorAxisAu: Double,
    val eccentricity: Double,
    val inclinationDeg: Double,
    val lonAscNodeDeg: Double,
    val argPerihelionDeg: Double,
    val meanLonJ2000Deg: Double,
    val orbitalPeriodDays: Double,
    val surfaceTempC: String,
    val gravityMs2: String,
    val hasRings: Boolean = false
)

enum class CelestialBodyId {
    SUN, MERCURY, VENUS, EARTH, MOON, MARS, JUPITER, SATURN, URANUS, NEPTUNE
}

object SolarCatalog {
    val planets: List<PlanetSpec> = listOf(
        PlanetSpec(
            id = CelestialBodyId.SUN, nameAr = "الشمس", nameEn = "Sun",
            radiusKm = 695_700.0, visualRadius = 5.4f, accentColor = Color(0xFFFFD27A),
            kind = BodyKind.STAR, gmKm3s2 = 1.3271244e11, oblateness = 0f,
            axialTiltDeg = 7.25f, rotationHours = 609.12,
            semiMajorAxisAu = 0.0, eccentricity = 0.0, inclinationDeg = 0.0,
            lonAscNodeDeg = 0.0, argPerihelionDeg = 0.0, meanLonJ2000Deg = 0.0,
            orbitalPeriodDays = 0.0, surfaceTempC = "5,505 °C", gravityMs2 = "274.0 m/s²"
        ),
        PlanetSpec(
            id = CelestialBodyId.MERCURY, nameAr = "عطارد", nameEn = "Mercury",
            radiusKm = 2439.7, visualRadius = 0.56f, accentColor = Color(0xFFB4AFA8),
            kind = BodyKind.ROCK, gmKm3s2 = 22_032.0, oblateness = 0f,
            axialTiltDeg = 0.03f, rotationHours = 1407.6,
            semiMajorAxisAu = 0.387098, eccentricity = 0.205630, inclinationDeg = 7.005,
            lonAscNodeDeg = 48.331, argPerihelionDeg = 29.124, meanLonJ2000Deg = 252.251,
            orbitalPeriodDays = 87.969, surfaceTempC = "167 °C", gravityMs2 = "3.70 m/s²"
        ),
        PlanetSpec(
            id = CelestialBodyId.VENUS, nameAr = "الزهرة", nameEn = "Venus",
            radiusKm = 6051.8, visualRadius = 1.02f, accentColor = Color(0xFFF0DBA4),
            kind = BodyKind.VENUS, gmKm3s2 = 324_859.0, oblateness = 0f,
            axialTiltDeg = 177.36f, rotationHours = -5832.5,
            semiMajorAxisAu = 0.723332, eccentricity = 0.006772, inclinationDeg = 3.3947,
            lonAscNodeDeg = 76.680, argPerihelionDeg = 54.884, meanLonJ2000Deg = 181.979,
            orbitalPeriodDays = 224.701, surfaceTempC = "464 °C", gravityMs2 = "8.87 m/s²"
        ),
        PlanetSpec(
            id = CelestialBodyId.EARTH, nameAr = "الأرض", nameEn = "Earth",
            radiusKm = 6371.0, visualRadius = 1.38f, accentColor = Color(0xFF4D96FF),
            kind = BodyKind.EARTH, gmKm3s2 = 398_600.4, oblateness = 0.00335f,
            axialTiltDeg = 23.44f, rotationHours = 23.9345,
            semiMajorAxisAu = 1.000001, eccentricity = 0.016708, inclinationDeg = 0.0001,
            lonAscNodeDeg = -11.261, argPerihelionDeg = 114.208, meanLonJ2000Deg = 100.464,
            orbitalPeriodDays = 365.256, surfaceTempC = "15 °C", gravityMs2 = "9.81 m/s²"
        ),
        PlanetSpec(
            id = CelestialBodyId.MOON, nameAr = "القمر", nameEn = "Moon",
            radiusKm = 1737.4, visualRadius = 0.42f, accentColor = Color(0xFFD4DCE6),
            kind = BodyKind.ROCK, gmKm3s2 = 4_904.87, oblateness = 0.0012f,
            axialTiltDeg = 6.68f, rotationHours = 655.72,
            semiMajorAxisAu = 0.0025695, eccentricity = 0.0549, inclinationDeg = 5.145,
            lonAscNodeDeg = 125.08, argPerihelionDeg = 318.15, meanLonJ2000Deg = 218.32,
            orbitalPeriodDays = 27.3217, surfaceTempC = "-23 °C", gravityMs2 = "1.62 m/s²"
        ),
        PlanetSpec(
            id = CelestialBodyId.MARS, nameAr = "المريخ", nameEn = "Mars",
            radiusKm = 3389.5, visualRadius = 0.82f, accentColor = Color(0xFFE66C40),
            kind = BodyKind.ROCK, gmKm3s2 = 42_828.4, oblateness = 0.00589f,
            axialTiltDeg = 25.19f, rotationHours = 24.6229,
            semiMajorAxisAu = 1.523679, eccentricity = 0.093405, inclinationDeg = 1.850,
            lonAscNodeDeg = 49.558, argPerihelionDeg = 286.502, meanLonJ2000Deg = 355.453,
            orbitalPeriodDays = 686.980, surfaceTempC = "-65 °C", gravityMs2 = "3.71 m/s²"
        ),
        PlanetSpec(
            id = CelestialBodyId.JUPITER, nameAr = "المشتري", nameEn = "Jupiter",
            radiusKm = 71_492.0, visualRadius = 2.9f, accentColor = Color(0xFFDFA975),
            kind = BodyKind.GAS, gmKm3s2 = 1.266865e8, oblateness = 0.06487f,
            axialTiltDeg = 3.13f, rotationHours = 9.925,
            semiMajorAxisAu = 5.2044, eccentricity = 0.0489, inclinationDeg = 1.303,
            lonAscNodeDeg = 100.464, argPerihelionDeg = 273.867, meanLonJ2000Deg = 34.351,
            orbitalPeriodDays = 4332.59, surfaceTempC = "-110 °C", gravityMs2 = "24.79 m/s²"
        ),
        PlanetSpec(
            id = CelestialBodyId.SATURN, nameAr = "زحل", nameEn = "Saturn",
            radiusKm = 60_268.0, visualRadius = 2.5f, accentColor = Color(0xFFEBD294),
            kind = BodyKind.GAS, gmKm3s2 = 3.7931187e7, oblateness = 0.09796f,
            axialTiltDeg = 26.73f, rotationHours = 10.656,
            semiMajorAxisAu = 9.5826, eccentricity = 0.0565, inclinationDeg = 2.485,
            lonAscNodeDeg = 113.665, argPerihelionDeg = 339.392, meanLonJ2000Deg = 50.077,
            orbitalPeriodDays = 10_759.22, surfaceTempC = "-140 °C", gravityMs2 = "10.44 m/s²",
            hasRings = true
        ),
        PlanetSpec(
            id = CelestialBodyId.URANUS, nameAr = "أورانوس", nameEn = "Uranus",
            radiusKm = 25_559.0, visualRadius = 1.55f, accentColor = Color(0xFF8BE2F0),
            kind = BodyKind.GAS, gmKm3s2 = 5.793939e6, oblateness = 0.0229f,
            axialTiltDeg = 97.77f, rotationHours = -17.24,
            semiMajorAxisAu = 19.2184, eccentricity = 0.04638, inclinationDeg = 0.773,
            lonAscNodeDeg = 74.006, argPerihelionDeg = 96.998, meanLonJ2000Deg = 314.055,
            orbitalPeriodDays = 30_688.5, surfaceTempC = "-195 °C", gravityMs2 = "8.69 m/s²",
            hasRings = true
        ),
        PlanetSpec(
            id = CelestialBodyId.NEPTUNE, nameAr = "نبتون", nameEn = "Neptune",
            radiusKm = 24_764.0, visualRadius = 1.5f, accentColor = Color(0xFF4C80FF),
            kind = BodyKind.GAS, gmKm3s2 = 6.836529e6, oblateness = 0.0171f,
            axialTiltDeg = 28.32f, rotationHours = 16.11,
            semiMajorAxisAu = 30.1104, eccentricity = 0.00946, inclinationDeg = 1.770,
            lonAscNodeDeg = 131.784, argPerihelionDeg = 276.336, meanLonJ2000Deg = 304.348,
            orbitalPeriodDays = 60_182.0, surfaceTempC = "-200 °C", gravityMs2 = "11.15 m/s²"
        )
    )

    val byId: Map<CelestialBodyId, PlanetSpec> = planets.associateBy { it.id }
}

enum class BlackHolePreset(
    val shortAr: String,
    val titleAr: String,
    val titleEn: String,
    val badge: String,
    val massAr: String,
    val distanceAr: String,
    val schwarzschildAr: String,
    val defaultSpin: Float,
    val defaultAccretion: Float,
    val defaultJets: Boolean,
    val defaultS2: Boolean
) {
    SGR_A(
        shortAr = "الرامي A*",
        titleAr = "الرامي A* (قلب المجرة)",
        titleEn = "Sagittarius A* · SMBH",
        badge = "Sgr A*",
        massAr = "4.3 مليون كتلة شمسية",
        distanceAr = "27,000 سنة ضوئية",
        schwarzschildAr = "12.7 مليون كم",
        defaultSpin = 0.90f,
        defaultAccretion = 1.32f,
        defaultJets = false,
        defaultS2 = true
    ),
    M87(
        shortAr = "M87*",
        titleAr = "مسيه 87* (العذراء A)",
        titleEn = "Messier 87* · Supergiant SMBH",
        badge = "M87*",
        massAr = "6.5 مليار كتلة شمسية",
        distanceAr = "55 مليون سنة ضوئية",
        schwarzschildAr = "19.2 مليار كم",
        defaultSpin = 0.92f,
        defaultAccretion = 1.55f,
        defaultJets = true,
        defaultS2 = false
    ),
    CYGNUS_X1(
        shortAr = "Cyg X-1",
        titleAr = "الدجاجة X-1",
        titleEn = "Cygnus X-1 · Stellar BH",
        badge = "Cyg X-1",
        massAr = "21.2 كتلة شمسية",
        distanceAr = "7,240 سنة ضوئية",
        schwarzschildAr = "62.6 كم",
        defaultSpin = 0.96f,
        defaultAccretion = 1.65f,
        defaultJets = true,
        defaultS2 = false
    )
}

data class CityLocation(
    val id: String,
    val nameAr: String,
    val lat: Double,
    val lon: Double,
    val tzId: String
)

object CityCatalog {
    val cities = listOf(
        CityLocation("beirut", "بيروت", 33.8938, 35.5018, "Asia/Beirut"),
        CityLocation("damascus", "دمشق", 33.5138, 36.2765, "Asia/Damascus"),
        CityLocation("jerusalem", "القدس", 31.7683, 35.2137, "Asia/Jerusalem"),
        CityLocation("amman", "عمّان", 31.9454, 35.9284, "Asia/Amman"),
        CityLocation("baghdad", "بغداد", 33.3152, 44.3661, "Asia/Baghdad"),
        CityLocation("mecca", "مكة", 21.4225, 39.8262, "Asia/Riyadh"),
        CityLocation("riyadh", "الرياض", 24.7136, 46.6753, "Asia/Riyadh"),
        CityLocation("dubai", "دبي", 25.2048, 55.2708, "Asia/Dubai"),
        CityLocation("cairo", "القاهرة", 30.0444, 31.2357, "Africa/Cairo"),
        CityLocation("london", "لندن", 51.5074, -0.1278, "Europe/London"),
        CityLocation("paris", "باريس", 48.8566, 2.3522, "Europe/Paris"),
        CityLocation("tokyo", "طوكيو", 35.6762, 139.6503, "Asia/Tokyo")
    )
}

enum class StudioPreset(val labelAr: String) {
    REALISTIC_8K("واقعي 8K"),
    CINEMA_HDR("سينمائي HDR"),
    EXTREME_RELIEF("تضاريس قصوى"),
    STYLIZED_RINGS("تفلطح وحلقات")
}

data class StudioConfig(
    val preset: StudioPreset = StudioPreset.REALISTIC_8K,
    val planetScale: Float = 1.0f,
    val oblatenessOverride: Float = 0.0f,
    val visualSpin: Float = 0.08f,
    val reliefDepth: Float = 1.45f,
    val atmosphereDensity: Float = 1.25f,
    val cloudDensity: Float = 1.05f,
    val nightLights: Float = 1.35f,
    val ringIntensity: Float = 1.10f,
    val sunIntensity: Float = 1.08f
)

data class BlackHoleConfig(
    val preset: BlackHolePreset = BlackHolePreset.SGR_A,
    val spinA: Float = 0.90f,
    val accretionTemp: Float = 1.32f,
    val lensingStrength: Float = 1.0f,
    val dopplerShift: Boolean = true,
    val polarJets: Boolean = false,
    val showS2Star: Boolean = true,
    val divingToHorizon: Boolean = false
)

enum class RenderQuality(
    val labelAr: String,
    val badge: String,
    val resolutionScale: Float,
    val bhMaxSteps: Int,
    val bloomEnabled: Boolean
) {
    ULTRA_8K("فائقة (8K Ultra)", "8K HDR", 1.0f, 108, true),
    HIGH_4K("عالية (4K)", "4K HDR", 0.85f, 88, true),
    BALANCED("متوسطة", "HD+", 0.70f, 68, true),
    ECO("اقتصادية", "ECO", 0.55f, 52, false)
}

data class EngineSettings(
    val helicalMotion: Boolean = false,
    val spacetimeGrid: Boolean = false,
    val liveClouds: Boolean = true,
    val cosmicDust: Boolean = true,
    val asteroidBelt: Boolean = true,
    val auroras: Boolean = true,
    val orbits: Boolean = true,
    val labels: Boolean = true,
    val constellations: Boolean = true,
    val trueScale: Boolean = false,
    val hdrBloom: Boolean = true,
    val quality: RenderQuality = RenderQuality.ULTRA_8K,
    val exposure: Float = 1.08f
)

data class WeatherInfo(
    val cloudCoverPercent: Int = 28,
    val temperatureC: Double = 22.0,
    val descriptionAr: String = "سماء صافية جزئياً",
    val isLive: Boolean = false
)

data class BlackHoleTelemetry(
    val distanceRs: Double = 14.2,
    val timeDilationPercent: Double = 96.4,
    val escapeVelocityC: Double = 0.265,
    val eventHorizonRs: Double = 0.718,
    val iscoRs: Double = 1.16,
    val shipClockSec: Double = 0.0,
    val earthClockSec: Double = 0.0
)

data class SkyTelemetry(
    val moonPhaseNameAr: String = "أحدب متزايد",
    val moonIlluminationPct: Int = 78,
    val moonAltitudeDeg: Double = 24.0,
    val sunAltitudeDeg: Double = -18.0,
    val nextSunriseLocal: String = "06:15",
    val nextSunsetLocal: String = "17:42",
    val visiblePlanetsCount: Int = 4,
    val cameraAzimuthDeg: Float = 180f,
    val cameraAltitudeDeg: Float = 28f
)

data class HelicalTelemetry(
    val galacticSpeedKms: Double = 230.0,
    val galacticSpeedKmh: String = "828,000 كم/س",
    val apexTargetAr: String = "نحو النسر الواقع (Vega)",
    val galacticYearAr: String = "230 مليون سنة",
    val distanceTraveledKm: Double = 0.0
)

data class BodyTelemetry(
    val titleAr: String = "الأرض",
    val subtitle: String = "Earth · عالم حي",
    val distanceFromSunKm: String = "149.60 مليون كم",
    val distanceFromEarthKm: String = "0 كم",
    val orbitalSpeedKms: String = "29.78 كم/ث",
    val lightTimeFromSun: String = "8.32 دقيقة ضوئية",
    val surfaceTemp: String = "15 °C",
    val gravity: String = "9.81 m/s²",
    val extraRelativistic: String = "تمدد زمني: 0.999999990",
    val apparentMag: String = "-26.74"
)

data class ProjectedLabel(
    val id: String,
    val text: String,
    val xNorm: Float,
    val yNorm: Float,
    val color: Color,
    val isSubtle: Boolean = false,
    val isBlackHoleTag: Boolean = false
)

enum class ActiveDrawer {
    NONE, STUDIO, PLACES, BLACK_HOLE, SETTINGS
}

data class TimeSpeedOption(
    val id: String,
    val label: String,
    val multiplierSecondsPerSecond: Double
)

object TimeSpeeds {
    val options = listOf(
        TimeSpeedOption("1x", "1×", 1.0),
        TimeSpeedOption("1h", "1س/ث", 3600.0),
        TimeSpeedOption("1d", "1ي/ث", 86_400.0),
        TimeSpeedOption("1w", "7ي/ث", 604_800.0),
        TimeSpeedOption("1m", "30ي/ث", 2_592_000.0)
    )
}

data class CosmosUiState(
    val mode: CosmosMode = CosmosMode.SPACE,
    val selectedBody: CelestialBodyId = CelestialBodyId.EARTH,
    val selectedCity: CityLocation = CityCatalog.cities.first(),
    val simEpochMillis: Long = System.currentTimeMillis(),
    val isPlaying: Boolean = true,
    val isReversed: Boolean = false,
    val speedIndex: Int = 2, // 1 day/sec feels alive & majestic immediately
    val cinemaMode: Boolean = false,
    val infoExpanded: Boolean = false,
    val audio8DEnabled: Boolean = false,
    val gyroscopeEnabled: Boolean = false,
    val gyroAvailable: Boolean = true,
    val activeDrawer: ActiveDrawer = ActiveDrawer.NONE,
    val studio: StudioConfig = StudioConfig(),
    val blackHole: BlackHoleConfig = BlackHoleConfig(),
    val settings: EngineSettings = EngineSettings(),
    val weather: WeatherInfo = WeatherInfo(),
    val bodyTelemetry: BodyTelemetry = BodyTelemetry(),
    val bhTelemetry: BlackHoleTelemetry = BlackHoleTelemetry(),
    val skyTelemetry: SkyTelemetry = SkyTelemetry(),
    val helicalTelemetry: HelicalTelemetry = HelicalTelemetry(),
    val texturesLoadedCount: Int = 0,
    val texturesTotalCount: Int = 11,
    val splashVisible: Boolean = true,
    val toastMessage: String? = null,
    val flyToTrigger: Long = 0L,
    val resetCameraTrigger: Long = 0L,
    val clockSyncVersion: Long = 0L
)
