package com.example.domain

import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.Direction
import io.github.cosinekitty.astronomy.EquatorEpoch
import io.github.cosinekitty.astronomy.Observer
import io.github.cosinekitty.astronomy.Refraction
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.equator
import io.github.cosinekitty.astronomy.geoVector
import io.github.cosinekitty.astronomy.helioVector
import io.github.cosinekitty.astronomy.horizon
import io.github.cosinekitty.astronomy.illumination
import io.github.cosinekitty.astronomy.moonPhase
import io.github.cosinekitty.astronomy.searchRiseSet
import io.github.cosinekitty.astronomy.siderealTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    fun length(): Float = sqrt(x * x + y * y + z * z)
    fun normalized(): Vec3 {
        val len = length()
        return if (len > 1e-6f) Vec3(x / len, y / len, z / len) else Vec3(0f, 1f, 0f)
    }
}

data class HorizontalCoords(
    val altitudeDeg: Double,
    val azimuthDeg: Double
)

data class LunarPhaseInfo(
    val phaseAngleDeg: Double,
    val illuminationPercent: Int,
    val phaseNameAr: String
)

data class StarEntry(
    val raDeg: Double,
    val decDeg: Double,
    val mag: Float,
    val bv: Float,
    val nameAr: String
)

data class ConstellationDef(
    val nameAr: String,
    val raDeg: Double,
    val decDeg: Double,
    val polylines: List<List<Pair<Double, Double>>>
)

/**
 * High-precision Astrophysics & Ephemeris Engine backed by CosineKitty Astronomy Engine
 * (VSOP87 planetary series, ELP/MPP02 lunar model, NOVAS atmospheric refraction & aberration)
 * combined with Kerr General Relativity black hole metrics.
 */
object AstrophysicsEngine {
    const val SCENE_AU = 120.0
    const val AU_KM = 149_597_870.7
    const val C_KMS = 299_792.458
    const val GALACTIC_SPEED_KMS = 230.0
    private const val J2000_MS = 946_728_000_000L // 2000-01-01T12:00:00Z
    private const val DEG = PI / 180.0

    /** Solar Apex trajectory unit vector (~60° inclined to the ecliptic plane toward Vega / Hercules). */
    val HELIX_DIR: Vec3 = Vec3(0.24f, 0.87f, 0.43f).normalized()
    const val HELIX_UNITS_PER_YEAR: Double = 3.65 * SCENE_AU

