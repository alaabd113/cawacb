package com.example.engine

import android.opengl.GLES30
import com.example.domain.AstrophysicsEngine
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class MeshVao(
    val vao: Int,
    val vbo: Int,
    val ibo: Int,
    val count: Int,
    val indexed: Boolean
) {
    fun draw(mode: Int = GLES30.GL_TRIANGLES) {
        GLES30.glBindVertexArray(vao)
        if (indexed) {
            GLES30.glDrawElements(mode, count, GLES30.GL_UNSIGNED_INT, 0)
        } else {
            GLES30.glDrawArrays(mode, 0, count)
        }
        GLES30.glBindVertexArray(0)
    }
}

object GlGeometry {

    fun createFullscreenTriangle(): MeshVao {
        val verts = floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f)
        val vao = genVao()
        val vbo = genBuf()
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, verts.size * 4, GlUtils.createFloatBuffer(verts), GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)
        GLES30.glBindVertexArray(0)
        return MeshVao(vao, vbo, 0, 3, false)
    }

    fun createBillboardQuad(): MeshVao {
        val verts = floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f, -1f, -1f, 1f, 1f, -1f, 1f)
        val vao = genVao()
        val vbo = genBuf()
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, verts.size * 4, GlUtils.createFloatBuffer(verts), GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)
        GLES30.glBindVertexArray(0)
        return MeshVao(vao, vbo, 0, 6, false)
    }

    /** UV sphere: pos(3), norm(3), uv(2) = 8 floats per vertex. */
    fun createSphere(lonSegs: Int = 96, latSegs: Int = 64): MeshVao {
        val vCount = (lonSegs + 1) * (latSegs + 1)
        val verts = FloatArray(vCount * 8)
        var vi = 0
        for (y in 0..latSegs) {
            val v = y.toFloat() / latSegs
            val phi = v * PI
            val sinPhi = sin(phi).toFloat()
            val cosPhi = cos(phi).toFloat()
            for (x in 0..lonSegs) {
                val u = x.toFloat() / lonSegs
                val theta = u * 2.0 * PI
                val nx = (cos(theta) * sinPhi).toFloat()
                val ny = cosPhi
                val nz = (-sin(theta) * sinPhi).toFloat()
                verts[vi++] = nx; verts[vi++] = ny; verts[vi++] = nz
                verts[vi++] = nx; verts[vi++] = ny; verts[vi++] = nz
                verts[vi++] = u; verts[vi++] = v
            }
        }
        val indices = IntArray(lonSegs * latSegs * 6)
        var ii = 0
        for (y in 0 until latSegs) {
            for (x in 0 until lonSegs) {
                val a = y * (lonSegs + 1) + x
                val b = a + lonSegs + 1
                indices[ii++] = a; indices[ii++] = b; indices[ii++] = a + 1
                indices[ii++] = b; indices[ii++] = b + 1; indices[ii++] = a + 1
            }
        }

        val vao = genVao(); val vbo = genBuf(); val ibo = genBuf()
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, verts.size * 4, GlUtils.createFloatBuffer(verts), GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 4, GlUtils.createIntBuffer(indices), GLES30.GL_STATIC_DRAW)
        val stride = 8 * 4
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 12)
        GLES30.glEnableVertexAttribArray(2); GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, stride, 24)
        GLES30.glBindVertexArray(0)
        return MeshVao(vao, vbo, ibo, indices.size, true)
    }

    /** Flat annular ring in XZ plane: pos(3). */
    fun createRing(innerR: Float, outerR: Float, segs: Int = 180): MeshVao {
        val verts = FloatArray((segs + 1) * 2 * 3)
        var vi = 0
        for (i in 0..segs) {
            val a = (i.toDouble() / segs) * 2.0 * PI
            val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
            verts[vi++] = ca * innerR; verts[vi++] = 0f; verts[vi++] = sa * innerR
            verts[vi++] = ca * outerR; verts[vi++] = 0f; verts[vi++] = sa * outerR
        }
        val indices = IntArray(segs * 6)
        var ii = 0
        for (i in 0 until segs) {
            val a = i * 2
            indices[ii++] = a; indices[ii++] = a + 1; indices[ii++] = a + 2
            indices[ii++] = a + 1; indices[ii++] = a + 3; indices[ii++] = a + 2
        }
        val vao = genVao(); val vbo = genBuf(); val ibo = genBuf()
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, verts.size * 4, GlUtils.createFloatBuffer(verts), GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 4, GlUtils.createIntBuffer(indices), GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0)
        GLES30.glBindVertexArray(0)
        return MeshVao(vao, vbo, ibo, indices.size, true)
    }

    /** Tessellated XZ plane for Einstein spacetime gravity curvature grid. */
    fun createSpacetimePlane(size: Float = 3600f, div: Int = 128): MeshVao {
        val verts = FloatArray((div + 1) * (div + 1) * 3)
        var vi = 0
        val half = size * 0.5f
        for (z in 0..div) {
            val fz = z.toFloat() / div
            val pz = (fz - 0.5f) * size
            for (x in 0..div) {
                val fx = x.toFloat() / div
                verts[vi++] = fx * size - half
                verts[vi++] = 0f
                verts[vi++] = pz
            }
        }
        val indices = IntArray(div * div * 6)
        var ii = 0
        for (z in 0 until div) {
            for (x in 0 until div) {
                val a = z * (div + 1) + x
                val b = a + div + 1
                indices[ii++] = a; indices[ii++] = b; indices[ii++] = a + 1
                indices[ii++] = b; indices[ii++] = b + 1; indices[ii++] = a + 1
            }
        }
        val vao = genVao(); val vbo = genBuf(); val ibo = genBuf()
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, verts.size * 4, GlUtils.createFloatBuffer(verts), GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 4, GlUtils.createIntBuffer(indices), GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0)
        GLES30.glBindVertexArray(0)
        return MeshVao(vao, vbo, ibo, indices.size, true)
    }

    /** Deep-space background starfield (bright catalog stars + 3,600 Milky Way field stars): pos(3), color(3), size(1), seed(1). */
    fun createStarfield(radius: Float = 18_000f): MeshVao {
        val rng = Random(1337)
        val fieldCount = 3600
        val total = AstrophysicsEngine.BRIGHT_STARS.size + fieldCount
        val data = FloatArray(total * 8)
        var idx = 0

        for (st in AstrophysicsEngine.BRIGHT_STARS) {
            val ra = st.raDeg * PI / 180.0
            val dec = st.decDeg * PI / 180.0
            val x = (radius * cos(dec) * cos(ra)).toFloat()
            val y = (radius * sin(dec)).toFloat()
            val z = (-radius * cos(dec) * sin(ra)).toFloat()
            val (r, g, b) = AstrophysicsEngine.bvToRgb(st.bv)
            val sz = (7.5f - st.mag * 1.4f).coerceIn(3.5f, 11.5f)
            data[idx++] = x; data[idx++] = y; data[idx++] = z
            data[idx++] = r * 1.6f; data[idx++] = g * 1.6f; data[idx++] = b * 1.6f
            data[idx++] = sz; data[idx++] = rng.nextFloat()
        }

        for (i in 0 until fieldCount) {
            // Concentrate 45% along galactic plane for luminous Milky Way arc
            val inBand = rng.nextFloat() < 0.45f
            val u = rng.nextDouble() * 2.0 * PI
            val v = if (inBand) {
                (rng.nextDouble() - 0.5) * 0.24 + (rng.nextDouble() - 0.5) * 0.14
            } else {
                kotlin.math.asin(rng.nextDouble() * 2.0 - 1.0)
            }
            // Tilt galactic plane nicely in equatorial coordinates
            val gx = cos(v) * cos(u)
            val gy = sin(v)
            val gz = cos(v) * sin(u)
            val tilt = 1.08
            val rx = gx
            val ry = gy * cos(tilt) - gz * sin(tilt)
            val rz = gy * sin(tilt) + gz * cos(tilt)

            val bv = (rng.nextFloat() * 2.1f - 0.35f)
            val (r, g, b) = AstrophysicsEngine.bvToRgb(bv)
            val lum = (0.28f + rng.nextFloat().pow(2.4f) * 1.25f) * if (inBand) 1.15f else 0.92f
            val sz = 1.6f + rng.nextFloat().pow(3.0f) * 4.4f
            data[idx++] = (rx * radius).toFloat()
            data[idx++] = (ry * radius).toFloat()
            data[idx++] = (rz * radius).toFloat()
            data[idx++] = r * lum; data[idx++] = g * lum; data[idx++] = b * lum
            data[idx++] = sz; data[idx++] = rng.nextFloat()
        }

        val vao = genVao(); val vbo = genBuf()
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, GlUtils.createFloatBuffer(data), GLES30.GL_STATIC_DRAW)
        val stride = 8 * 4
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 12)
        GLES30.glEnableVertexAttribArray(2); GLES30.glVertexAttribPointer(2, 1, GLES30.GL_FLOAT, false, stride, 24)
        GLES30.glEnableVertexAttribArray(3); GLES30.glVertexAttribPointer(3, 1, GLES30.GL_FLOAT, false, stride, 28)
        GLES30.glBindVertexArray(0)
        return MeshVao(vao, vbo, 0, total, false)
    }

    /** Volumetric 22,000-star barred spiral Milky Way 3D galaxy: pos(3), color(3), size(1). */
    fun createMilkyWayGalaxy(count: Int = 22_000): MeshVao {
        val rng = Random(2026)
        val data = FloatArray(count * 7)
        var idx = 0
        val arms = 4
        val maxR = 95f

        for (i in 0 until count) {
            val u = rng.nextFloat()
            val isCore = u < 0.22f
            val isNebula = !isCore && u > 0.91f

            val x: Float; val y: Float; val z: Float
            val cr: Float; val cg: Float; val cb: Float; val sz: Float

            if (isCore) {
                val r = (-ln(rng.nextFloat().coerceAtLeast(1e-4f)) * 9.5f).coerceAtMost(24f)
                val ang = rng.nextFloat() * 2f * PI.toFloat()
                // Barred galactic bulge
                x = cos(ang) * r * 1.45f
                z = sin(ang) * r * 0.72f
                y = (rng.nextFloat() - 0.5f) * (9.5f * kotlin.math.exp(-r * 0.08f))
                val heat = (1f - r / 26f).coerceIn(0f, 1f)
                cr = 1.0f * (0.9f + 0.5f * heat)
                cg = (0.72f + 0.24f * heat) * 1.15f
                cb = (0.40f + 0.38f * heat)
                sz = 2.6f + rng.nextFloat() * 4.5f
            } else {
                val armIdx = i % arms
                val r = (rng.nextFloat().pow(0.75f) * maxR).coerceAtLeast(4f)
                val spinAngle = r * 0.088f
                val branchAngle = (armIdx.toFloat() / arms) * 2f * PI.toFloat()
                val scatter = (rng.nextFloat() - 0.5f) * (0.55f + r * 0.008f)
                val angle = branchAngle + spinAngle + scatter
                x = cos(angle) * r
                z = sin(angle) * r
                val diskH = 1.8f + 4.5f * kotlin.math.exp(-r * 0.045f)
                y = (rng.nextFloat() - 0.5f) * diskH

                if (isNebula) {
                    // Rose-magenta H-II star-forming nurseries along spiral arms
                    cr = 1.15f; cg = 0.34f; cb = 0.72f
                    sz = 6.5f + rng.nextFloat() * 6.0f
                } else {
                    val t = (r / maxR).coerceIn(0f, 1f)
                    cr = 0.96f * (1f - t) + 0.38f * t
                    cg = 0.80f * (1f - t) + 0.68f * t
                    cb = 0.56f * (1f - t) + 1.08f * t
                    val lum = 0.45f + rng.nextFloat().pow(2.2f) * 1.1f
                    sz = 1.8f + rng.nextFloat().pow(2.6f) * 3.8f
                    // Modulate brightness
                    data[idx++] = x; data[idx++] = y; data[idx++] = z
                    data[idx++] = cr * lum; data[idx++] = cg * lum; data[idx++] = cb * lum
                    data[idx++] = sz
                    continue
                }
            }
            data[idx++] = x; data[idx++] = y; data[idx++] = z
            data[idx++] = cr; data[idx++] = cg; data[idx++] = cb
            data[idx++] = sz
        }

        val vao = genVao(); val vbo = genBuf()
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, GlUtils.createFloatBuffer(data), GLES30.GL_STATIC_DRAW)
        val stride = 7 * 4
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 12)
        GLES30.glEnableVertexAttribArray(2); GLES30.glVertexAttribPointer(2, 1, GLES30.GL_FLOAT, false, stride, 24)
        GLES30.glBindVertexArray(0)
        return MeshVao(vao, vbo, 0, count, false)
    }

    /** 5,000 Keplerian Main Asteroid Belt particles: orbit(4), color(3). */
    fun createAsteroidBelt(count: Int = 5000): MeshVao {
        val rng = Random(777)
        val data = FloatArray(count * 7)
        var idx = 0
        for (i in 0 until count) {
            data[idx++] = 2.08f + rng.nextFloat() * 1.18f
            data[idx++] = rng.nextFloat() * 0.16f
            data[idx++] = (rng.nextFloat() - 0.5f) * 0.30f
            data[idx++] = rng.nextFloat() * 6.28318f
            val warm = rng.nextFloat() < 0.38f
            val sh = 0.55f + rng.nextFloat() * 0.45f
            data[idx++] = sh * if (warm) 0.94f else 0.70f
            data[idx++] = sh * if (warm) 0.80f else 0.66f
            data[idx++] = sh * if (warm) 0.64f else 0.62f
        }
        val vao = genVao(); val vbo = genBuf()
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, GlUtils.createFloatBuffer(data), GLES30.GL_STATIC_DRAW)
        val stride = 7 * 4
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 4, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 16)
        GLES30.glBindVertexArray(0)
        return MeshVao(vao, vbo, 0, count, false)
    }

    /** 1,400 3D parallax cosmic dust motes: pos(3), color(3). */
    fun createCosmicDust(count: Int = 1400): MeshVao {
        val rng = Random(909)
        val data = FloatArray(count * 6)
        var idx = 0
        for (i in 0 until count) {
            data[idx++] = (rng.nextFloat() - 0.5f) * 60f
            data[idx++] = (rng.nextFloat() - 0.5f) * 60f
            data[idx++] = (rng.nextFloat() - 0.5f) * 60f
            val gold = rng.nextFloat() < 0.45f
            data[idx++] = if (gold) 0.96f else 0.55f
            data[idx++] = if (gold) 0.86f else 0.82f
            data[idx++] = if (gold) 0.64f else 1.0f
        }
        val vao = genVao(); val vbo = genBuf()
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, GlUtils.createFloatBuffer(data), GLES30.GL_STATIC_DRAW)
        val stride = 6 * 4
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 12)
        GLES30.glBindVertexArray(0)
        return MeshVao(vao, vbo, 0, count, false)
    }

    /** Dynamic line VAO for orbits, helical wakes, constellations, and S2 star rosette: pos(3), prog(1). */
    fun createDynamicTrail(maxVerts: Int): MeshVao {
        val vao = genVao(); val vbo = genBuf()
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, maxVerts * 4 * 4, null, GLES30.GL_DYNAMIC_DRAW)
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 16, 0)
        GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 1, GLES30.GL_FLOAT, false, 16, 12)
        GLES30.glBindVertexArray(0)
        return MeshVao(vao, vbo, 0, maxVerts, false)
    }

    private var trailScratchBuf: java.nio.FloatBuffer =
        java.nio.ByteBuffer.allocateDirect(512 * 4 * 4)
            .order(java.nio.ByteOrder.nativeOrder())
            .asFloatBuffer()

    fun uploadTrailVertices(mesh: MeshVao, data: FloatArray, vertexCount: Int) {
        val floatCount = vertexCount * 4
        if (trailScratchBuf.capacity() < floatCount) {
            trailScratchBuf = java.nio.ByteBuffer.allocateDirect(floatCount * 4)
                .order(java.nio.ByteOrder.nativeOrder())
                .asFloatBuffer()
        }
        trailScratchBuf.clear()
        trailScratchBuf.put(data, 0, floatCount)
        trailScratchBuf.flip()
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mesh.vbo)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, floatCount * 4, trailScratchBuf)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    /**
     * Volumetric 3D camera-facing plasma ribbon VAO: pos(3), side(1 in [-1..+1]), prog(1 in [0..1]) = 5 floats/vert.
     * Drawn as GL_TRIANGLE_STRIP with 2 * pointCount vertices.
     */
    fun createDynamicRibbon(maxPoints: Int): MeshVao {
        val maxVerts = maxPoints * 2
        val vao = genVao(); val vbo = genBuf()
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, maxVerts * 5 * 4, null, GLES30.GL_DYNAMIC_DRAW)
        val stride = 5 * 4
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, stride, 12)
        GLES30.glBindVertexArray(0)
        return MeshVao(vao, vbo, 0, maxVerts, false)
    }

    private var ribbonScratchBuf: java.nio.FloatBuffer =
        java.nio.ByteBuffer.allocateDirect(512 * 2 * 5 * 4)
            .order(java.nio.ByteOrder.nativeOrder())
            .asFloatBuffer()

    /**
     * Builds a camera-facing 3D triangle-strip ribbon from polyline points (packed x,y,z,prog) and uploads to GPU.
     * Returns actual vertex count to draw with GL_TRIANGLE_STRIP.
     */
    fun uploadRibbonFromPoints(
        mesh: MeshVao,
        points4: FloatArray,
        pointCount: Int,
        camX: Float,
        camY: Float,
        camZ: Float,
        baseHalfWidth: Float,
        taperTail: Boolean,
        outScratch: FloatArray
    ): Int {
        if (pointCount < 2) return 0
        var outIdx = 0
        for (i in 0 until pointCount) {
            val pi = i * 4
            val px = points4[pi]; val py = points4[pi + 1]; val pz = points4[pi + 2]
            val prog = points4[pi + 3]

            val prevIdx = if (i > 0) (i - 1) * 4 else pi
            val nextIdx = if (i < pointCount - 1) (i + 1) * 4 else pi
            var tx = points4[nextIdx] - points4[prevIdx]
            var ty = points4[nextIdx + 1] - points4[prevIdx + 1]
            var tz = points4[nextIdx + 2] - points4[prevIdx + 2]
            val tLen = sqrt(tx * tx + ty * ty + tz * tz).coerceAtLeast(1e-5f)
            tx /= tLen; ty /= tLen; tz /= tLen

            // View vector from point to camera
            var vx = camX - px; var vy = camY - py; var vz = camZ - pz
            val vLen = sqrt(vx * vx + vy * vy + vz * vz).coerceAtLeast(1e-5f)
            vx /= vLen; vy /= vLen; vz /= vLen

            // Side vector perpendicular to tangent and camera view
            var sx = ty * vz - tz * vy
            var sy = tz * vx - tx * vz
            var sz = tx * vy - ty * vx
            val sLen = sqrt(sx * sx + sy * sy + sz * sz)
            if (sLen < 1e-4f) {
                sx = 1f; sy = 0f; sz = 0f
            } else {
                sx /= sLen; sy /= sLen; sz /= sLen
            }

            // Distance-adaptive half-width so ribbon always looks lush & substantial, tapering elegantly toward tail
            val distBoost = (vLen * 0.0042f).coerceIn(0.45f, 3.6f)
            val widthFactor = if (taperTail) (0.28f + 0.92f * prog.pow(0.85f)) else 0.72f
            val hw = baseHalfWidth * widthFactor * distBoost

            outScratch[outIdx++] = px - sx * hw
            outScratch[outIdx++] = py - sy * hw
            outScratch[outIdx++] = pz - sz * hw
            outScratch[outIdx++] = -1f
            outScratch[outIdx++] = prog

            outScratch[outIdx++] = px + sx * hw
            outScratch[outIdx++] = py + sy * hw
            outScratch[outIdx++] = pz + sz * hw
            outScratch[outIdx++] = 1f
            outScratch[outIdx++] = prog
        }

        val vertCount = pointCount * 2
        if (ribbonScratchBuf.capacity() < outIdx) {
            ribbonScratchBuf = java.nio.ByteBuffer.allocateDirect(outIdx * 4)
                .order(java.nio.ByteOrder.nativeOrder())
                .asFloatBuffer()
        }
        ribbonScratchBuf.clear()
        ribbonScratchBuf.put(outScratch, 0, outIdx)
        ribbonScratchBuf.flip()
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mesh.vbo)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, outIdx * 4, ribbonScratchBuf)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        return vertCount
    }

    /**
     * Graceful 3D Heliosphere Bow-Shock Paraboloid shell aligned along +Y apex:
     * pos(3), uv(2) where uv.y=0 at apex nose and uv.y=1 at trailing skirt.
     */
    fun createBowShockParaboloid(radSegs: Int = 48, rings: Int = 20): MeshVao {
        val verts = FloatArray((radSegs + 1) * (rings + 1) * 5)
        var vi = 0
        for (j in 0..rings) {
            val v = j.toFloat() / rings
            // Paraboloid nose at y = +22, sweeping back to y = -135 with radius expanding smoothly
            val radius = 95f * sqrt(v.coerceAtLeast(0.0005f))
            val y = 24f - 150f * v * v
            for (i in 0..radSegs) {
                val u = i.toFloat() / radSegs
                val ang = u * 2.0 * PI
                verts[vi++] = (cos(ang) * radius).toFloat()
                verts[vi++] = y
                verts[vi++] = (sin(ang) * radius).toFloat()
                verts[vi++] = u
                verts[vi++] = v
            }
        }
        val indices = IntArray(radSegs * rings * 6)
        var ii = 0
        for (j in 0 until rings) {
            for (i in 0 until radSegs) {
                val a = j * (radSegs + 1) + i
                val b = a + radSegs + 1
                indices[ii++] = a; indices[ii++] = b; indices[ii++] = a + 1
                indices[ii++] = b; indices[ii++] = b + 1; indices[ii++] = a + 1
            }
        }
        val vao = genVao(); val vbo = genBuf(); val ibo = genBuf()
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, verts.size * 4, GlUtils.createFloatBuffer(verts), GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 4, GlUtils.createIntBuffer(indices), GLES30.GL_STATIC_DRAW)
        val stride = 5 * 4
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, stride, 12)
        GLES30.glBindVertexArray(0)
        return MeshVao(vao, vbo, ibo, indices.size, true)
    }

    /**
     * 3D Galactic Velocity Star-Streaks rushing backward opposite HELIX_DIR in Helical mode.
     * Pairs of vertices (head, tail): baseOffset(3), isTail(1), seed(1) = 5 floats/vert.
     */
    fun createVelocityStreaks(streakCount: Int = 260): MeshVao {
        val rng = Random(4207)
        val data = FloatArray(streakCount * 2 * 5)
        var idx = 0
        for (i in 0 until streakCount) {
            val ox = (rng.nextFloat() - 0.5f) * 900f
            val oy = (rng.nextFloat() - 0.5f) * 900f
            val oz = (rng.nextFloat() - 0.5f) * 900f
            val seed = rng.nextFloat()
            // Head vertex
            data[idx++] = ox; data[idx++] = oy; data[idx++] = oz
            data[idx++] = 0f; data[idx++] = seed
            // Tail vertex
            data[idx++] = ox; data[idx++] = oy; data[idx++] = oz
            data[idx++] = 1f; data[idx++] = seed
        }
        val vao = genVao(); val vbo = genBuf()
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, GlUtils.createFloatBuffer(data), GLES30.GL_STATIC_DRAW)
        val stride = 5 * 4
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, stride, 12)
        GLES30.glBindVertexArray(0)
        return MeshVao(vao, vbo, 0, streakCount * 2, false)
    }

    /** Flat XZ quad for volumetric glowing Milky Way core & spiral nebula disc: pos(3), uv(2). */
    fun createGalaxyDiscQuad(radius: Float = 118f): MeshVao {
        val verts = floatArrayOf(
            -radius, 0f, -radius, -1f, -1f,
             radius, 0f, -radius,  1f, -1f,
             radius, 0f,  radius,  1f,  1f,
            -radius, 0f, -radius, -1f, -1f,
             radius, 0f,  radius,  1f,  1f,
            -radius, 0f,  radius, -1f,  1f
        )
        val vao = genVao(); val vbo = genBuf()
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, verts.size * 4, GlUtils.createFloatBuffer(verts), GLES30.GL_STATIC_DRAW)
        val stride = 5 * 4
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, stride, 12)
        GLES30.glBindVertexArray(0)
        return MeshVao(vao, vbo, 0, 6, false)
    }

    private fun genVao(): Int {
        val a = IntArray(1); GLES30.glGenVertexArrays(1, a, 0); return a[0]
    }

    private fun genBuf(): Int {
        val a = IntArray(1); GLES30.glGenBuffers(1, a, 0); return a[0]
    }
}
