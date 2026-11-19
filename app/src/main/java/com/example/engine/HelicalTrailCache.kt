package com.example.engine

import com.example.domain.AstrophysicsEngine
import com.example.domain.CelestialBodyId
import com.example.domain.Vec3
import kotlin.math.abs

/**
 * Zero-allocation sliding window cache for 3D Helical Galactic Vortex trails.
 *
 * Instead of evaluating 9 * 140 = 1,260 full VSOP87 planetary series per frame on the GL thread,
 * this cache maintains timestamped samples spanning [spanMs] per body and only evaluates newly
 * crossed time buckets (typically 0..2 calls per body per frame — >95% CPU reduction).
 * Both the tail (oldest sample) and head (live sub-frame planet position) are C0-continuously
 * interpolated every VSync frame so the 3D plasma ribbons glide with 60/120 FPS silkiness.
 */
class HelicalTrailCache(
    private val spanMs: Double = 920.0 * 86_400_000.0
) {
    private class BodyRing(val capacity: Int) {
        val epochs = DoubleArray(capacity)
        val xs = FloatArray(capacity)
        val ys = FloatArray(capacity)
        val zs = FloatArray(capacity)
        var headIdx = 0
        var count = 0
    }

    private val rings = HashMap<CelestialBodyId, BodyRing>()
    private var cachedTrueScale: Boolean? = null
    private var cachedRefEpochMs: Double = Double.NaN
    private var cachedSegments: Int = -1
    private var lastEpochMs: Double = Double.NaN
    private var lastDirection: Int = 1

    fun invalidate() {
        rings.clear()
        cachedTrueScale = null
        cachedRefEpochMs = Double.NaN
        cachedSegments = -1
        lastEpochMs = Double.NaN
    }

    /**
     * Fills [outPoints4] as packed (x, y, z, progress 0..1) vertices for [id] at [epochMs],
     * pinning the tip directly to [livePos] so the ribbon is seamlessly welded to the moving planet.
     * Returns the number of points written (up to segments + 2).
     */
    fun fillTrailPoints(
        id: CelestialBodyId,
        epochMs: Double,
        helicalRefEpochMs: Double,
        trueScale: Boolean,
        segments: Int,
        livePos: Vec3,
        outPoints4: FloatArray
    ): Int {
        val segs = segments.coerceIn(16, 146)
        val stepMs = spanMs / segs.toDouble()
        val ringCap = segs + 2

        val needsFullReset = cachedTrueScale != trueScale ||
            cachedSegments != segs ||
            cachedRefEpochMs.isNaN() ||
            abs(cachedRefEpochMs - helicalRefEpochMs) > 1.0 ||
            lastEpochMs.isNaN() ||
            abs(epochMs - lastEpochMs) > spanMs * 0.22

        if (needsFullReset) {
            rings.clear()
            cachedTrueScale = trueScale
            cachedRefEpochMs = helicalRefEpochMs
            cachedSegments = segs
            lastEpochMs = epochMs
            lastDirection = 1
        }

        val deltaEpoch = epochMs - lastEpochMs
        val currentDir = when {
            deltaEpoch > 1.0 -> 1
            deltaEpoch < -1.0 -> -1
            else -> lastDirection
        }

        val ring = rings.getOrPut(id) { BodyRing(ringCap) }

        if (ring.count < segs + 1 || (currentDir != lastDirection && abs(deltaEpoch) > stepMs * 0.5)) {
            rebuildRing(id, ring, epochMs, helicalRefEpochMs, trueScale, segs, stepMs, currentDir)
        } else if (currentDir > 0) {
            var newestEpoch = ring.epochs[ring.headIdx]
            var pushed = 0
            while (epochMs - newestEpoch >= stepMs && pushed < 16) {
                val nextBucket = newestEpoch + stepMs
                val p = AstrophysicsEngine.scenePositionContinuous(id, nextBucket, true, helicalRefEpochMs, trueScale)
                pushRing(ring, nextBucket, p)
                newestEpoch = nextBucket
                pushed++
            }
            if (pushed >= 16) {
                rebuildRing(id, ring, epochMs, helicalRefEpochMs, trueScale, segs, stepMs, 1)
            }
        } else {
            var newestEpoch = ring.epochs[ring.headIdx]
            var pushed = 0
            while (newestEpoch - epochMs >= stepMs && pushed < 16) {
                val nextBucket = newestEpoch - stepMs
                val p = AstrophysicsEngine.scenePositionContinuous(id, nextBucket, true, helicalRefEpochMs, trueScale)
                pushRing(ring, nextBucket, p)
                newestEpoch = nextBucket
                pushed++
            }
            if (pushed >= 16) {
                rebuildRing(id, ring, epochMs, helicalRefEpochMs, trueScale, segs, stepMs, -1)
            }
        }

        // Exact C0-continuous extraction:
        // Bucket A = headIdx - segs has epoch E_head - spanMs; Bucket B = headIdx - segs + 1 is E_A + stepMs.
        // Because 0 <= |epochMs - E_head| < stepMs, oldestTargetEpoch ALWAYS lies strictly inside [E_A, E_B)!
        val oldestTargetEpoch = if (currentDir >= 0) epochMs - spanMs else epochMs + spanMs
        val cap = ring.capacity
        val idxA = (ring.headIdx - segs + cap * 4) % cap
        val idxB = (idxA + 1) % cap

        val eA = ring.epochs[idxA]
        val eB = ring.epochs[idxB]
        val frac0 = if (abs(eB - eA) > 1e-3) {
            ((oldestTargetEpoch - eA) / (eB - eA)).toFloat().coerceIn(0f, 1f)
        } else 0f

        var outCount = 0
        // Vertex 0: smoothly sliding tail point at progress = 0.0
        outPoints4[0] = ring.xs[idxA] + (ring.xs[idxB] - ring.xs[idxA]) * frac0
        outPoints4[1] = ring.ys[idxA] + (ring.ys[idxB] - ring.ys[idxA]) * frac0
        outPoints4[2] = ring.zs[idxA] + (ring.zs[idxB] - ring.zs[idxA]) * frac0
        outPoints4[3] = 0f
        outCount++

        // Interior vertices: exact fixed buckets from (headIdx - segs + 1) up to headIdx
        val invSpan = if (currentDir >= 0) 1.0 / spanMs else -1.0 / spanMs
        for (k in 1..segs) {
            val idx = (idxA + k) % cap
            val prog = ((ring.epochs[idx] - oldestTargetEpoch) * invSpan).toFloat().coerceIn(0f, 1f)
            outPoints4[outCount * 4] = ring.xs[idx]
            outPoints4[outCount * 4 + 1] = ring.ys[idx]
            outPoints4[outCount * 4 + 2] = ring.zs[idx]
            outPoints4[outCount * 4 + 3] = prog
            outCount++
        }

        // Tip vertex: welded directly to livePos at progress = 1.0 (zero gap!)
        outPoints4[outCount * 4] = livePos.x
        outPoints4[outCount * 4 + 1] = livePos.y
        outPoints4[outCount * 4 + 2] = livePos.z
        outPoints4[outCount * 4 + 3] = 1f
        outCount++

        return outCount
    }

    fun commitFrame(epochMs: Double) {
        if (!lastEpochMs.isNaN()) {
            val d = epochMs - lastEpochMs
            if (d > 1.0) lastDirection = 1 else if (d < -1.0) lastDirection = -1
        }
        lastEpochMs = epochMs
    }

    private fun rebuildRing(
        id: CelestialBodyId,
        ring: BodyRing,
        epochMs: Double,
        helicalRefEpochMs: Double,
        trueScale: Boolean,
        segs: Int,
        stepMs: Double,
        dir: Int
    ) {
        ring.count = 0
        ring.headIdx = ring.capacity - 1
        val signedStep = if (dir >= 0) stepMs else -stepMs
        val startEpoch = epochMs - signedStep * segs.toDouble()
        for (i in 0..segs) {
            val t = startEpoch + signedStep * i.toDouble()
            val p = AstrophysicsEngine.scenePositionContinuous(id, t, true, helicalRefEpochMs, trueScale)
            pushRing(ring, t, p)
        }
    }

    private fun pushRing(ring: BodyRing, epochMs: Double, p: Vec3) {
        ring.headIdx = (ring.headIdx + 1) % ring.capacity
        ring.epochs[ring.headIdx] = epochMs
        ring.xs[ring.headIdx] = p.x
        ring.ys[ring.headIdx] = p.y
        ring.zs[ring.headIdx] = p.z
        if (ring.count < ring.capacity) ring.count++
    }
}