    /** 40 brightest Hipparcos catalog stars with authentic classical Arabic astronomical names. */
    val BRIGHT_STARS = listOf(
        StarEntry(101.29, -16.72, -1.46f, 0.00f, "الشعرى اليمانية"),
        StarEntry(95.99, -52.70, -0.74f, 0.15f, "سهيل"),
        StarEntry(219.90, -60.84, -0.27f, 0.71f, "رجل قنطورس"),
        StarEntry(213.92, 19.18, -0.05f, 1.23f, "السماك الرامح"),
        StarEntry(279.23, 38.78, 0.03f, 0.00f, "النسر الواقع"),
        StarEntry(79.17, 46.00, 0.08f, 0.80f, "العيّوق"),
        StarEntry(78.63, -8.20, 0.13f, -0.03f, "رجل الجبّار"),
        StarEntry(114.83, 5.22, 0.34f, 0.42f, "الشعرى الشامية"),
        StarEntry(24.43, -57.24, 0.46f, -0.16f, "آخر النهر"),
        StarEntry(88.79, 7.41, 0.50f, 1.85f, "منكب الجوزاء"),
        StarEntry(210.96, -60.37, 0.61f, -0.23f, "الحضار"),
        StarEntry(297.70, 8.87, 0.76f, 0.22f, "النسر الطائر"),
        StarEntry(186.65, -63.10, 0.77f, -0.24f, "نير الصليب"),
        StarEntry(68.98, 16.51, 0.86f, 1.54f, "الدبران"),
        StarEntry(201.30, -11.16, 0.97f, -0.23f, "السماك الأعزل"),
        StarEntry(247.35, -26.43, 0.96f, 1.83f, "قلب العقرب"),
        StarEntry(116.33, 28.03, 1.14f, 1.00f, "رأس التوأم المؤخر"),
        StarEntry(344.41, -29.62, 1.16f, 0.09f, "فم الحوت"),
        StarEntry(310.36, 45.28, 1.25f, 0.09f, "ذنب الدجاجة"),
        StarEntry(191.93, -59.69, 1.25f, -0.23f, "ميموزا"),
        StarEntry(152.09, 11.97, 1.35f, -0.11f, "قلب الأسد"),
        StarEntry(104.66, -28.97, 1.50f, -0.21f, "العذارى"),
        StarEntry(113.65, 31.89, 1.58f, 0.03f, "رأس التوأم المقدم"),
        StarEntry(263.40, -37.10, 1.62f, -0.22f, "الشولة"),
        StarEntry(81.28, 6.35, 1.64f, -0.22f, "المرزم الناجذ"),
        StarEntry(81.57, 28.61, 1.65f, -0.13f, "النطح"),
        StarEntry(84.05, -1.20, 1.69f, -0.19f, "النظم"),
        StarEntry(193.51, 55.96, 1.77f, -0.02f, "الجون"),
        StarEntry(165.93, 61.75, 1.79f, 1.07f, "الدُّبّ"),
        StarEntry(264.33, -43.00, 1.86f, 0.40f, "سرجس"),
        StarEntry(206.89, 49.31, 1.86f, -0.19f, "قائد بنات نعش"),
        StarEntry(85.19, -1.94, 1.77f, -0.21f, "النطاق"),
        StarEntry(37.95, 89.26, 1.98f, 0.60f, "النجم القطبي"),
        StarEntry(95.67, -17.96, 1.98f, -0.23f, "مرزم الشعرى"),
        StarEntry(141.90, -8.66, 1.98f, 1.44f, "الفرد"),
        StarEntry(31.79, 23.46, 2.00f, 1.15f, "الناطح"),
        StarEntry(177.26, 14.57, 2.14f, 0.09f, "الصرفة"),
        StarEntry(263.73, 12.56, 2.08f, 0.15f, "رأس الحواء"),
        StarEntry(10.13, 56.54, 2.23f, 1.17f, "صدر الكرسي"),
        StarEntry(56.87, 24.11, 2.87f, -0.09f, "الثريا")
    )

    val CONSTELLATIONS = listOf(
        ConstellationDef(
            "الجبّار", 83.0, 3.0,
            listOf(
                listOf(88.8 to 7.4, 81.3 to 6.3, 83.0 to -0.3, 84.1 to -1.2, 85.2 to -1.9, 86.9 to -9.7, 78.6 to -8.2, 83.0 to -0.3),
                listOf(88.8 to 7.4, 85.2 to -1.9)
            )
        ),
        ConstellationDef(
            "الدب الأكبر", 185.0, 56.0,
            listOf(
                listOf(165.9 to 61.8, 165.5 to 56.4, 178.5 to 53.7, 183.9 to 57.0, 165.9 to 61.8),
                listOf(183.9 to 57.0, 193.5 to 56.0, 201.0 to 54.9, 206.9 to 49.3)
            )
        ),
        ConstellationDef(
            "ذات الكرسي", 15.0, 60.0,
            listOf(listOf(28.6 to 63.7, 21.5 to 60.2, 14.2 to 60.7, 10.1 to 56.5, 2.3 to 59.1))
        ),
        ConstellationDef(
            "العقرب", 252.0, -31.0,
            listOf(listOf(240.1 to -22.6, 247.4 to -26.4, 252.5 to -34.3, 253.6 to -42.4, 264.3 to -43.0, 263.4 to -37.1))
        ),
        ConstellationDef(
            "الأسد", 158.0, 18.0,
            listOf(listOf(152.1 to 12.0, 151.8 to 16.8, 155.0 to 19.8, 168.5 to 20.5, 177.3 to 14.6, 168.6 to 15.4, 152.1 to 12.0))
        ),
        ConstellationDef(
            "الدجاجة", 304.0, 40.0,
            listOf(
                listOf(310.4 to 45.3, 305.6 to 40.3, 292.7 to 28.0),
                listOf(318.2 to 30.2, 305.6 to 40.3, 296.2 to 45.1)
            )
        ),
        ConstellationDef(
            "التوأمان", 112.0, 24.0,
            listOf(
                listOf(113.7 to 31.9, 106.0 to 20.6, 95.7 to 22.5),
                listOf(116.3 to 28.0, 110.0 to 22.0, 99.4 to 16.4),
                listOf(113.7 to 31.9, 116.3 to 28.0)
            )
        ),
        ConstellationDef(
            "الثور", 68.0, 18.0,
            listOf(
                listOf(81.6 to 28.6, 69.0 to 16.5, 65.0 to 15.6, 67.2 to 19.2, 84.4 to 21.1),
                listOf(65.0 to 15.6, 56.9 to 24.1)
            )
        )
    )

