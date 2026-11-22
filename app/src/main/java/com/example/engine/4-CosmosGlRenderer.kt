package com.example.engine

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import androidx.compose.ui.graphics.Color
import com.example.data.PlanetTexKey
import com.example.domain.AstrophysicsEngine
import com.example.domain.BlackHoleTelemetry
import com.example.domain.BodyKind
import com.example.domain.CelestialBodyId
import com.example.domain.CosmosMode
import com.example.domain.CosmosUiState
import com.example.domain.ProjectedLabel
import com.example.domain.SolarCatalog
import com.example.domain.TimeSpeeds
import com.example.domain.Vec3
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class CosmosGlRenderer(
    private val onFrameTelemetry: (
        bhTelemetry: BlackHoleTelemetry,
        labels: List<ProjectedLabel>,
        cameraYawRad: Float,
        skyAzDeg: Float,
        skyAltDeg: Float
    ) -> Unit
) : GLSurfaceView.Renderer {

    @Volatile private var uiState: CosmosUiState = CosmosUiState()
    @Volatile private var pendingFlyTrigger = 0L
    @Volatile private var pendingResetTrigger = 0L

    private val pendingBitmaps = ConcurrentLinkedQueue<Pair<PlanetTexKey, Bitmap>>()
    private val glTextures = HashMap<PlanetTexKey, Int>()
    private val texMixProgress = HashMap<PlanetTexKey, Float>()
    private var whiteTex = 0

    // Shader programs
    private lateinit var bhProg: GlProgram
    private lateinit var spaceBgProg: GlProgram
    private lateinit var planetProg: GlProgram
    private lateinit var atmoProg: GlProgram
    private lateinit var cloudProg: GlProgram
    private lateinit var ringProg: GlProgram
    private lateinit var sunProg: GlProgram
    private lateinit var coronaProg: GlProgram
    private lateinit var ribbonProg: GlProgram
    private lateinit var bowShockProg: GlProgram
    private lateinit var streakProg: GlProgram
    private lateinit var galaxyDiscProg: GlProgram
    private lateinit var skyDomeProg: GlProgram
    private lateinit var starsProg: GlProgram
    private lateinit var galaxyProg: GlProgram
    private lateinit var asteroidProg: GlProgram
    private lateinit var dustProg: GlProgram
    private lateinit var spacetimeProg: GlProgram
    private lateinit var trailProg: GlProgram
    private lateinit var hdrPipeline: HdrBloomPipeline

    // Meshes
    private lateinit var fsMesh: MeshVao
    private lateinit var quadMesh: MeshVao
    private lateinit var sphereHi: MeshVao
    private lateinit var sphereLo: MeshVao
    private lateinit var saturnRingMesh: MeshVao
    private lateinit var uranusRingMesh: MeshVao
    private lateinit var spacetimeMesh: MeshVao
    private lateinit var starfieldMesh: MeshVao
    private lateinit var galaxyMesh: MeshVao
    private lateinit var galaxyDiscMesh: MeshVao
    private lateinit var bowShockMesh: MeshVao
    private lateinit var streakMesh: MeshVao
    private lateinit var asteroidMesh: MeshVao
    private lateinit var dustMesh: MeshVao
    private lateinit var sharedRibbonMesh: MeshVao
    private lateinit var constellationMesh: MeshVao

    // Cached static orbit polylines (x, y, z, prog) per planet
    private val orbitPointCache = HashMap<CelestialBodyId, FloatArray>()

    // Camera state
    private var viewportW = 1080
    private var viewportH = 1920
    private var pixelRatio = 2.0f

    private var orbitTheta = 0.72f
    private var orbitPhi = 1.24f
    private var orbitRadius = 4.6f
    private var targetTheta = 0.72f
    private var targetPhi = 1.24f
    private var targetRadius = 4.6f
    private var orbitVelTheta = 0f
    private var orbitVelPhi = 0f
    private var camTarget = Vec3(0f, 0f, 0f)
    private var initialCameraAligned = false

    // Dual-phase camera focus tracking (zero lag when locked, quintic flight on body switch)
    private var prevFocusBody: CelestialBodyId = CelestialBodyId.EARTH
    private var prevFocusHelicalOverview: Boolean = false
    private var focusTransitionT = 1f

    // Black Hole dedicated orbital camera (in Schwarzschild radii r_s) + Dual Relativistic Clocks
    private var bhTheta = 0.38f
    private var bhPhi = 1.38f
    private var bhRadius = 14.0f
    private var bhTargetTheta = 0.38f
    private var bhTargetPhi = 1.38f
    private var bhTargetRadius = 14.0f
    private var bhVelTheta = 0f
    private var bhVelPhi = 0f
    private var s2Phase = 0.0
    private var bhShipClockSec = 0.0
    private var bhEarthClockSec = 0.0

    // Earth Sky perspective camera (azimuth deg, altitude deg)
    private var skyAzimuthDeg = 180f
    private var skyAltitudeDeg = 22f
    private var skyTargetAz = 180f
    private var skyTargetAlt = 22f
    private var skyVelAz = 0f
    private var skyVelAlt = 0f

    // Continuous 60/120 FPS render-thread simulation clock (decoupled from 5 Hz Compose UI ticks)
    private var renderEpochMs: Double = System.currentTimeMillis().toDouble()
    private var clockInitialized = false
    private var lastClockSyncVersion = -1L
    private var smoothSpeedMult = 0.0
    private var smoothDt = 1f / 60f

    private var lastMode: CosmosMode = CosmosMode.SPACE
    private var lastBody: CelestialBodyId = CelestialBodyId.EARTH
    private var lastTrueScale: Boolean = false
    private var lastFrameNs = System.nanoTime()
    private var elapsedSec = 0f
    private var visualSpinAngle = 0.0
    private var helicalRefEpoch: Double = System.currentTimeMillis().toDouble()
    private var lastOrbitRebuildEpoch = 0L
    private var lastTelemetryEmitNs = 0L
    private val helicalTrailCache = HelicalTrailCache()

    // Adaptive GPU performance governor (eliminates EGL fence stalls on virtualized/software GPUs)
    private var isSoftwareGpu = false
    private var emaFrameMs = 16.6f
    private var dynamicScale = 1.0f
    private var lastScaleAdjustNs = 0L

    // Reusable matrices & scratch buffers (zero per-frame heap churn)
    private val projM = FloatArray(16)
    private val viewM = FloatArray(16)
    private val vpM = FloatArray(16)
    private val modelM = FloatArray(16)
    private val mvpM = FloatArray(16)
    private val invModelM = FloatArray(16)
    private val cloudM = FloatArray(16)
    private val tmpIn4 = FloatArray(4)
    private val tmpOut4 = FloatArray(4)
    private val spacetimePacked = FloatArray(8 * 4)
    private val helicalPointsBuf = FloatArray(340 * 4)
    private val s2PointsBuf = FloatArray(150 * 4)
    private val ribbonVertsScratch = FloatArray(360 * 2 * 5)
    private val constellationBuf = FloatArray(64 * 4)
    private val skyPlanetDirs = FloatArray(5 * 4)
    private val skyPlanetCols = FloatArray(5 * 3)
    private val bodyWorldPositions = HashMap<CelestialBodyId, Vec3>()

    private val skyPlanetIds = listOf(
        CelestialBodyId.MERCURY,
        CelestialBodyId.VENUS,
        CelestialBodyId.MARS,
        CelestialBodyId.JUPITER,
        CelestialBodyId.SATURN
    )

    fun updateState(state: CosmosUiState) {
        uiState = state
    }

    fun enqueueTexture(key: PlanetTexKey, bitmap: Bitmap) {
        pendingBitmaps.add(key to bitmap)
    }

    fun applyGyroOrientation(azimuthDeg: Float, altitudeDeg: Float) {
        if (uiState.mode == CosmosMode.EARTH_SKY && uiState.gyroscopeEnabled) {
            skyTargetAz = azimuthDeg
            skyTargetAlt = altitudeDeg.coerceIn(-12f, 88f)
        }
    }

    fun onDrag(dx: Float, dy: Float) {
        val sens = 0.0034f
        val dAz = -dx * 0.16f
        val dAlt = dy * 0.14f
        when (uiState.mode) {
            CosmosMode.EARTH_SKY -> {
                skyTargetAz = ((skyTargetAz + dAz) + 360f) % 360f
                skyTargetAlt = (skyTargetAlt + dAlt).coerceIn(-12f, 88f)
                skyVelAz = (skyVelAz * 0.45f + dAz * 26f).coerceIn(-160f, 160f)
                skyVelAlt = (skyVelAlt * 0.45f + dAlt * 22f).coerceIn(-110f, 110f)
            }
            CosmosMode.BLACK_HOLE -> {
                val dT = -dx * sens
                val dP = -dy * sens
                bhTargetTheta += dT
                bhTargetPhi = (bhTargetPhi + dP).coerceIn(0.18f, 2.94f)
                bhVelTheta = (bhVelTheta * 0.45f + dT * 24f).coerceIn(-4.2f, 4.2f)
                bhVelPhi = (bhVelPhi * 0.45f + dP * 20f).coerceIn(-2.8f, 2.8f)
            }
            else -> {
                val dT = -dx * sens
                val dP = -dy * sens
                targetTheta += dT
                targetPhi = (targetPhi + dP).coerceIn(0.12f, 3.02f)
                orbitVelTheta = (orbitVelTheta * 0.45f + dT * 16f).coerceIn(-3.2f, 3.2f)
                orbitVelPhi = (orbitVelPhi * 0.45f + dP * 13f).coerceIn(-2.0f, 2.0f)
            }
        }
    }

    fun onPinchZoom(scaleFactor: Float) {
        orbitVelTheta = 0f; orbitVelPhi = 0f
        bhVelTheta = 0f; bhVelPhi = 0f
        val inv = 1f / scaleFactor.coerceIn(0.6f, 1.6f)
        when (uiState.mode) {
            CosmosMode.BLACK_HOLE -> {
                val minR = (AstrophysicsEngine.kerrEventHorizon(uiState.blackHole.spinA.toDouble()) * 1.18).toFloat()
                bhTargetRadius = (bhTargetRadius * inv).coerceIn(minR, 42f)
            }
            CosmosMode.GALAXY -> {
                targetRadius = (targetRadius * inv).coerceIn(22f, 280f)
            }
            CosmosMode.EARTH_SKY -> Unit
            CosmosMode.HELICAL -> {
                targetRadius = (targetRadius * inv).coerceIn(38f, 1800f)
            }
            CosmosMode.SPACE -> {
                val spec = SolarCatalog.byId.getValue(uiState.selectedBody)
                val minR = (spec.visualRadius * uiState.studio.planetScale * 1.12f).coerceAtLeast(0.8f)
                targetRadius = (targetRadius * inv).coerceIn(minR, 2400f)
            }
        }
    }

    fun onDoubleTap() {
        orbitVelTheta = 0f; orbitVelPhi = 0f
        bhVelTheta = 0f; bhVelPhi = 0f
        skyVelAz = 0f; skyVelAlt = 0f
        when (uiState.mode) {
            CosmosMode.BLACK_HOLE -> {
                bhTargetTheta = 0.38f; bhTargetPhi = 1.38f; bhTargetRadius = 13.5f
            }
            CosmosMode.GALAXY -> {
                targetTheta = 0.65f; targetPhi = 0.90f; targetRadius = 128f
            }
            CosmosMode.EARTH_SKY -> {
                skyTargetAz = 180f; skyTargetAlt = 24f
            }
            CosmosMode.HELICAL -> {
                targetTheta = 0.62f; targetPhi = 1.16f; targetRadius = 335f
            }
            CosmosMode.SPACE -> {
                val spec = SolarCatalog.byId.getValue(uiState.selectedBody)
                targetRadius = (spec.visualRadius * uiState.studio.planetScale * 3.35f).coerceAtLeast(2.4f)
                targetPhi = 1.24f
                targetTheta = computeSunlitHeroTheta(uiState.selectedBody, renderEpochMs)
            }
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val rendererStr = (GLES30.glGetString(GLES30.GL_RENDERER) ?: "").lowercase()
        val vendorStr = (GLES30.glGetString(GLES30.GL_VENDOR) ?: "").lowercase()
        isSoftwareGpu = rendererStr.contains("swiftshader") ||
            rendererStr.contains("emulator") ||
            rendererStr.contains("llvmpipe") ||
            rendererStr.contains("softpipe") ||
            rendererStr.contains("goldfish") ||
            rendererStr.contains("cuttlefish") ||
            rendererStr.contains("android emulator") ||
            (vendorStr.contains("google") && rendererStr.contains("angle"))
        dynamicScale = if (isSoftwareGpu) 0.64f else 1.0f

        GLES30.glCullFace(GLES30.GL_BACK)
        GLES30.glFrontFace(GLES30.GL_CCW)

        whiteTex = GlUtils.createWhiteTexture()

        bhProg = GlUtils.compileProgram(ShaderPrograms.FULLSCREEN_VS, ShaderPrograms.BLACK_HOLE_FS)
        spaceBgProg = GlUtils.compileProgram(ShaderPrograms.FULLSCREEN_VS, VortexAndSkyShaders.SPACE_BG_FS)
        planetProg = GlUtils.compileProgram(ShaderPrograms.PLANET_VS, ShaderPrograms.PLANET_FS)
        atmoProg = GlUtils.compileProgram(ShaderPrograms.ATMO_VS, ShaderPrograms.ATMO_FS)
        cloudProg = GlUtils.compileProgram(ShaderPrograms.PLANET_VS, ShaderPrograms.CLOUD_FS)
        ringProg = GlUtils.compileProgram(ShaderPrograms.RING_VS, ShaderPrograms.RING_FS)
        sunProg = GlUtils.compileProgram(ShaderPrograms.SUN_VS, ShaderPrograms.SUN_FS)
        coronaProg = GlUtils.compileProgram(ShaderPrograms.CORONA_VS, ShaderPrograms.CORONA_FS)
        ribbonProg = GlUtils.compileProgram(VortexAndSkyShaders.RIBBON_VS, VortexAndSkyShaders.RIBBON_FS)
        bowShockProg = GlUtils.compileProgram(VortexAndSkyShaders.BOWSHOCK_VS, VortexAndSkyShaders.BOWSHOCK_FS)
        streakProg = GlUtils.compileProgram(VortexAndSkyShaders.STREAK_VS, VortexAndSkyShaders.STREAK_FS)
        galaxyDiscProg = GlUtils.compileProgram(VortexAndSkyShaders.GALAXY_DISC_VS, VortexAndSkyShaders.GALAXY_DISC_FS)
        skyDomeProg = GlUtils.compileProgram(ShaderPrograms.FULLSCREEN_VS, VortexAndSkyShaders.EARTH_SKY_FS)
        starsProg = GlUtils.compileProgram(VortexAndSkyShaders.STARS_VS, VortexAndSkyShaders.STARS_FS)
        galaxyProg = GlUtils.compileProgram(VortexAndSkyShaders.GALAXY_VS, VortexAndSkyShaders.STARS_FS)
        asteroidProg = GlUtils.compileProgram(VortexAndSkyShaders.ASTEROID_VS, VortexAndSkyShaders.ASTEROID_FS)
        dustProg = GlUtils.compileProgram(VortexAndSkyShaders.DUST_VS, VortexAndSkyShaders.DUST_FS)
        spacetimeProg = GlUtils.compileProgram(VortexAndSkyShaders.SPACETIME_VS, VortexAndSkyShaders.SPACETIME_FS)
        trailProg = GlUtils.compileProgram(VortexAndSkyShaders.TRAIL_VS, VortexAndSkyShaders.TRAIL_FS)

        val preProg = GlUtils.compileProgram(ShaderPrograms.FULLSCREEN_VS, ShaderPrograms.BLOOM_PRE_FS)
        val blurProg = GlUtils.compileProgram(ShaderPrograms.FULLSCREEN_VS, ShaderPrograms.BLOOM_BLUR_FS)
        val compProg = GlUtils.compileProgram(ShaderPrograms.FULLSCREEN_VS, ShaderPrograms.COMPOSITE_FS)

        fsMesh = GlGeometry.createFullscreenTriangle()
        quadMesh = GlGeometry.createBillboardQuad()
        sphereHi = GlGeometry.createSphere(if (isSoftwareGpu) 44 else 64, if (isSoftwareGpu) 30 else 42)
        sphereLo = GlGeometry.createSphere(24, 16)
        saturnRingMesh = GlGeometry.createRing(1.236f, 2.327f, 128)
        uranusRingMesh = GlGeometry.createRing(1.62f, 2.04f, 96)
        spacetimeMesh = GlGeometry.createSpacetimePlane(3600f, if (isSoftwareGpu) 56 else 76)
        starfieldMesh = GlGeometry.createStarfield(18_000f)
        galaxyMesh = GlGeometry.createMilkyWayGalaxy(if (isSoftwareGpu) 9_500 else 16_000)
        galaxyDiscMesh = GlGeometry.createGalaxyDiscQuad(118f)
        bowShockMesh = GlGeometry.createBowShockParaboloid(44, 18)
        streakMesh = GlGeometry.createVelocityStreaks(if (isSoftwareGpu) 180 else 280)
        asteroidMesh = GlGeometry.createAsteroidBelt(if (isSoftwareGpu) 2000 else 3400)
        dustMesh = GlGeometry.createCosmicDust(if (isSoftwareGpu) 700 else 1100)
        sharedRibbonMesh = GlGeometry.createDynamicRibbon(360)
        constellationMesh = GlGeometry.createDynamicTrail(256)

        hdrPipeline = HdrBloomPipeline(fsMesh, preProg, blurProg, compProg)
        rebuildStaticOrbits(uiState.simEpochMillis, uiState.settings.trueScale)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportW = width.coerceAtLeast(2)
        viewportH = height.coerceAtLeast(2)
        pixelRatio = (min(viewportW, viewportH) / 420f).coerceIn(1.2f, 2.6f)
        applyAdaptivePipelineResize(uiState)
    }

    private fun applyAdaptivePipelineResize(st: CosmosUiState) {
        val effScale = (st.settings.quality.resolutionScale * dynamicScale).coerceIn(0.55f, 1.0f)
        val maxEdge = if (isSoftwareGpu) 960 else 1920
        hdrPipeline.resize(
            screenWidth = viewportW,
            screenHeight = viewportH,
            scale = effScale,
            maxLongEdgePx = maxEdge,
            preferHalfFloat = !isSoftwareGpu
        )
    }

    /** Computes a dramatic 3/4 golden-terminator hero azimuth so planets NEVER open on their dark backside. */
    private fun computeSunlitHeroTheta(bodyId: CelestialBodyId, epochMillis: Double): Float {
        if (bodyId == CelestialBodyId.SUN) return 0.72f
        val sunPos = Vec3(0f, 0f, 0f)
        val bodyPos = AstrophysicsEngine.scenePositionContinuous(bodyId, epochMillis, false, helicalRefEpoch, false)
        val toSun = sunPos - bodyPos
        val sunAz = atan2(toSun.z.toDouble(), toSun.x.toDouble()).toFloat()
        // Place camera ~52° off the sub-solar line for crisp lit continents + twilight terminator + glowing night cities!
        return sunAz + 0.90f
    }

    override fun onDrawFrame(gl: GL10?) {
        val frameStartNs = System.nanoTime()
        val rawIntervalMs = (frameStartNs - lastFrameNs) * 1e-6f
        val rawDt = (rawIntervalMs * 1e-3f).coerceIn(0.002f, 0.055f)
        lastFrameNs = frameStartNs
        // Stabilize noisy VSync / emulator frame deltas so planetary motion never pulses
        smoothDt = if (rawIntervalMs > 120f) 1f / 60f else (smoothDt * 0.72f + rawDt * 0.28f).coerceIn(0.004f, 0.045f)
        val dt = smoothDt
        elapsedSec += dt

        if (rawIntervalMs in 1f..800f) {
            emaFrameMs = emaFrameMs * 0.84f + rawIntervalMs * 0.16f
        }
        if (frameStartNs - lastScaleAdjustNs > 1_200_000_000L) {
            val maxScale = if (isSoftwareGpu) 0.72f else 1.0f
            val minScale = if (isSoftwareGpu) 0.42f else 0.55f
            if (emaFrameMs > 46f && dynamicScale > minScale) {
                dynamicScale = (dynamicScale - 0.10f).coerceAtLeast(minScale)
                lastScaleAdjustNs = frameStartNs
            } else if (emaFrameMs < 20f && dynamicScale < maxScale) {
                dynamicScale = (dynamicScale + 0.08f).coerceAtMost(maxScale)
                lastScaleAdjustNs = frameStartNs
            }
        }

        val st = uiState
        // Continuous 60/120 FPS render-thread simulation clock (decoupled from 5 Hz Compose UI ticks)
        val baseSpeed = TimeSpeeds.options[st.speedIndex.coerceIn(TimeSpeeds.options.indices)].multiplierSecondsPerSecond
        val targetSpeedMult = if (st.isPlaying) baseSpeed * (if (st.isReversed) -1.0 else 1.0) else 0.0
        if (!clockInitialized || st.clockSyncVersion != lastClockSyncVersion) {
            clockInitialized = true
            lastClockSyncVersion = st.clockSyncVersion
            renderEpochMs = st.simEpochMillis.toDouble()
            smoothSpeedMult = targetSpeedMult
            helicalTrailCache.invalidate()
        } else {
            // Smooth exponential acceleration/braking for cinematic inertia on Play/Pause/Speed/Reverse
            val speedRamp = 1.0 - exp(-10.5 * dt.toDouble())
            smoothSpeedMult += (targetSpeedMult - smoothSpeedMult) * speedRamp
            if (abs(smoothSpeedMult) < 1e-4 && targetSpeedMult == 0.0) smoothSpeedMult = 0.0
            renderEpochMs += dt.toDouble() * smoothSpeedMult * 1000.0
        }

        if (!initialCameraAligned) {
            initialCameraAligned = true
            val heroTheta = computeSunlitHeroTheta(st.selectedBody, renderEpochMs)
            orbitTheta = heroTheta
            targetTheta = heroTheta
            prevFocusBody = st.selectedBody
            val focus = AstrophysicsEngine.scenePositionContinuous(
                st.selectedBody, renderEpochMs, false, helicalRefEpoch, st.settings.trueScale
            )
            camTarget = focus
        }

        processPendingTextures(dt)
        applyAdaptivePipelineResize(st)
        handleModeAndTargetChanges(st)

        // Apply silky touch fling momentum with exponential coasting decay
        val coastDecay = exp(-6.2f * dt)
        if (abs(orbitVelTheta) > 0.002f || abs(orbitVelPhi) > 0.002f) {
            targetTheta += orbitVelTheta * dt
            targetPhi = (targetPhi + orbitVelPhi * dt).coerceIn(0.12f, 3.02f)
            orbitVelTheta *= coastDecay
            orbitVelPhi *= coastDecay
        }
        if (abs(bhVelTheta) > 0.002f || abs(bhVelPhi) > 0.002f) {
            bhTargetTheta += bhVelTheta * dt
            bhTargetPhi = (bhTargetPhi + bhVelPhi * dt).coerceIn(0.18f, 2.94f)
            bhVelTheta *= coastDecay
            bhVelPhi *= coastDecay
        }
        if (abs(skyVelAz) > 0.05f || abs(skyVelAlt) > 0.05f) {
            skyTargetAz = ((skyTargetAz + skyVelAz * dt) + 360f) % 360f
            skyTargetAlt = (skyTargetAlt + skyVelAlt * dt).coerceIn(-12f, 88f)
            skyVelAz *= coastDecay
            skyVelAlt *= coastDecay
        }

        visualSpinAngle += dt.toDouble() * st.studio.visualSpin.toDouble() * 1.4

        hdrPipeline.beginScenePass()

        val labels = ArrayList<ProjectedLabel>(18)
        var currentYawRad = orbitTheta

        when (st.mode) {
            CosmosMode.BLACK_HOLE -> {
                renderBlackHoleMode(st, dt, labels)
                currentYawRad = bhTheta
            }
            CosmosMode.EARTH_SKY -> {
                renderEarthSkyMode(st, dt, labels)
                currentYawRad = (skyAzimuthDeg * PI / 180.0).toFloat()
            }
            CosmosMode.GALAXY -> {
                renderGalaxyMode(st, dt, labels)
                currentYawRad = orbitTheta
            }
            CosmosMode.SPACE, CosmosMode.HELICAL -> {
                renderSolarSystemMode(st, dt, labels)
                currentYawRad = orbitTheta
            }
        }

        val doBloom = st.settings.hdrBloom && st.settings.quality.bloomEnabled && emaFrameMs < 88f
        val blurIters = if (isSoftwareGpu || emaFrameMs > 38f) 1 else 2
        hdrPipeline.finishAndPresent(doBloom, st.settings.exposure, elapsedSec, blurIters)

        if (frameStartNs - lastTelemetryEmitNs > 60_000_000L) {
            lastTelemetryEmitNs = frameStartNs
            val bhTele = computeBlackHoleTelemetry(st)
            onFrameTelemetry(bhTele, labels, currentYawRad, skyAzimuthDeg, skyAltitudeDeg)
        }
    }

    private fun handleModeAndTargetChanges(st: CosmosUiState) {
        if (st.settings.trueScale != lastTrueScale) {
            lastTrueScale = st.settings.trueScale
            helicalTrailCache.invalidate()
            rebuildStaticOrbits(renderEpochMs.toLong(), st.settings.trueScale)
        }
        if (st.mode != lastMode) {
            val wasHelicalOverview = lastMode == CosmosMode.HELICAL && targetRadius > 90f
            prevFocusBody = lastBody
            prevFocusHelicalOverview = wasHelicalOverview
            focusTransitionT = 0f
            orbitVelTheta = 0f; orbitVelPhi = 0f
            lastMode = st.mode
            when (st.mode) {
                CosmosMode.GALAXY -> {
                    targetTheta = 0.68f; targetPhi = 0.90f; targetRadius = 132f
                }
                CosmosMode.HELICAL -> {
                    helicalRefEpoch = renderEpochMs
                    helicalTrailCache.invalidate()
                    // Dramatic sweeping 3/4 trailing hero framing of the Solar Galactic Vortex!
                    targetTheta = 0.62f; targetPhi = 1.16f; targetRadius = 335f
                }
                CosmosMode.BLACK_HOLE -> {
                    bhTargetTheta = 0.36f; bhTargetPhi = 1.38f; bhTargetRadius = 13.8f
                    bhShipClockSec = 0.0
                    bhEarthClockSec = 0.0
                }
                CosmosMode.SPACE -> {
                    val spec = SolarCatalog.byId.getValue(st.selectedBody)
                    targetRadius = (spec.visualRadius * st.studio.planetScale * 3.35f).coerceAtLeast(2.5f)
                    targetPhi = 1.24f
                    targetTheta = computeSunlitHeroTheta(st.selectedBody, renderEpochMs)
                }
                CosmosMode.EARTH_SKY -> {
                    // Point toward Moon if visible, else South-West horizon
                    val moonHz = AstrophysicsEngine.bodyHorizontalAtCity(CelestialBodyId.MOON, st.selectedCity, renderEpochMs.toLong())
                    if (moonHz.altitudeDeg > 6.0) {
                        skyTargetAz = moonHz.azimuthDeg.toFloat()
                        skyTargetAlt = moonHz.altitudeDeg.toFloat().coerceIn(14f, 65f)
                    } else {
                        skyTargetAz = 195f
                        skyTargetAlt = 24f
                    }
                }
            }
        }
        if (st.selectedBody != lastBody && (st.mode == CosmosMode.SPACE || st.mode == CosmosMode.HELICAL)) {
            prevFocusBody = lastBody
            prevFocusHelicalOverview = st.mode == CosmosMode.HELICAL && targetRadius > 90f
            focusTransitionT = 0f
            orbitVelTheta = 0f; orbitVelPhi = 0f
            lastBody = st.selectedBody
            if (st.mode == CosmosMode.SPACE) {
                val spec = SolarCatalog.byId.getValue(st.selectedBody)
                targetRadius = (spec.visualRadius * st.studio.planetScale * 3.35f).coerceAtLeast(2.5f)
                targetPhi = 1.24f
                targetTheta = computeSunlitHeroTheta(st.selectedBody, renderEpochMs)
            }
        }
        if (st.flyToTrigger != pendingFlyTrigger) {
            pendingFlyTrigger = st.flyToTrigger
            val lonRad = (-st.selectedCity.lon * PI / 180.0).toFloat()
            val latRad = ((90.0 - st.selectedCity.lat) * PI / 180.0).toFloat().coerceIn(0.2f, 2.9f)
            targetTheta = lonRad
            targetPhi = latRad
            targetRadius = 3.1f * st.studio.planetScale
        }
        if (st.resetCameraTrigger != pendingResetTrigger) {
            pendingResetTrigger = st.resetCameraTrigger
            onDoubleTap()
        }
    }

    private fun processPendingTextures(dt: Float) {
        val item = pendingBitmaps.poll()
        if (item != null) {
            val (key, bmp) = item
            val texId = GlUtils.createTextureFromBitmap(bmp, clampS = false)
            bmp.recycle()
            glTextures[key] = texId
            texMixProgress[key] = 0.01f
        }
        for ((k, v) in texMixProgress) {
            if (v < 1f) texMixProgress[k] = (v + dt * 1.1f).coerceAtMost(1f)
        }
    }

    private fun texId(key: PlanetTexKey): Int = glTextures[key] ?: whiteTex
    private fun texMix(key: PlanetTexKey): Float = texMixProgress[key] ?: 0f

    // =========================================================================
    // MODE 1 & 2: SOLAR SYSTEM 8K & HELICAL GALACTIC VORTEX
    // =========================================================================
    private fun renderSolarSystemMode(st: CosmosUiState, dt: Float, labels: MutableList<ProjectedLabel>) {
        val helical = st.mode == CosmosMode.HELICAL || st.settings.helicalMotion
        val trueScale = st.settings.trueScale
        val epochMs = renderEpochMs
        val epochLong = epochMs.toLong()
        val hdrScale = hdrPipeline.hdrEncodeScale

        // Compute Earth first so Moon reuses precomputed Earth position (zero duplicate VSOP87 call)
        val earthPos = AstrophysicsEngine.scenePositionContinuous(
            CelestialBodyId.EARTH, epochMs, helical, helicalRefEpoch, trueScale
        )
        bodyWorldPositions[CelestialBodyId.EARTH] = earthPos
        for (spec in SolarCatalog.planets) {
            if (spec.id == CelestialBodyId.EARTH) continue
            bodyWorldPositions[spec.id] = AstrophysicsEngine.scenePositionContinuous(
                spec.id, epochMs, helical, helicalRefEpoch, trueScale,
                precomputedEarthPos = if (spec.id == CelestialBodyId.MOON) earthPos else null
            )
        }
        val sunPos = bodyWorldPositions.getValue(CelestialBodyId.SUN)
        val isHelicalOverview = st.mode == CosmosMode.HELICAL && targetRadius > 90f
        // In Helical overview, frame a majestic point slightly trailing the Sun along HELIX_DIR so the entire corkscrew fills the screen!
        val focusPos = if (isHelicalOverview) {
            sunPos - AstrophysicsEngine.HELIX_DIR * 78f
        } else {
            bodyWorldPositions.getValue(st.selectedBody)
        }

        // Slow regal orbital camera drift in Helical mode when untouched so 3D depth pops immediately
        if (st.mode == CosmosMode.HELICAL && abs(orbitVelTheta) < 0.01f) {
            targetTheta += dt * 0.085f
        }

        val dampK = 1f - exp(-8.5f * dt)
        orbitTheta += (targetTheta - orbitTheta) * dampK
        orbitPhi += (targetPhi - orbitPhi) * dampK
        orbitRadius += (targetRadius - orbitRadius) * dampK

        // Dual-phase camera target: Quintic SmootherStep flight on body switches, ZERO-LAG lock once arrived!
        if (focusTransitionT < 1f) {
            focusTransitionT = (focusTransitionT + dt / 0.68f).coerceAtMost(1f)
            val t = focusTransitionT
            val ease = t * t * t * (t * (t * 6f - 15f) + 10f)
            val prevPos = if (prevFocusHelicalOverview) {
                sunPos - AstrophysicsEngine.HELIX_DIR * 78f
            } else {
                bodyWorldPositions[prevFocusBody] ?: focusPos
            }
            camTarget = Vec3(
                prevPos.x + (focusPos.x - prevPos.x) * ease,
                prevPos.y + (focusPos.y - prevPos.y) * ease,
                prevPos.z + (focusPos.z - prevPos.z) * ease
            )
        } else {
            camTarget = focusPos
        }

        val camX = camTarget.x + orbitRadius * sin(orbitPhi) * cos(orbitTheta)
        val camY = camTarget.y + orbitRadius * cos(orbitPhi)
        val camZ = camTarget.z + orbitRadius * sin(orbitPhi) * sin(orbitTheta)
        val camPos = Vec3(camX, camY, camZ)

        val aspect = viewportW.toFloat() / viewportH.toFloat()
        val fovDeg = if (helical) 52f else 48f
        val tanHalfFov = kotlin.math.tan(fovDeg * 0.5 * PI / 180.0).toFloat()
        Matrix.perspectiveM(projM, 0, fovDeg, aspect, 0.08f, 45_000f)
        Matrix.setLookAtM(viewM, 0, camX, camY, camZ, camTarget.x, camTarget.y, camTarget.z, 0f, 1f, 0f)
        Matrix.multiplyMM(vpM, 0, projM, 0, viewM, 0)

        val camFwd = (camTarget - camPos).normalized()
        val camRight = Vec3(viewM[0], viewM[4], viewM[8]).normalized()
        val camUp = Vec3(viewM[1], viewM[5], viewM[9]).normalized()

        // 1. Volumetric Deep-Space Milky Way + Nebula Skybox (replaces empty black void!)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        spaceBgProg.use()
        spaceBgProg.setFloat("uTanFov", tanHalfFov)
        spaceBgProg.setFloat("uAspect", aspect)
        spaceBgProg.setVec3("uCamFwd", camFwd.x, camFwd.y, camFwd.z)
        spaceBgProg.setVec3("uCamRight", camRight.x, camRight.y, camRight.z)
        spaceBgProg.setVec3("uCamUp", camUp.x, camUp.y, camUp.z)
        spaceBgProg.setFloat("uHdrScale", hdrScale)
        spaceBgProg.setFloat("uHelicalBoost", if (helical) 1f else 0f)
        fsMesh.draw()

        // 2. Crisp Hipparcos & Field Starfield Layer
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
        Matrix.setIdentityM(modelM, 0)
        Matrix.translateM(modelM, 0, camX, camY, camZ)
        Matrix.multiplyMM(mvpM, 0, vpM, 0, modelM, 0)
        starsProg.use()
        starsProg.setMat4("uMvp", mvpM)
        starsProg.setFloat("uTime", elapsedSec)
        starsProg.setFloat("uPixelRatio", pixelRatio)
        starsProg.setFloat("uVisibility", 1.15f)
        starsProg.setFloat("uTwinkle", 0.38f)
        starsProg.setFloat("uHdrScale", hdrScale)
        starfieldMesh.draw(GLES30.GL_POINTS)

        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(true)

        // 3. Einstein Spacetime Curvature Grid
        if (st.settings.spacetimeGrid) {
            drawSpacetimeGrid(sunPos, hdrScale)
        }

        // 4. 3D Galactic Velocity Star-Streaks & Heliosphere Golden Bow-Shock (Helical Mode!)
        if (helical) {
            drawHelicalVelocityStreaks(sunPos, hdrScale)
            drawHeliosphereBowShock(sunPos, hdrScale)
        }

        // 5. 3D Volumetric Glowing Plasma Ribbons (Helical Corkscrew Wakes OR Luminous Orbital Rings)
        if (st.settings.orbits || helical) {
            drawVolumetricOrbitsOrHelix(epochMs, helical, trueScale, camX, camY, camZ, hdrScale)
        }

        val daysJ2000 = AstrophysicsEngine.daysSinceJ2000Double(epochMs)

        // 6. 5,000 Keplerian Asteroid Belt
        if (st.settings.asteroidBelt) {
            GLES30.glDepthMask(false)
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            asteroidProg.use()
            asteroidProg.setMat4("uMvp", vpM)
            asteroidProg.setVec3("uSunPos", sunPos.x, sunPos.y, sunPos.z)
            asteroidProg.setVec3("uCamPos", camX, camY, camZ)
            asteroidProg.setFloat("uDays", daysJ2000.toFloat())
            asteroidProg.setFloat("uPixelRatio", pixelRatio)
            asteroidProg.setFloat("uHdrScale", hdrScale)
            asteroidMesh.draw(GLES30.GL_POINTS)
            GLES30.glDepthMask(true)
        }

        // 7. Render Sun & All Planets (with smart helical overview scale boost so planets visibly ride their plasma helices!)
        val helicalZoomFactor = if (helical && !trueScale) {
            ((orbitRadius - 60f) / 180f).coerceIn(0f, 1f)
        } else 0f

        for (spec in SolarCatalog.planets) {
            val pos = bodyWorldPositions.getValue(spec.id)
            val baseR = if (trueScale) {
                (spec.radiusKm / AstrophysicsEngine.AU_KM * AstrophysicsEngine.SCENE_AU).toFloat().coerceAtLeast(0.08f)
            } else {
                val boost = if (spec.id == CelestialBodyId.SUN) {
                    1f + helicalZoomFactor * 0.65f
                } else {
                    1f + helicalZoomFactor * 1.45f
                }
                spec.visualRadius * st.studio.planetScale * boost
            }
            val oblateness = (spec.oblateness + st.studio.oblatenessOverride).coerceIn(-0.15f, 0.38f)
            val scaleY = baseR * (1f - oblateness)
            // Compute axial rotation in full Double precision before wrapping to [0, 360) for glass-smooth spin
            val rawSpinDeg = (daysJ2000 * 24.0 / spec.rotationHours) * 360.0 + visualSpinAngle * 35.0
            val spinDeg = (((rawSpinDeg % 360.0) + 360.0) % 360.0).toFloat()

            Matrix.setIdentityM(modelM, 0)
            Matrix.translateM(modelM, 0, pos.x, pos.y, pos.z)
            Matrix.rotateM(modelM, 0, spec.axialTiltDeg, 0f, 0f, 1f)
            Matrix.rotateM(modelM, 0, spinDeg, 0f, 1f, 0f)
            Matrix.scaleM(modelM, 0, baseR, scaleY, baseR)
            Matrix.multiplyMM(mvpM, 0, vpM, 0, modelM, 0)
            Matrix.invertM(invModelM, 0, modelM, 0)

            val camLocal = transformPoint(invModelM, camPos)
            val camDistRadii = (camPos - pos).length() / max(baseR, 0.01f)
            val sphereMesh = if (camDistRadii < 18f || spec.id == st.selectedBody) sphereHi else sphereLo

            if (spec.kind == BodyKind.STAR) {
                GLES30.glEnable(GLES30.GL_CULL_FACE)
                GLES30.glDisable(GLES30.GL_BLEND)
                GLES30.glDepthMask(true)
                sunProg.use()
                sunProg.setMat4("uMvp", mvpM)
                sunProg.setVec3("uCamLocal", camLocal.x, camLocal.y, camLocal.z)
                sunProg.setFloat("uTime", elapsedSec)
                sunProg.setFloat("uSunBoost", st.studio.sunIntensity)
                sunProg.setFloat("uHdrScale", hdrScale)
                sphereMesh.draw()

                // Blazing Additive Solar Corona Billboard
                GLES30.glDisable(GLES30.GL_CULL_FACE)
                GLES30.glEnable(GLES30.GL_BLEND)
                GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
                GLES30.glDepthMask(false)
                coronaProg.use()
                coronaProg.setMat4("uMvp", vpM)
                coronaProg.setVec3("uRight", camRight.x, camRight.y, camRight.z)
                coronaProg.setVec3("uUp", camUp.x, camUp.y, camUp.z)
                coronaProg.setVec3("uCenter", pos.x, pos.y, pos.z)
                coronaProg.setFloat("uSize", baseR * (if (helical) 6.2f else 5.4f))
                coronaProg.setFloat("uTime", elapsedSec)
                coronaProg.setFloat("uSunBoost", st.studio.sunIntensity)
                coronaProg.setFloat("uHdrScale", hdrScale)
                quadMesh.draw()
                GLES30.glDepthMask(true)
            } else {
                val sunDirWorld = (sunPos - pos).normalized()
                val sunDirLocal = transformDir(invModelM, sunDirWorld).normalized()
                val detailFade = ((6.5f - camDistRadii) / 4.8f).coerceIn(0f, 1f)

                GLES30.glEnable(GLES30.GL_CULL_FACE)
                GLES30.glDisable(GLES30.GL_BLEND)
                GLES30.glDepthMask(true)
                planetProg.use()
                planetProg.setMat4("uMvp", mvpM)
                planetProg.setVec3("uSunDir", sunDirLocal.x, sunDirLocal.y, sunDirLocal.z)
                planetProg.setVec3("uCamLocal", camLocal.x, camLocal.y, camLocal.z)
                planetProg.setFloat("uTime", elapsedSec)
                planetProg.setFloat("uIrr", 1.15f)
                planetProg.setFloat("uDetail", detailFade)
                planetProg.setFloat("uRelief", st.studio.reliefDepth)
                planetProg.setFloat("uNightBoost", st.studio.nightLights)
                planetProg.setFloat("uSunBoost", st.studio.sunIntensity)
                planetProg.setFloat("uCloudBoost", st.studio.cloudDensity)
                planetProg.setFloat("uAurora", if (st.settings.auroras) 1f else 0f)
                planetProg.setFloat("uCloudDrift", (elapsedSec * 0.0022f) % 1f)
                planetProg.setFloat("uHdrScale", hdrScale)

                bindPlanetUniforms(spec.id)
                sphereMesh.draw()

                if (spec.id == CelestialBodyId.EARTH && st.studio.cloudDensity > 0.02f && camDistRadii < 24f) {
                    Matrix.scaleM(cloudM, 0, modelM, 0, 1.008f, 1.008f, 1.008f)
                    Matrix.multiplyMM(mvpM, 0, vpM, 0, cloudM, 0)
                    GLES30.glEnable(GLES30.GL_BLEND)
                    GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
                    GLES30.glDepthMask(false)
                    cloudProg.use()
                    cloudProg.setMat4("uMvp", mvpM)
                    GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId(PlanetTexKey.EARTH_CLOUD))
                    cloudProg.setInt("uCloud", 0)
                    cloudProg.setFloat("uCloudMix", texMix(PlanetTexKey.EARTH_CLOUD))
                    cloudProg.setVec3("uSunDir", sunDirLocal.x, sunDirLocal.y, sunDirLocal.z)
                    cloudProg.setVec3("uCamLocal", camLocal.x, camLocal.y, camLocal.z)
                    cloudProg.setFloat("uIrr", 1.15f)
                    cloudProg.setFloat("uDrift", (elapsedSec * 0.0028f) % 1f)
                    cloudProg.setFloat("uTime", elapsedSec)
                    cloudProg.setFloat("uCloudBoost", st.studio.cloudDensity)
                    cloudProg.setFloat("uHdrScale", hdrScale)
                    sphereMesh.draw()
                    GLES30.glDepthMask(true)
                }

                if (spec.hasRings && st.studio.ringIntensity > 0.02f) {
                    GLES30.glDisable(GLES30.GL_CULL_FACE)
                    GLES30.glEnable(GLES30.GL_BLEND)
                    GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
                    GLES30.glDepthMask(false)
                    ringProg.use()
                    ringProg.setMat4("uMvp", mvpM)
                    ringProg.setVec3("uSunDir", sunDirLocal.x, sunDirLocal.y, sunDirLocal.z)
                    ringProg.setVec3("uCamLocal", camLocal.x, camLocal.y, camLocal.z)
                    ringProg.setFloat("uIrr", 1.15f)
                    ringProg.setFloat("uRingBoost", st.studio.ringIntensity)
                    ringProg.setFloat("uHdrScale", hdrScale)
                    if (spec.id == CelestialBodyId.SATURN) {
                        ringProg.setVec2("uRingBounds", 1.236f, 2.327f)
                        ringProg.setFloat("uIsDust", 0f)
                        saturnRingMesh.draw()
                    } else {
                        ringProg.setVec2("uRingBounds", 1.62f, 2.04f)
                        ringProg.setFloat("uIsDust", 1f)
                        uranusRingMesh.draw()
                    }
                    GLES30.glDepthMask(true)
                }

                if (camDistRadii < 42f) {
                    GLES30.glEnable(GLES30.GL_CULL_FACE)
                    drawAtmosphereShell(spec.id, sunDirLocal, camLocal, st.studio.atmosphereDensity, hdrScale)
                }
            }
            GLES30.glDisable(GLES30.GL_CULL_FACE)

            if (st.settings.labels && (!helical || spec.id != CelestialBodyId.MOON)) {
                projectToLabel(spec.id.name, spec.nameAr, pos + Vec3(0f, baseR * 1.48f, 0f), spec.accentColor)?.let {
                    labels.add(it)
                }
            }
        }

        // 8. 3D Cosmic Parallax Dust
        if (st.settings.cosmicDust) {
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
            GLES30.glDepthMask(false)
            dustProg.use()
            dustProg.setMat4("uMvp", vpM)
            dustProg.setVec3("uCamPos", camX, camY, camZ)
            dustProg.setFloat("uTime", elapsedSec)
            dustProg.setFloat("uPixelRatio", pixelRatio)
            dustProg.setFloat("uHdrScale", hdrScale)
            dustMesh.draw(GLES30.GL_POINTS)
            GLES30.glDepthMask(true)
        }
    }

    private fun drawHelicalVelocityStreaks(sunPos: Vec3, hdrScale: Float) {
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
        GLES30.glDepthMask(false)
        GLES30.glLineWidth(2.0f)
        val hd = AstrophysicsEngine.HELIX_DIR
        streakProg.use()
        streakProg.setMat4("uMvp", vpM)
        streakProg.setVec3("uAnchorPos", sunPos.x, sunPos.y, sunPos.z)
        streakProg.setVec3("uHelixDir", hd.x, hd.y, hd.z)
        streakProg.setFloat("uTime", elapsedSec)
        streakProg.setFloat("uHdrScale", hdrScale)
        streakMesh.draw(GLES30.GL_LINES)
        GLES30.glDepthMask(true)
    }

    private fun drawHeliosphereBowShock(sunPos: Vec3, hdrScale: Float) {
        val hd = AstrophysicsEngine.HELIX_DIR
        // Rotate local +Y axis to align with HELIX_DIR
        val up = Vec3(0f, 1f, 0f)
        val axis = cross(up, hd)
        val axisLen = axis.length()
        val angleDeg = (acos((up.y * hd.y).coerceIn(-1f, 1f).toDouble()) * 180.0 / PI).toFloat()

        Matrix.setIdentityM(modelM, 0)
        Matrix.translateM(modelM, 0, sunPos.x, sunPos.y, sunPos.z)
        if (axisLen > 1e-4f) {
            Matrix.rotateM(modelM, 0, angleDeg, axis.x / axisLen, axis.y / axisLen, axis.z / axisLen)
        }
        Matrix.multiplyMM(mvpM, 0, vpM, 0, modelM, 0)

        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
        GLES30.glDepthMask(false)
        bowShockProg.use()
        bowShockProg.setMat4("uMvp", mvpM)
        bowShockProg.setFloat("uTime", elapsedSec)
        bowShockProg.setFloat("uHdrScale", hdrScale)
        bowShockMesh.draw()
        GLES30.glDepthMask(true)
    }

    private fun drawVolumetricOrbitsOrHelix(
        epochMs: Double,
        helical: Boolean,
        trueScale: Boolean,
        camX: Float,
        camY: Float,
        camZ: Float,
        hdrScale: Float
    ) {
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
        GLES30.glDepthMask(false)
        ribbonProg.use()
        ribbonProg.setMat4("uMvp", vpM)
        ribbonProg.setFloat("uTime", elapsedSec)
        ribbonProg.setFloat("uHdrScale", hdrScale)

        if (helical) {
            // Sliding-window cached 920-day 3D plasma corkscrew wakes (>95% faster CPU than per-frame full VSOP87 rebuild!)
            val segs = if (isSoftwareGpu) 160 else 320
            val helicalIds = listOf(
                CelestialBodyId.SUN,
                CelestialBodyId.MERCURY,
                CelestialBodyId.VENUS,
                CelestialBodyId.EARTH,
                CelestialBodyId.MARS,
                CelestialBodyId.JUPITER,
                CelestialBodyId.SATURN,
                CelestialBodyId.URANUS,
                CelestialBodyId.NEPTUNE
            )
            for (id in helicalIds) {
                val spec = SolarCatalog.byId.getValue(id)
                val livePos = bodyWorldPositions.getValue(id)
                val pointCount = helicalTrailCache.fillTrailPoints(
                    id = id,
                    epochMs = epochMs,
                    helicalRefEpochMs = helicalRefEpoch,
                    trueScale = trueScale,
                    segments = segs,
                    livePos = livePos,
                    outPoints4 = helicalPointsBuf
                )
                val baseW = when (id) {
                    CelestialBodyId.SUN -> 4.6f
                    CelestialBodyId.JUPITER, CelestialBodyId.SATURN -> 3.0f
                    CelestialBodyId.EARTH -> 2.6f
                    else -> 2.1f
                }
                val vertCount = GlGeometry.uploadRibbonFromPoints(
                    mesh = sharedRibbonMesh,
                    points4 = helicalPointsBuf,
                    pointCount = pointCount,
                    camX = camX,
                    camY = camY,
                    camZ = camZ,
                    baseHalfWidth = baseW,
                    taperTail = true,
                    outScratch = ribbonVertsScratch
                )
                ribbonProg.setFloat("uHead", 1.0f)
                ribbonProg.setFloat("uClosed", 0f)
                ribbonProg.setFloat("uEnergy", 1.0f)
                ribbonProg.setVec3("uColor", spec.accentColor.red, spec.accentColor.green, spec.accentColor.blue)
                ribbonProg.setFloat("uOpacity", if (id == CelestialBodyId.SUN) 1.35f else 1.15f)
                GLES30.glBindVertexArray(sharedRibbonMesh.vao)
                GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, vertCount)
                GLES30.glBindVertexArray(0)
            }
            helicalTrailCache.commitFrame(epochMs)
        } else {
            val epochLong = epochMs.toLong()
            if (kotlin.math.abs(epochLong - lastOrbitRebuildEpoch) > 365L * 86_400_000L * 4) {
                rebuildStaticOrbits(epochLong, trueScale)
            }
            for ((id, pts) in orbitPointCache) {
                val spec = SolarCatalog.byId.getValue(id)
                val pointCount = pts.size / 4
                val vertCount = GlGeometry.uploadRibbonFromPoints(
                    mesh = sharedRibbonMesh,
                    points4 = pts,
                    pointCount = pointCount,
                    camX = camX,
                    camY = camY,
                    camZ = camZ,
                    baseHalfWidth = 0.85f,
                    taperTail = false,
                    outScratch = ribbonVertsScratch
                )
                val head = (((epochMs - lastOrbitRebuildEpoch.toDouble()) / (spec.orbitalPeriodDays * 86_400_000.0)) % 1.0 + 1.0) % 1.0
                ribbonProg.setFloat("uHead", head.toFloat())
                ribbonProg.setFloat("uClosed", 1f)
                ribbonProg.setFloat("uEnergy", 0.25f)
                ribbonProg.setVec3("uColor", spec.accentColor.red, spec.accentColor.green, spec.accentColor.blue)
                ribbonProg.setFloat("uOpacity", 0.52f)
                GLES30.glBindVertexArray(sharedRibbonMesh.vao)
                GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, vertCount)
                GLES30.glBindVertexArray(0)
            }
        }
        GLES30.glDepthMask(true)
    }

    private fun rebuildStaticOrbits(epoch: Long, trueScale: Boolean) {
        lastOrbitRebuildEpoch = epoch
        val segs = 144
        for (p in SolarCatalog.planets) {
            if (p.id == CelestialBodyId.SUN || p.id == CelestialBodyId.MOON) continue
            val buf = orbitPointCache.getOrPut(p.id) { FloatArray((segs + 1) * 4) }
            val periodMs = p.orbitalPeriodDays * 86_400_000.0
            for (i in 0..segs) {
                val f = i.toFloat() / segs
                val t = epoch + (f * periodMs).toLong()
                val pos = AstrophysicsEngine.scenePosition(p.id, t, false, helicalRefEpoch.toLong(), trueScale)
                buf[i * 4] = pos.x; buf[i * 4 + 1] = pos.y; buf[i * 4 + 2] = pos.z; buf[i * 4 + 3] = f
            }
        }
    }

    private fun bindPlanetUniforms(id: CelestialBodyId) {
        val kindInt = when (id) {
            CelestialBodyId.EARTH -> 3
            CelestialBodyId.VENUS -> 2
            CelestialBodyId.MERCURY, CelestialBodyId.MOON, CelestialBodyId.MARS -> 1
            else -> 4
        }
        planetProg.setInt("uKind", kindInt)

        val mapKey = when (id) {
            CelestialBodyId.EARTH -> PlanetTexKey.EARTH_DAY
            CelestialBodyId.MOON -> PlanetTexKey.MOON_MAP
            CelestialBodyId.MERCURY -> PlanetTexKey.MERCURY_MAP
            CelestialBodyId.MARS -> PlanetTexKey.MARS_MAP
            CelestialBodyId.JUPITER -> PlanetTexKey.JUPITER_MAP
            CelestialBodyId.SATURN -> PlanetTexKey.SATURN_MAP
            CelestialBodyId.URANUS -> PlanetTexKey.URANUS_MAP
            CelestialBodyId.NEPTUNE -> PlanetTexKey.NEPTUNE_MAP
            else -> null
        }

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mapKey?.let { texId(it) } ?: whiteTex)
        planetProg.setInt("uMap", 0)
        planetProg.setFloat("uMapMix", mapKey?.let { texMix(it) } ?: 0f)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (id == CelestialBodyId.EARTH) texId(PlanetTexKey.EARTH_NIGHT) else whiteTex)
        planetProg.setInt("uNight", 1)
        planetProg.setFloat("uNightMix", if (id == CelestialBodyId.EARTH) texMix(PlanetTexKey.EARTH_NIGHT) else 0f)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (id == CelestialBodyId.EARTH) texId(PlanetTexKey.EARTH_WATER) else whiteTex)
        planetProg.setInt("uWater", 2)
        planetProg.setFloat("uWaterMix", if (id == CelestialBodyId.EARTH) texMix(PlanetTexKey.EARTH_WATER) else 0f)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (id == CelestialBodyId.EARTH) texId(PlanetTexKey.EARTH_CLOUD) else whiteTex)
        planetProg.setInt("uCloud", 3)
        planetProg.setFloat("uCloudMix", if (id == CelestialBodyId.EARTH) texMix(PlanetTexKey.EARTH_CLOUD) else 0f)

        if (id == CelestialBodyId.SATURN) {
            planetProg.setVec2("uRingBounds", 1.236f, 2.327f)
        } else {
            planetProg.setVec2("uRingBounds", 0f, 0f)
        }

        when (id) {
            CelestialBodyId.MERCURY -> {
                planetProg.setVec3("uColA", 0.36f, 0.35f, 0.34f); planetProg.setVec3("uColB", 0.69f, 0.67f, 0.64f)
                planetProg.setFloat("uLommel", 0.72f)
            }
            CelestialBodyId.MOON -> {
                planetProg.setVec3("uColA", 0.43f, 0.43f, 0.44f); planetProg.setVec3("uColB", 0.78f, 0.78f, 0.80f)
                planetProg.setFloat("uLommel", 0.78f)
            }
            CelestialBodyId.MARS -> {
                planetProg.setVec3("uColA", 0.50f, 0.18f, 0.09f); planetProg.setVec3("uColB", 0.88f, 0.49f, 0.28f)
                planetProg.setFloat("uLommel", 0.35f)
            }
            CelestialBodyId.JUPITER -> {
                planetProg.setVec3("uColA", 0.68f, 0.47f, 0.29f); planetProg.setVec3("uColB", 0.92f, 0.82f, 0.66f)
                planetProg.setFloat("uLimb", 0.55f); planetProg.setFloat("uWrap", 0.03f); planetProg.setFloat("uWind", 1.0f)
            }
            CelestialBodyId.SATURN -> {
                planetProg.setVec3("uColA", 0.80f, 0.70f, 0.48f); planetProg.setVec3("uColB", 0.96f, 0.90f, 0.70f)
                planetProg.setFloat("uLimb", 0.50f); planetProg.setFloat("uWrap", 0.03f); planetProg.setFloat("uWind", 0.85f)
            }
            CelestialBodyId.URANUS -> {
                planetProg.setVec3("uColA", 0.48f, 0.84f, 0.90f); planetProg.setVec3("uColB", 0.70f, 0.94f, 0.98f)
                planetProg.setFloat("uLimb", 0.60f); planetProg.setFloat("uWrap", 0.05f); planetProg.setFloat("uWind", 0.35f)
            }
            CelestialBodyId.NEPTUNE -> {
                planetProg.setVec3("uColA", 0.18f, 0.35f, 0.86f); planetProg.setVec3("uColB", 0.38f, 0.58f, 1.0f)
                planetProg.setFloat("uLimb", 0.55f); planetProg.setFloat("uWrap", 0.05f); planetProg.setFloat("uWind", 0.68f)
            }
            else -> Unit
        }
    }

    private fun drawAtmosphereShell(id: CelestialBodyId, sunDirLocal: Vec3, camLocal: Vec3, atmoBoost: Float, hdrScale: Float) {
        if (atmoBoost <= 0.02f) return
        val params = when (id) {
            CelestialBodyId.EARTH -> floatArrayOf(1.065f, 5.8f, 13.5f, 33.1f, 0.28f)
            CelestialBodyId.VENUS -> floatArrayOf(1.09f, 9.0f, 7.0f, 3.5f, 0.34f)
            CelestialBodyId.MARS -> floatArrayOf(1.045f, 2.4f, 1.8f, 1.0f, 0.25f)
            CelestialBodyId.JUPITER -> floatArrayOf(1.05f, 3.0f, 3.6f, 4.6f, 0.18f)
            CelestialBodyId.SATURN -> floatArrayOf(1.05f, 3.4f, 3.2f, 2.6f, 0.18f)
            CelestialBodyId.URANUS -> floatArrayOf(1.06f, 1.5f, 6.0f, 9.0f, 0.24f)
            CelestialBodyId.NEPTUNE -> floatArrayOf(1.06f, 1.2f, 4.5f, 13.0f, 0.24f)
            else -> return
        }
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
        GLES30.glDepthMask(false)
        atmoProg.use()
        atmoProg.setMat4("uMvp", mvpM)
        atmoProg.setFloat("uShell", params[0])
        atmoProg.setVec3("uBetaR", params[1], params[2], params[3])
        atmoProg.setFloat("uIntensity", params[4])
        atmoProg.setVec3("uSunDir", sunDirLocal.x, sunDirLocal.y, sunDirLocal.z)
        atmoProg.setVec3("uCamLocal", camLocal.x, camLocal.y, camLocal.z)
        atmoProg.setFloat("uIrr", 1.15f)
        atmoProg.setFloat("uAtmoBoost", atmoBoost)
        atmoProg.setFloat("uHdrScale", hdrScale)
        sphereLo.draw()
        GLES30.glDepthMask(true)
    }

    private fun drawSpacetimeGrid(sunPos: Vec3, hdrScale: Float) {
        val order = listOf(
            CelestialBodyId.MERCURY, CelestialBodyId.VENUS, CelestialBodyId.EARTH, CelestialBodyId.MARS,
            CelestialBodyId.JUPITER, CelestialBodyId.SATURN, CelestialBodyId.URANUS, CelestialBodyId.NEPTUNE
        )
        val weights = floatArrayOf(2.5f, 5f, 6.5f, 3.5f, 42f, 30f, 15f, 16f)
        for (i in order.indices) {
            val p = bodyWorldPositions[order[i]] ?: Vec3(0f, 0f, 0f)
            spacetimePacked[i * 4] = p.x
            spacetimePacked[i * 4 + 1] = p.y
            spacetimePacked[i * 4 + 2] = p.z
            spacetimePacked[i * 4 + 3] = weights[i]
        }
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
        GLES30.glDepthMask(false)
        spacetimeProg.use()
        spacetimeProg.setMat4("uMvp", vpM)
        spacetimeProg.setVec3("uSunPos", sunPos.x, sunPos.y, sunPos.z)
        spacetimeProg.setVec4Array("uPlanets", spacetimePacked, 8)
        spacetimeProg.setFloat("uTime", elapsedSec)
        spacetimeProg.setFloat("uHdrScale", hdrScale)
        spacetimeMesh.draw()
        GLES30.glDepthMask(true)
    }

    // =========================================================================
    // MODE 3: EARTH SKY OBSERVATORY DOME ("سماؤك الآن")
    // =========================================================================
    private fun renderEarthSkyMode(st: CosmosUiState, dt: Float, labels: MutableList<ProjectedLabel>) {
        val hdrScale = hdrPipeline.hdrEncodeScale
        val dampK = 1f - exp(-9.0f * dt)
        var dAz = skyTargetAz - skyAzimuthDeg
        if (dAz > 180f) dAz -= 360f
        if (dAz < -180f) dAz += 360f
        skyAzimuthDeg = ((skyAzimuthDeg + dAz * dampK) + 360f) % 360f
        skyAltitudeDeg += (skyTargetAlt - skyAltitudeDeg) * dampK

        val fwd = AstrophysicsEngine.horizontalToUnitVec(skyAltitudeDeg.toDouble(), skyAzimuthDeg.toDouble()).normalized()
        val worldUp = Vec3(0f, 1f, 0f)
        val right = cross(fwd, worldUp).normalized()
        val up = cross(right, fwd).normalized()

        val skyEpoch = renderEpochMs.toLong()
        val sunHz = AstrophysicsEngine.sunHorizontalAtCity(st.selectedCity, skyEpoch)
        val sunDir = AstrophysicsEngine.horizontalToUnitVec(sunHz.altitudeDeg, sunHz.azimuthDeg).normalized()
        val moonHz = AstrophysicsEngine.bodyHorizontalAtCity(CelestialBodyId.MOON, st.selectedCity, skyEpoch)
        val moonDir = AstrophysicsEngine.horizontalToUnitVec(moonHz.altitudeDeg, moonHz.azimuthDeg).normalized()

        // Pack 5 visible naked-eye planets with Astronomy Engine coordinates & magnitude weights
        for (i in skyPlanetIds.indices) {
            val id = skyPlanetIds[i]
            val spec = SolarCatalog.byId.getValue(id)
            val hz = AstrophysicsEngine.bodyHorizontalAtCity(id, st.selectedCity, skyEpoch)
            val dir = AstrophysicsEngine.horizontalToUnitVec(hz.altitudeDeg, hz.azimuthDeg).normalized()
            val mag = AstrophysicsEngine.apparentMagnitude(id, skyEpoch)
            val weight = if (hz.altitudeDeg > 0.5) ((2.6 - mag * 0.28).toFloat()).coerceIn(0.85f, 2.6f) else 0f
            skyPlanetDirs[i * 4] = dir.x
            skyPlanetDirs[i * 4 + 1] = dir.y
            skyPlanetDirs[i * 4 + 2] = dir.z
            skyPlanetDirs[i * 4 + 3] = weight
            skyPlanetCols[i * 3] = spec.accentColor.red
            skyPlanetCols[i * 3 + 1] = spec.accentColor.green
            skyPlanetCols[i * 3 + 2] = spec.accentColor.blue
        }

        val aspect = viewportW.toFloat() / viewportH.toFloat()
        val fovDeg = 58f
        val tanHalfFov = kotlin.math.tan(fovDeg * 0.5 * PI / 180.0).toFloat()

        // 1. Living Observatory Sky Dome (Rayleigh/Mie + Real Illuminated Moon + Planets + Mountains + Meteors + Clouds)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_BLEND)
        skyDomeProg.use()
        skyDomeProg.setFloat("uTime", elapsedSec)
        skyDomeProg.setFloat("uTanFov", tanHalfFov)
        skyDomeProg.setFloat("uAspect", aspect)
        skyDomeProg.setVec3("uCamFwd", fwd.x, fwd.y, fwd.z)
        skyDomeProg.setVec3("uCamRight", right.x, right.y, right.z)
        skyDomeProg.setVec3("uCamUp", up.x, up.y, up.z)
        skyDomeProg.setVec3("uSunDir", sunDir.x, sunDir.y, sunDir.z)
        skyDomeProg.setVec3("uMoonDir", moonDir.x, moonDir.y, moonDir.z)
        skyDomeProg.setVec4Array("uPlanetDirs", skyPlanetDirs, 5)
        skyDomeProg.setVec3Array("uPlanetCols", skyPlanetCols, 5)
        skyDomeProg.setFloat("uCloudCover", st.weather.cloudCoverPercent / 100f)
        skyDomeProg.setFloat("uShowClouds", if (st.settings.liveClouds) 1f else 0f)
        skyDomeProg.setFloat("uHdrScale", hdrScale)
        fsMesh.draw()

        Matrix.perspectiveM(projM, 0, fovDeg, aspect, 0.1f, 25_000f)
        Matrix.setLookAtM(viewM, 0, 0f, 0f, 0f, fwd.x, fwd.y, fwd.z, up.x, up.y, up.z)
        Matrix.multiplyMM(vpM, 0, projM, 0, viewM, 0)

        val nightAlpha = (1f - ((sunHz.altitudeDeg + 10.0) / 22.0).toFloat()).coerceIn(0.06f, 1.0f)

        // 2. Twinkling Hipparcos Stars rotated by Local Apparent Sidereal Time & City Latitude
        val lstDeg = (AstrophysicsEngine.greenwichSiderealDeg(skyEpoch) + st.selectedCity.lon).toFloat()
        val latDeg = st.selectedCity.lat.toFloat()
        Matrix.setIdentityM(modelM, 0)
        Matrix.rotateM(modelM, 0, (90f - latDeg), 1f, 0f, 0f)
        Matrix.rotateM(modelM, 0, -lstDeg, 0f, 1f, 0f)
        Matrix.multiplyMM(mvpM, 0, vpM, 0, modelM, 0)

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
        starsProg.use()
        starsProg.setMat4("uMvp", mvpM)
        starsProg.setFloat("uTime", elapsedSec)
        starsProg.setFloat("uPixelRatio", pixelRatio)
        starsProg.setFloat("uVisibility", nightAlpha * 1.25f)
        starsProg.setFloat("uTwinkle", 1.0f)
        starsProg.setFloat("uHdrScale", hdrScale)
        starfieldMesh.draw(GLES30.GL_POINTS)

        // 3. Classical Constellations
        if (st.settings.constellations && nightAlpha > 0.12f) {
            drawConstellations(st, skyEpoch, nightAlpha, hdrScale)
        }

        // 4. Projected Astronomical Labels
        if (st.settings.labels) {
            val cardinals = listOf(
                "شمال N" to 0.0, "شرق E" to 90.0, "جنوب S" to 180.0, "غرب W" to 270.0
            )
            for ((txt, az) in cardinals) {
                val p = AstrophysicsEngine.horizontalToUnitVec(3.2, az) * 100f
                projectToLabel("card_$az", txt, p, Color(0xFFEBD091), isSubtle = true)?.let { labels.add(it) }
            }
            for (spec in SolarCatalog.planets) {
                if (spec.id == CelestialBodyId.EARTH) continue
                val hz = AstrophysicsEngine.bodyHorizontalAtCity(spec.id, st.selectedCity, skyEpoch)
                if (hz.altitudeDeg > 1.5) {
                    val p = AstrophysicsEngine.horizontalToUnitVec(hz.altitudeDeg, hz.azimuthDeg) * 120f
                    projectToLabel("sky_${spec.id}", "${spec.nameAr} (${hz.altitudeDeg.toInt()}°)", p, spec.accentColor)?.let {
                        labels.add(it)
                    }
                }
            }
            if (nightAlpha > 0.25f) {
                for (star in AstrophysicsEngine.BRIGHT_STARS.take(14)) {
                    val hz = AstrophysicsEngine.equatorialToHorizontal(
                        star.raDeg, star.decDeg, st.selectedCity.lat, st.selectedCity.lon, skyEpoch
                    )
                    if (hz.altitudeDeg > 10.0) {
                        val p = AstrophysicsEngine.horizontalToUnitVec(hz.altitudeDeg, hz.azimuthDeg) * 140f
                        projectToLabel("st_${star.nameAr}", star.nameAr, p, Color(0xFF9BF2FF), isSubtle = true)?.let {
                            labels.add(it)
                        }
                    }
                }
            }
        }
    }

    private fun drawConstellations(st: CosmosUiState, skyEpoch: Long, nightAlpha: Float, hdrScale: Float) {
        GLES30.glLineWidth(2.0f)
        trailProg.use()
        trailProg.setMat4("uMvp", vpM)
        trailProg.setFloat("uHead", 1f)
        trailProg.setFloat("uClosed", 1f)
        trailProg.setVec3("uColor", 0.36f, 0.80f, 1.0f)
        trailProg.setFloat("uOpacity", nightAlpha * 0.56f)
        trailProg.setFloat("uHdrScale", hdrScale)

        for (con in AstrophysicsEngine.CONSTELLATIONS) {
            for (poly in con.polylines) {
                var count = 0
                for ((ra, dec) in poly) {
                    val hz = AstrophysicsEngine.equatorialToHorizontal(
                        ra, dec, st.selectedCity.lat, st.selectedCity.lon, skyEpoch
                    )
                    if (hz.altitudeDeg < -6.0) continue
                    val v = AstrophysicsEngine.horizontalToUnitVec(hz.altitudeDeg, hz.azimuthDeg) * 150f
                    constellationBuf[count * 4] = v.x
                    constellationBuf[count * 4 + 1] = v.y
                    constellationBuf[count * 4 + 2] = v.z
                    constellationBuf[count * 4 + 3] = 1f
                    count++
                }
                if (count >= 2) {
                    GlGeometry.uploadTrailVertices(constellationMesh, constellationBuf, count)
                    GLES30.glBindVertexArray(constellationMesh.vao)
                    GLES30.glDrawArrays(GLES30.GL_LINE_STRIP, 0, count)
                    GLES30.glBindVertexArray(0)
                }
            }
        }
    }

    // =========================================================================
    // MODE 4: MILKY WAY 3D VOLUMETRIC GALAXY
    // =========================================================================
    private fun renderGalaxyMode(st: CosmosUiState, dt: Float, labels: MutableList<ProjectedLabel>) {
        val hdrScale = hdrPipeline.hdrEncodeScale
        targetTheta += dt * 0.045f
        val dampK = 1f - exp(-7.0f * dt)
        orbitTheta += (targetTheta - orbitTheta) * dampK
        orbitPhi += (targetPhi - orbitPhi) * dampK
        orbitRadius += (targetRadius - orbitRadius) * dampK

        val camX = orbitRadius * sin(orbitPhi) * cos(orbitTheta)
        val camY = orbitRadius * cos(orbitPhi)
        val camZ = orbitRadius * sin(orbitPhi) * sin(orbitTheta)

        val aspect = viewportW.toFloat() / viewportH.toFloat()
        Matrix.perspectiveM(projM, 0, 50f, aspect, 0.5f, 25_000f)
        Matrix.setLookAtM(viewM, 0, camX, camY, camZ, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(vpM, 0, projM, 0, viewM, 0)

        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)

        // 1. Distant universe background stars
        starsProg.use()
        starsProg.setMat4("uMvp", vpM)
        starsProg.setFloat("uTime", elapsedSec)
        starsProg.setFloat("uPixelRatio", pixelRatio)
        starsProg.setFloat("uVisibility", 0.58f)
        starsProg.setFloat("uTwinkle", 0.25f)
        starsProg.setFloat("uHdrScale", hdrScale)
        starfieldMesh.draw(GLES30.GL_POINTS)

        // 2. Volumetric Glowing Barred-Spiral Galactic Core & Nebula Disc
        galaxyDiscProg.use()
        galaxyDiscProg.setMat4("uMvp", vpM)
        galaxyDiscProg.setFloat("uTime", elapsedSec)
        galaxyDiscProg.setFloat("uHdrScale", hdrScale)
        galaxyDiscMesh.draw()

        // 3. 3D Volumetric Milky Way Spiral Starfield
        galaxyProg.use()
        galaxyProg.setMat4("uMvp", vpM)
        galaxyProg.setFloat("uPixelRatio", pixelRatio)
        galaxyProg.setFloat("uTime", elapsedSec)
        galaxyProg.setFloat("uHdrScale", hdrScale)
        galaxyMesh.draw(GLES30.GL_POINTS)

        if (st.settings.labels) {
            projectToLabel("gal_core", "مركز المجرة · Sgr A*", Vec3(0f, 5f, 0f), Color(0xFFFFBE82), isBlackHoleTag = true)?.let {
                labels.add(it)
            }
            projectToLabel("gal_sun", "أنت هنا · المنظومة الشمسية", Vec3(46f, 3f, 18f), Color(0xFF38E8FF))?.let {
                labels.add(it)
            }
            projectToLabel("gal_arm", "الذراع الجبّار (Orion Arm)", Vec3(-52f, 2f, 42f), Color(0xFFEBD091), isSubtle = true)?.let {
                labels.add(it)
            }
        }
    }

    // =========================================================================
    // MODE 5: KERR BLACK HOLE VOLUMETRIC GEODESIC RAY-MARCHING LAB
    // =========================================================================
    private fun renderBlackHoleMode(st: CosmosUiState, dt: Float, labels: MutableList<ProjectedLabel>) {
        val hdrScale = hdrPipeline.hdrEncodeScale
        val bh = st.blackHole
        val evR = AstrophysicsEngine.kerrEventHorizon(bh.spinA.toDouble()).toFloat()
        val iscoR = AstrophysicsEngine.kerrIsco(bh.spinA.toDouble()).toFloat()

        if (bh.divingToHorizon) {
            bhTargetRadius = max(evR * 1.12f, bhTargetRadius - dt * (1.28f + (bhTargetRadius - evR) * 0.24f))
            bhTargetTheta += dt * 0.36f
            bhTargetPhi = 1.44f + 0.08f * sin(elapsedSec * 0.6f)
        } else {
            bhTargetTheta += dt * 0.055f
        }

        val dampK = 1f - exp(-7.0f * dt)
        bhTheta += (bhTargetTheta - bhTheta) * dampK
        bhPhi += (bhTargetPhi - bhPhi) * dampK
        bhRadius += (bhTargetRadius - bhRadius) * dampK

        // Advance live Dual Relativistic Clocks (Earth coordinate time vs Spacecraft proper time dτ)
        val dilationRatio = AstrophysicsEngine.kerrTimeDilationPercent(bhRadius.toDouble(), bh.spinA.toDouble()) / 100.0
        bhEarthClockSec += dt.toDouble()
        bhShipClockSec += dt.toDouble() * dilationRatio

        val camX = bhRadius * sin(bhPhi) * cos(bhTheta)
        val camY = bhRadius * cos(bhPhi)
        val camZ = bhRadius * sin(bhPhi) * sin(bhTheta)
        val fwd = Vec3(-camX, -camY, -camZ).normalized()
        val right = cross(fwd, Vec3(0f, 1f, 0f)).normalized()
        val up = cross(right, fwd).normalized()

        val aspect = viewportW.toFloat() / viewportH.toFloat()
        val fovDeg = 52f
        val tanHalfFov = kotlin.math.tan(fovDeg * 0.5 * PI / 180.0).toFloat()

        s2Phase += dt * 0.36
        val s2Pos = AstrophysicsEngine.s2StarPosition(s2Phase)

        // 1. Fullscreen Kerr Geodesic Ray-Marcher with Volumetric Accretion Torus & Linear HDR Scaling
        val maxSteps = if (isSoftwareGpu) min(st.settings.quality.bhMaxSteps, 46) else min(st.settings.quality.bhMaxSteps, 84)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        bhProg.use()
        bhProg.setFloat("uTime", elapsedSec)
        bhProg.setFloat("uTanFov", tanHalfFov)
        bhProg.setFloat("uAspect", aspect)
        bhProg.setVec3("uCamPos", camX, camY, camZ)
        bhProg.setVec3("uCamFwd", fwd.x, fwd.y, fwd.z)
        bhProg.setVec3("uCamRight", right.x, right.y, right.z)
        bhProg.setVec3("uCamUp", up.x, up.y, up.z)
        bhProg.setFloat("uSpin", bh.spinA)
        bhProg.setFloat("uEv", evR)
        bhProg.setFloat("uIsco", iscoR)
        bhProg.setFloat("uAccretion", bh.accretionTemp)
        bhProg.setFloat("uLensing", bh.lensingStrength)
        bhProg.setFloat("uDoppler", if (bh.dopplerShift) 1f else 0f)
        bhProg.setFloat("uJets", if (bh.polarJets) 1f else 0f)
        bhProg.setFloat("uShowS2", if (bh.showS2Star) 1f else 0f)
        bhProg.setVec3("uS2Pos", s2Pos.x, s2Pos.y, s2Pos.z)
        bhProg.setInt("uMaxSteps", maxSteps)
        bhProg.setFloat("uHdrScale", hdrScale)
        fsMesh.draw()

        // 2. Relativistic Precessing Rosette Orbit Ribbon for Star S2
        Matrix.perspectiveM(projM, 0, fovDeg, aspect, 0.1f, 500f)
        Matrix.setLookAtM(viewM, 0, camX, camY, camZ, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(vpM, 0, projM, 0, viewM, 0)

        if (bh.showS2Star) {
            val segs = 130
            for (i in 0..segs) {
                val f = i.toFloat() / segs
                val p = AstrophysicsEngine.s2StarPosition(s2Phase - (1f - f) * 5.6)
                s2PointsBuf[i * 4] = p.x; s2PointsBuf[i * 4 + 1] = p.y; s2PointsBuf[i * 4 + 2] = p.z; s2PointsBuf[i * 4 + 3] = f
            }
            val vertCount = GlGeometry.uploadRibbonFromPoints(
                mesh = sharedRibbonMesh,
                points4 = s2PointsBuf,
                pointCount = segs + 1,
                camX = camX,
                camY = camY,
                camZ = camZ,
                baseHalfWidth = 0.24f,
                taperTail = true,
                outScratch = ribbonVertsScratch
            )
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
            ribbonProg.use()
            ribbonProg.setMat4("uMvp", vpM)
            ribbonProg.setFloat("uTime", elapsedSec)
            ribbonProg.setFloat("uHead", 1f)
            ribbonProg.setFloat("uClosed", 0f)
            ribbonProg.setFloat("uEnergy", 0.55f)
            ribbonProg.setVec3("uColor", 0.48f, 0.84f, 1.0f)
            ribbonProg.setFloat("uOpacity", 0.92f)
            ribbonProg.setFloat("uHdrScale", hdrScale)
            GLES30.glBindVertexArray(sharedRibbonMesh.vao)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, vertCount)
            GLES30.glBindVertexArray(0)
        }

        if (st.settings.labels) {
            projectToLabel("bh_eh", "أفق الحدث · ${bh.preset.badge}", Vec3(0f, evR * 1.45f, 0f), Color(0xFFFFBE82), isBlackHoleTag = true)?.let {
                labels.add(it)
            }
            if (bh.showS2Star) {
                projectToLabel("bh_s2", "النجم S2 النسبوي", s2Pos + Vec3(0f, 1.2f, 0f), Color(0xFF9BF2FF))?.let {
                    labels.add(it)
                }
            }
        }
    }

    private fun computeBlackHoleTelemetry(st: CosmosUiState): BlackHoleTelemetry {
        val spin = st.blackHole.spinA.toDouble()
        val r = bhRadius.toDouble()
        return BlackHoleTelemetry(
            distanceRs = r,
            timeDilationPercent = AstrophysicsEngine.kerrTimeDilationPercent(r, spin),
            escapeVelocityC = AstrophysicsEngine.kerrEscapeVelocityC(r, spin),
            eventHorizonRs = AstrophysicsEngine.kerrEventHorizon(spin),
            iscoRs = AstrophysicsEngine.kerrIsco(spin),
            shipClockSec = bhShipClockSec,
            earthClockSec = bhEarthClockSec
        )
    }

    private fun projectToLabel(
        id: String,
        text: String,
        worldPos: Vec3,
        color: Color,
        isSubtle: Boolean = false,
        isBlackHoleTag: Boolean = false
    ): ProjectedLabel? {
        tmpIn4[0] = worldPos.x; tmpIn4[1] = worldPos.y; tmpIn4[2] = worldPos.z; tmpIn4[3] = 1f
        Matrix.multiplyMV(tmpOut4, 0, vpM, 0, tmpIn4, 0)
        if (tmpOut4[3] <= 0.05f) return null
        val ndcX = tmpOut4[0] / tmpOut4[3]
        val ndcY = tmpOut4[1] / tmpOut4[3]
        val ndcZ = tmpOut4[2] / tmpOut4[3]
        if (ndcX < -0.96f || ndcX > 0.96f || ndcY < -0.92f || ndcY > 0.92f || ndcZ > 1.0f) return null
        return ProjectedLabel(
            id = id,
            text = text,
            xNorm = (ndcX * 0.5f + 0.5f),
            yNorm = (0.5f - ndcY * 0.5f),
            color = color,
            isSubtle = isSubtle,
            isBlackHoleTag = isBlackHoleTag
        )
    }

    private fun transformPoint(m: FloatArray, v: Vec3): Vec3 {
        tmpIn4[0] = v.x; tmpIn4[1] = v.y; tmpIn4[2] = v.z; tmpIn4[3] = 1f
        Matrix.multiplyMV(tmpOut4, 0, m, 0, tmpIn4, 0)
        return Vec3(tmpOut4[0], tmpOut4[1], tmpOut4[2])
    }

    private fun transformDir(m: FloatArray, v: Vec3): Vec3 {
        tmpIn4[0] = v.x; tmpIn4[1] = v.y; tmpIn4[2] = v.z; tmpIn4[3] = 0f
        Matrix.multiplyMV(tmpOut4, 0, m, 0, tmpIn4, 0)
        return Vec3(tmpOut4[0], tmpOut4[1], tmpOut4[2])
    }

    private fun cross(a: Vec3, b: Vec3): Vec3 = Vec3(
        a.y * b.z - a.z * b.y,
        a.z * b.x - a.x * b.z,
        a.x * b.y - a.y * b.x
    )
}
