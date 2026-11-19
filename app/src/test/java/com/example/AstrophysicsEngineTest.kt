package com.example

import com.example.domain.AstrophysicsEngine
import com.example.domain.CelestialBodyId
import com.example.domain.CityCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AstrophysicsEngineTest {

    private val j2000EpochMillis = 946_728_000_000L // 2000-01-01T12:00:00Z
    private val sample2026EpochMillis = 1_774_000_000_000L // 2026-03-20

    @Test
    fun `schwarzschild limit matches exact general relativity constants`() {
        assertEquals(1.0, AstrophysicsEngine.kerrEventHorizon(0.0), 1e-6)
        assertEquals(3.0, AstrophysicsEngine.kerrIsco(0.0), 1e-4)
        assertEquals(1.5, AstrophysicsEngine.kerrPhotonSphere(0.0), 1e-4)
    }

    @Test
    fun `kerr spinning black hole shrinks event horizon isco and photon sphere monotonically`() {
        val rH0 = AstrophysicsEngine.kerrEventHorizon(0.0)
        val rHHigh = AstrophysicsEngine.kerrEventHorizon(0.95)
        val isco0 = AstrophysicsEngine.kerrIsco(0.0)
        val iscoHigh = AstrophysicsEngine.kerrIsco(0.95)
        val ph0 = AstrophysicsEngine.kerrPhotonSphere(0.0)
        val phHigh = AstrophysicsEngine.kerrPhotonSphere(0.95)

        assertTrue("Event horizon must shrink with spin", rHHigh < rH0 && rHHigh > 0.5)
        assertTrue("Prograde ISCO must shrink with spin", iscoHigh < isco0 && iscoHigh > 0.5)
        assertTrue("Prograde photon sphere must shrink with spin", phHigh < ph0 && phHigh > 0.5)

        // Clamping negative and extreme spin values (boundary/negative check)
        assertEquals(1.0, AstrophysicsEngine.kerrEventHorizon(-2.5), 1e-6)
        assertTrue(AstrophysicsEngine.kerrEventHorizon(5.0) >= 0.5)
    }

    @Test
    fun `gravitational time dilation and escape velocity respect physical bounds`() {
        val spin = 0.90
        val rH = AstrophysicsEngine.kerrEventHorizon(spin)

        val farClock = AstrophysicsEngine.kerrTimeDilationPercent(100.0, spin)
        val nearClock = AstrophysicsEngine.kerrTimeDilationPercent(rH * 1.1, spin)
        val atHorizonClock = AstrophysicsEngine.kerrTimeDilationPercent(rH * 0.5, spin)

        assertTrue("Far clock approaches 100%", farClock > 99.0 && farClock <= 100.0)
        assertTrue("Near-horizon clock slows dramatically", nearClock < 40.0 && nearClock > 0.0)
        assertTrue("Inside/at horizon clock freezes near 0%", atHorizonClock < 2.0)

        val farEsc = AstrophysicsEngine.kerrEscapeVelocityC(100.0, spin)
        val nearEsc = AstrophysicsEngine.kerrEscapeVelocityC(rH * 1.05, spin)
        assertTrue("Far escape velocity is small", farEsc < 0.15)
        assertTrue("Near-horizon escape velocity approaches c", nearEsc > 0.95 && nearEsc <= 1.0)
    }

    @Test
    fun `keplerian ephemeris yields accurate orbital radii for solar system bodies`() {
        val sun = AstrophysicsEngine.heliocentricAu(CelestialBodyId.SUN, sample2026EpochMillis)
        assertEquals(0f, sun.length(), 1e-6f)

        val earthR = AstrophysicsEngine.heliocentricAu(CelestialBodyId.EARTH, sample2026EpochMillis).length()
        assertTrue("Earth distance must be ~1 AU (got $earthR)", earthR in 0.98f..1.02f)

        val marsR = AstrophysicsEngine.heliocentricAu(CelestialBodyId.MARS, sample2026EpochMillis).length()
        assertTrue("Mars distance must be 1.38..1.67 AU (got $marsR)", marsR in 1.37f..1.68f)

        val jupiterR = AstrophysicsEngine.heliocentricAu(CelestialBodyId.JUPITER, sample2026EpochMillis).length()
        assertTrue("Jupiter distance must be 4.95..5.46 AU (got $jupiterR)", jupiterR in 4.90f..5.50f)

        val earthPos = AstrophysicsEngine.heliocentricAu(CelestialBodyId.EARTH, sample2026EpochMillis)
        val moonPos = AstrophysicsEngine.heliocentricAu(CelestialBodyId.MOON, sample2026EpochMillis)
        val earthMoonAu = (moonPos - earthPos).length()
        assertTrue("Moon distance from Earth ~0.00257 AU (got $earthMoonAu)", earthMoonAu in 0.0022f..0.0029f)
    }

    @Test
    fun `helical solar offset is zero when disabled and advances linearly when enabled`() {
        val zero = AstrophysicsEngine.helicalSunOffset(sample2026EpochMillis, j2000EpochMillis, enabled = false)
        assertEquals(0f, zero.length(), 1e-6f)

        val oneYearMs = (365.25 * 86_400_000.0).toLong()
        val drift1 = AstrophysicsEngine.helicalSunOffset(j2000EpochMillis + oneYearMs, j2000EpochMillis, enabled = true)
        val drift2 = AstrophysicsEngine.helicalSunOffset(j2000EpochMillis + 2 * oneYearMs, j2000EpochMillis, enabled = true)
        assertTrue("Helical drift advances over 1 year", drift1.length() > 300f)
        assertEquals(drift1.length() * 2f, drift2.length(), 1e-2f)
    }

    @Test
    fun `horizontal sky coordinates and unit vector conversion remain valid`() {
        val riyadh = CityCatalog.cities.first { it.id == "riyadh" }
        val sunHoriz = AstrophysicsEngine.sunHorizontalAtCity(riyadh, sample2026EpochMillis)
        assertTrue("Altitude within [-90, 90]", sunHoriz.altitudeDeg in -90.0..90.0)
        assertTrue("Azimuth within [0, 360]", sunHoriz.azimuthDeg in 0.0..360.0)

        val vec = AstrophysicsEngine.horizontalToUnitVec(sunHoriz.altitudeDeg, sunHoriz.azimuthDeg)
        assertEquals(1.0f, vec.length(), 1e-5f)

        // North horizon check: alt=0, az=0 => (0, 0, -1)
        val north = AstrophysicsEngine.horizontalToUnitVec(0.0, 0.0)
        assertTrue(abs(north.x) < 1e-5f && abs(north.y) < 1e-5f && abs(north.z + 1f) < 1e-5f)
    }

    @Test
    fun `s2 relativistic rosette orbit stays bounded and smooth`() {
        val p0 = AstrophysicsEngine.s2StarPosition(0.0)
        val pPi = AstrophysicsEngine.s2StarPosition(Math.PI)
        assertTrue("Periapsis is much closer than apoapsis", p0.length() < pPi.length())
        assertTrue("Periapsis > 4 Rs", p0.length() > 4f)
        assertTrue("Apoapsis < 42 Rs", pPi.length() < 42f)
    }

    @Test
    fun `astronomy engine computes valid lunar phase sunrise sunset and apparent magnitudes`() {
        val (phaseDeg, illumPct, phaseAr) = AstrophysicsEngine.computeLunarPhase(sample2026EpochMillis)
        assertTrue("Lunar phase angle in [0, 360)", phaseDeg in 0.0..360.0)
        assertTrue("Lunar illumination in [0, 100]", illumPct in 0..100)
        assertTrue("Arabic lunar phase name is populated", phaseAr.isNotBlank())

        val mecca = CityCatalog.cities.first { it.id == "mecca" }
        val sky = AstrophysicsEngine.computeSkyTelemetry(mecca, sample2026EpochMillis, 180f, 35f)
        assertTrue("Sunrise formatted HH:mm (got ${sky.nextSunriseLocal})", sky.nextSunriseLocal.contains(":"))
        assertTrue("Sunset formatted HH:mm (got ${sky.nextSunsetLocal})", sky.nextSunsetLocal.contains(":"))
        assertEquals(180f, sky.cameraAzimuthDeg, 1e-4f)
        assertEquals(35f, sky.cameraAltitudeDeg, 1e-4f)

        val sunMag = AstrophysicsEngine.apparentMagnitude(CelestialBodyId.SUN, sample2026EpochMillis)
        assertTrue("Sun apparent magnitude ~ -26.7 (got $sunMag)", sunMag in -27.2..-26.3)

        val venusMag = AstrophysicsEngine.apparentMagnitude(CelestialBodyId.VENUS, sample2026EpochMillis)
        assertTrue("Venus apparent magnitude bright (got $venusMag)", venusMag in -5.0..-3.0)

        assertEquals("00:01:05.0", AstrophysicsEngine.formatMissionClock(65.0))
    }

    @Test
    fun `helical trail cache maintains C0 continuity and exact live head welding across 60fps frames`() {
        val cache = com.example.engine.HelicalTrailCache()
        val refEpoch = sample2026EpochMillis.toDouble()
        val buf = FloatArray(160 * 4)
        val segs = 96
        // Simulate 40 consecutive 60 FPS frames at 30 days/sec (~0.5 day per frame, crossing multiple buckets!)
        val frameStepMs = 0.5 * 86_400_000.0
        var prevTail = com.example.domain.Vec3(0f, 0f, 0f)
        var prevHead = com.example.domain.Vec3(0f, 0f, 0f)

        for (frame in 0 until 40) {
            val t = refEpoch + frame * frameStepMs
            val liveEarth = AstrophysicsEngine.scenePositionContinuous(
                CelestialBodyId.EARTH, t, true, refEpoch, false
            )
            val count = cache.fillTrailPoints(
                id = CelestialBodyId.EARTH,
                epochMs = t,
                helicalRefEpochMs = refEpoch,
                trueScale = false,
                segments = segs,
                livePos = liveEarth,
                outPoints4 = buf
            )
            cache.commitFrame(t)

            assertEquals("Expected segs + 2 vertices", segs + 2, count)
            val tail = com.example.domain.Vec3(buf[0], buf[1], buf[2])
            val headIdx = (count - 1) * 4
            val head = com.example.domain.Vec3(buf[headIdx], buf[headIdx + 1], buf[headIdx + 2])

            // Head must be welded identically to liveEarth with 0.000 gap
            assertEquals(0f, (head - liveEarth).length(), 1e-4f)
            assertEquals(0f, buf[3], 1e-4f)
            assertEquals(1f, buf[headIdx + 3], 1e-4f)

            if (frame > 0) {
                val headDelta = (head - prevHead).length()
                val tailDelta = (tail - prevTail).length()
                assertTrue("Planet advances smoothly every frame (headDelta=$headDelta)", headDelta in 0.5f..5.0f)
                assertTrue("Tail glides smoothly without bucket snapping (tailDelta=$tailDelta)", tailDelta in 0.5f..5.0f)
            }
            prevTail = tail
            prevHead = head
        }
    }
}