    private fun toAstroBody(id: CelestialBodyId): Body = when (id) {
        CelestialBodyId.SUN -> Body.Sun
        CelestialBodyId.MERCURY -> Body.Mercury
        CelestialBodyId.VENUS -> Body.Venus
        CelestialBodyId.EARTH -> Body.Earth
        CelestialBodyId.MOON -> Body.Moon
        CelestialBodyId.MARS -> Body.Mars
        CelestialBodyId.JUPITER -> Body.Jupiter
        CelestialBodyId.SATURN -> Body.Saturn
        CelestialBodyId.URANUS -> Body.Uranus
        CelestialBodyId.NEPTUNE -> Body.Neptune
    }

    private fun toAstroTime(epochMillis: Long): Time = Time.fromMillisecondsSince1970(epochMillis)

    fun daysSinceJ2000(epochMillis: Long): Double =
        (epochMillis - J2000_MS).toDouble() / 86_400_000.0

    fun julianDay(epochMillis: Long): Double = 2451545.0 + daysSinceJ2000(epochMillis)

    /** Greenwich Apparent Sidereal Time in degrees [0, 360) via Astronomy Engine (includes nutation). */
    fun greenwichSiderealDeg(epochMillis: Long): Double {
        val hours = siderealTime(toAstroTime(epochMillis))
        return ((hours * 15.0) % 360.0 + 360.0) % 360.0
    }

    /**
     * VSOP87 / ELP-MPP02 heliocentric position in AU mapped to OpenGL Y-up Equatorial J2000 coordinates.
     */
    fun heliocentricAu(id: CelestialBodyId, epochMillis: Long): Vec3 {
        if (id == CelestialBodyId.SUN) return Vec3(0f, 0f, 0f)
        val t = toAstroTime(epochMillis)
        val v = helioVector(toAstroBody(id), t)
        // J2000 Equatorial (eqX, eqY, eqZ) -> OpenGL Y-up (eqX, eqZ, -eqY)
        return Vec3(v.x.toFloat(), v.z.toFloat(), (-v.y).toFloat())
    }

    /** ELP/MPP02 Geocentric Moon vector in AU (OpenGL Y-up Equatorial J2000). */
    fun moonGeocentricAu(epochMillis: Long): Vec3 {
        val v = geoVector(Body.Moon, toAstroTime(epochMillis), Aberration.None)
        return Vec3(v.x.toFloat(), v.z.toFloat(), (-v.y).toFloat())
    }

    /** Scene-space position of any body (with optional Helical solar drift and visual spacing). */
    fun scenePosition(
        id: CelestialBodyId,
        epochMillis: Long,
        helicalEnabled: Boolean,
        helicalEpochRefMillis: Long,
        trueScale: Boolean
    ): Vec3 {
        val sunOffset = helicalSunOffset(epochMillis, helicalEpochRefMillis, helicalEnabled)
        if (id == CelestialBodyId.SUN) return sunOffset

        if (id == CelestialBodyId.MOON) {
            val earthScene = scenePosition(CelestialBodyId.EARTH, epochMillis, helicalEnabled, helicalEpochRefMillis, trueScale)
            val moonRelAu = moonGeocentricAu(epochMillis)
            val moonScale = if (trueScale) SCENE_AU.toFloat() else (SCENE_AU * 19.5).toFloat()
            return earthScene + moonRelAu * moonScale
        }

        val au = heliocentricAu(id, epochMillis)
        val rAu = au.length().toDouble()
        val sceneR = if (trueScale || rAu < 1e-6) {
            rAu * SCENE_AU
        } else {
            // Smooth artistic compression for outer gas giants so entire solar system & helical vortex look lush
            when {
                rAu <= 1.6 -> rAu * SCENE_AU
                else -> (1.6 + (rAu - 1.6).pow(0.68) * 1.12) * SCENE_AU
            }
        }
        val dir = au.normalized()
        return sunOffset + dir * sceneR.toFloat()
    }

    fun helicalSunOffset(epochMillis: Long, epochRefMillis: Long, enabled: Boolean): Vec3 =
        helicalSunOffsetDouble(epochMillis.toDouble(), epochRefMillis.toDouble(), enabled)

    fun helicalSunOffsetDouble(epochMillis: Double, epochRefMillis: Double, enabled: Boolean): Vec3 {
        if (!enabled) return Vec3(0f, 0f, 0f)
        val years = (epochMillis - epochRefMillis) / (365.25 * 86_400_000.0)
        return HELIX_DIR * (years * HELIX_UNITS_PER_YEAR).toFloat()
    }

    fun daysSinceJ2000Double(epochMillis: Double): Double =
        (epochMillis - J2000_MS.toDouble()) / 86_400_000.0

    /**
     * Sub-millisecond continuous scene position for 60/120 FPS OpenGL frame integration.
     * Avoids duplicate Earth ephemeris lookup when [precomputedEarthPos] is supplied for the Moon.
     */
    fun scenePositionContinuous(
        id: CelestialBodyId,
        epochMillis: Double,
        helicalEnabled: Boolean,
        helicalEpochRefMillis: Double,
        trueScale: Boolean,
        precomputedEarthPos: Vec3? = null
    ): Vec3 {
        val sunOffset = helicalSunOffsetDouble(epochMillis, helicalEpochRefMillis, helicalEnabled)
        if (id == CelestialBodyId.SUN) return sunOffset

        val epochLong = epochMillis.toLong()
        if (id == CelestialBodyId.MOON) {
            val earthScene = precomputedEarthPos ?: scenePositionContinuous(
                CelestialBodyId.EARTH, epochMillis, helicalEnabled, helicalEpochRefMillis, trueScale
            )
            val moonRelAu = moonGeocentricAu(epochLong)
            val moonScale = if (trueScale) SCENE_AU.toFloat() else (SCENE_AU * 19.5).toFloat()
            return earthScene + moonRelAu * moonScale
        }

        val au = heliocentricAu(id, epochLong)
        val rAu = au.length().toDouble()
        val sceneR = if (trueScale || rAu < 1e-6) {
            rAu * SCENE_AU
        } else {
            when {
                rAu <= 1.6 -> rAu * SCENE_AU
                else -> (1.6 + (rAu - 1.6).pow(0.68) * 1.12) * SCENE_AU
            }
        }
        val dir = au.normalized()
        return sunOffset + dir * sceneR.toFloat()
    }

    /** Converts J2000 Equatorial RA/Dec (degrees) to local Horizon Altitude & Azimuth (degrees) with atmospheric refraction. */
    fun equatorialToHorizontal(
        raDeg: Double,
        decDeg: Double,
        latDeg: Double,
        lonDeg: Double,
        epochMillis: Long
    ): HorizontalCoords {
        val t = toAstroTime(epochMillis)
        val obs = Observer(latDeg, lonDeg, 0.0)
        // Astronomy Engine horizon() expects RA in hours [0..24) and Dec in degrees [-90..90]
        val topo = horizon(t, obs, raDeg / 15.0, decDeg, Refraction.Normal)
        return HorizontalCoords(topo.altitude, topo.azimuth)
    }

    /** Topocentric Sun horizontal Altitude & Azimuth with atmospheric refraction & aberration. */
    fun sunHorizontalAtCity(city: CityLocation, epochMillis: Long): HorizontalCoords =
        bodyHorizontalAtCity(CelestialBodyId.SUN, city, epochMillis)

    /** Topocentric Horizontal Alt/Az for any planet, Sun, or Moon via Astronomy Engine (with Refraction.Normal). */
    fun bodyHorizontalAtCity(id: CelestialBodyId, city: CityLocation, epochMillis: Long): HorizontalCoords {
        if (id == CelestialBodyId.EARTH) return HorizontalCoords(-90.0, 0.0)
        val t = toAstroTime(epochMillis)
        val obs = Observer(city.lat, city.lon, 0.0)
        val eq = equator(toAstroBody(id), t, obs, EquatorEpoch.OfDate, Aberration.Corrected)
        val topo = horizon(t, obs, eq.ra, eq.dec, Refraction.Normal)
        return HorizontalCoords(topo.altitude, topo.azimuth)
    }

    /** Exact Lunar Phase Angle, Illumination %, and classical Arabic lunar phase name via Astronomy Engine. */
    fun computeLunarPhase(epochMillis: Long): LunarPhaseInfo {
        val t = toAstroTime(epochMillis)
        val phaseDeg = ((moonPhase(t) % 360.0) + 360.0) % 360.0
        val illum = illumination(Body.Moon, t)
        val pct = (illum.phaseFraction * 100.0).roundToInt().coerceIn(0, 100)
        val nameAr = when {
            phaseDeg < 11.25 || phaseDeg >= 348.75 -> "محاق (قمر جديد)"
            phaseDeg < 78.75 -> "هلال متزايد"
            phaseDeg < 101.25 -> "تربيع أول"
            phaseDeg < 168.75 -> "أحدب متزايد"
            phaseDeg < 191.25 -> "بدر مكتمل"
            phaseDeg < 258.75 -> "أحدب متناقص"
            phaseDeg < 281.25 -> "تربيع أخير"
            else -> "هلال متناقص"
        }
        return LunarPhaseInfo(phaseDeg, pct, nameAr)
    }

    /** Visual magnitude of a body from Earth (via Astronomy Engine illumination()). */
    fun apparentMagnitude(id: CelestialBodyId, epochMillis: Long): Double {
        if (id == CelestialBodyId.EARTH) return 0.0
        return illumination(toAstroBody(id), toAstroTime(epochMillis)).mag
    }

    /** Computes full Earth Sky Observatory snapshot (Moon phase, Sunrise/Sunset, visible planets). */
    fun computeSkyTelemetry(
        city: CityLocation,
        epochMillis: Long,
        cameraAzDeg: Float = 180f,
        cameraAltDeg: Float = 28f
    ): SkyTelemetry {
        val t = toAstroTime(epochMillis)
        val obs = Observer(city.lat, city.lon, 0.0)
        val lunar = computeLunarPhase(epochMillis)
        val sunHz = sunHorizontalAtCity(city, epochMillis)
        val moonHz = bodyHorizontalAtCity(CelestialBodyId.MOON, city, epochMillis)

        // Start searching rise/set from beginning of current day
        val dayStart = toAstroTime(epochMillis - 12L * 3600L * 1000L)
        val riseTime = searchRiseSet(Body.Sun, obs, Direction.Rise, dayStart, 2.0)
        val setTime = searchRiseSet(Body.Sun, obs, Direction.Set, dayStart, 2.0)

        var visibleCount = 0
        for (id in listOf(
            CelestialBodyId.MERCURY, CelestialBodyId.VENUS, CelestialBodyId.MARS,
            CelestialBodyId.JUPITER, CelestialBodyId.SATURN, CelestialBodyId.MOON
        )) {
            if (bodyHorizontalAtCity(id, city, epochMillis).altitudeDeg > 0.0) visibleCount++
        }

        return SkyTelemetry(
            moonPhaseNameAr = lunar.phaseNameAr,
            moonIlluminationPct = lunar.illuminationPercent,
            moonAltitudeDeg = moonHz.altitudeDeg,
            sunAltitudeDeg = sunHz.altitudeDeg,
            nextSunriseLocal = riseTime?.let { formatLocalTime(it.toMillisecondsSince1970(), city.tzId) } ?: "--:--",
            nextSunsetLocal = setTime?.let { formatLocalTime(it.toMillisecondsSince1970(), city.tzId) } ?: "--:--",
            visiblePlanetsCount = visibleCount,
            cameraAzimuthDeg = cameraAzDeg,
            cameraAltitudeDeg = cameraAltDeg
        )
    }

    /** Convert Altitude/Azimuth (deg) to unit sky dome vector (Y = up, -Z = North, +X = East). */
    fun horizontalToUnitVec(altDeg: Double, azDeg: Double): Vec3 {
        val alt = altDeg * DEG
        val az = azDeg * DEG
        val cosAlt = cos(alt)
        val x = (cosAlt * sin(az)).toFloat()
        val y = sin(alt).toFloat()
        val z = (-cosAlt * cos(az)).toFloat()
        return Vec3(x, y, z)
    }

    // ---- Kerr General Relativity Black Hole Physics (units of Schwarzschild radius r_s = 2GM/c²) ----

    /** Event horizon radius r_+ / r_s for dimensionless Kerr spin a* in [0, 0.998]. */
    fun kerrEventHorizon(spinA: Double): Double {
        val a = spinA.coerceIn(0.0, 0.998)
        return 0.5 * (1.0 + sqrt(1.0 - a * a))
    }

    /** Prograde Innermost Stable Circular Orbit (ISCO) radius in units of r_s. */
    fun kerrIsco(spinA: Double): Double {
        val a = spinA.coerceIn(0.0, 0.998)
        val oneMinusA2 = max(1.0 - a * a, 1e-6)
        val z1 = 1.0 + cbrt(oneMinusA2) * (cbrt(1.0 + a) + cbrt(1.0 - a))
        val z2 = sqrt(3.0 * a * a + z1 * z1)
        return 0.5 * (3.0 + z2 - sqrt(max(0.0, (3.0 - z1) * (3.0 + z1 + 2.0 * z2))))
    }

    /** Prograde equatorial photon sphere radius in units of r_s. */
    fun kerrPhotonSphere(spinA: Double): Double {
        val a = spinA.coerceIn(0.0, 0.998)
        return 0.5 * (2.0 * (1.0 + cos((2.0 / 3.0) * acos(-a))))
    }

    /** Gravitational time dilation dτ/dt as percentage [0..100] at radius r (in r_s). */
    fun kerrTimeDilationPercent(radiusRs: Double, spinA: Double): Double {
        val rH = kerrEventHorizon(spinA)
        val ratio = (1.0 - rH / max(radiusRs, rH + 1e-4)).coerceIn(0.0, 1.0)
        return sqrt(ratio) * 100.0
    }

    /** Relativistic escape velocity in units of c [0..1] at radius r (in r_s). */
    fun kerrEscapeVelocityC(radiusRs: Double, spinA: Double): Double {
        val rH = kerrEventHorizon(spinA)
        return sqrt((rH / max(radiusRs, rH + 1e-4)).coerceIn(0.0, 1.0))
    }

    /** Relativistic precessing rosette orbit for star S2 around Sagittarius A*. */
    fun s2StarPosition(phaseRad: Double): Vec3 {
        val a = 22.0
        val e = 0.75
        var E = phaseRad
        for (i in 0 until 5) {
            E -= (E - e * sin(E) - phaseRad) / (1.0 - e * cos(E))
        }
        val nu = 2.0 * atan2(sqrt(1.0 + e) * sin(E / 2.0), sqrt(1.0 - e) * cos(E / 2.0))
        val r = a * (1.0 - e * cos(E))
        val precessed = nu + phaseRad * 0.055
        return Vec3(
            (r * cos(precessed)).toFloat(),
            (r * sin(precessed) * sin(0.42)).toFloat(),
            (r * sin(precessed) * cos(0.42)).toFloat()
        )
    }

    fun computeBodyTelemetry(id: CelestialBodyId, epochMillis: Long): BodyTelemetry {
        val spec = SolarCatalog.byId.getValue(id)
        val pos = heliocentricAu(id, epochMillis)
        val earth = heliocentricAu(CelestialBodyId.EARTH, epochMillis)
        val distSunAu = pos.length().toDouble()
        val distEarthAu = (pos - earth).length().toDouble()
        val distSunKm = distSunAu * AU_KM
        val distEarthKm = distEarthAu * AU_KM

        val speedKms = when (id) {
            CelestialBodyId.SUN -> GALACTIC_SPEED_KMS
            CelestialBodyId.MOON -> 1.022
            else -> {
                val rKm = max(distSunKm, 1e6)
                val aKm = spec.semiMajorAxisAu * AU_KM
                sqrt(max(SolarCatalog.byId.getValue(CelestialBodyId.SUN).gmKm3s2 * (2.0 / rKm - 1.0 / aKm), 0.0))
            }
        }
        val lightMin = (distSunKm / C_KMS) / 60.0
        val lightStr = when {
            id == CelestialBodyId.SUN -> "مصدر الضوء"
            lightMin >= 60.0 -> String.format(Locale.US, "%.2f ساعة ضوئية", lightMin / 60.0)
            else -> String.format(Locale.US, "%.2f دقيقة ضوئية", lightMin)
        }
        val vOverC = speedKms / C_KMS
        val gammaInv = sqrt(max(1.0 - vOverC * vOverC, 0.0))
        val magStr = if (id == CelestialBodyId.EARTH) {
            "كوكبك الأم"
        } else {
            val illum = illumination(toAstroBody(id), toAstroTime(epochMillis))
            String.format(Locale.US, "%.2f mag · %.0f%%", illum.mag, illum.phaseFraction * 100.0)
        }

        return BodyTelemetry(
            titleAr = spec.nameAr,
            subtitle = "${spec.nameEn} · نصف القطر ${formatKmAr(spec.radiusKm)}",
            distanceFromSunKm = if (id == CelestialBodyId.SUN) "مركز المنظومة" else formatKmAr(distSunKm),
            distanceFromEarthKm = if (id == CelestialBodyId.EARTH) "0 كم (موقعك)" else formatKmAr(distEarthKm),
            orbitalSpeedKms = String.format(Locale.US, "%.2f كم/ث", speedKms),
            lightTimeFromSun = lightStr,
            surfaceTemp = spec.surfaceTempC,
            gravity = spec.gravityMs2,
            extraRelativistic = String.format(Locale.US, "معامل لورنتز: %.8f", gammaInv),
            apparentMag = magStr
        )
    }

    fun formatKmAr(km: Double): String = when {
        km >= 1e9 -> String.format(Locale.US, "%.2f مليار كم", km / 1e9)
        km >= 1e6 -> String.format(Locale.US, "%.2f مليون كم", km / 1e6)
        else -> String.format(Locale.US, "%,.0f كم", km)
    }

    fun formatUtcClock(epochMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd  HH:mm:ss 'UTC'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(epochMillis))
    }

    fun formatLocalTime(epochMillis: Long, tzId: String): String {
        return try {
            val sdf = SimpleDateFormat("HH:mm", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone(tzId)
            sdf.format(Date(epochMillis))
        } catch (_: Exception) {
            "00:00"
        }
    }

    fun formatMissionClock(seconds: Double): String {
        val total = max(0, seconds.toInt())
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        val tenths = ((seconds - total) * 10).toInt().coerceIn(0, 9)
        return String.format(Locale.US, "%02d:%02d:%02d.%d", h, m, s, tenths)
    }

    fun bvToRgb(bv: Float): Triple<Float, Float, Float> {
        val t = bv.coerceIn(-0.4f, 2.0f)
        val k = 46.0 * (1.0 / (0.92 * t + 1.7) + 1.0 / (0.92 * t + 0.62))
        val r = (if (k <= 66) 255.0 else 329.7 * (k - 60.0).pow(-0.133)).coerceIn(0.0, 255.0) / 255.0
        val g = (if (k <= 66) 99.5 * kotlin.math.ln(max(k, 1.0)) - 161.1 else 288.1 * (k - 60.0).pow(-0.075)).coerceIn(0.0, 255.0) / 255.0
        val b = (if (k >= 66) 255.0 else if (k <= 19) 0.0 else 138.5 * kotlin.math.ln(max(k - 10.0, 1.0)) - 305.0).coerceIn(0.0, 255.0) / 255.0
        val m = max(r, max(g, b)).coerceAtLeast(1e-3)
        return Triple(
            (0.35 + 0.65 * (r / m)).toFloat(),
            (0.35 + 0.65 * (g / m)).toFloat(),
            (0.35 + 0.65 * (b / m)).toFloat()
        )
    }
}
