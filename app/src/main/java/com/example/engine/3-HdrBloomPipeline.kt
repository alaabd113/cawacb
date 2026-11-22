package com.example.engine

import android.opengl.GLES30

class HdrBloomPipeline(
    private val fullscreenMesh: MeshVao,
    private val preProg: GlProgram,
    private val blurProg: GlProgram,
    private val compProg: GlProgram
) {
    private var sceneFbo = 0
    private var sceneTex = 0
    private var sceneDepth = 0

    private var bloomFboA = 0
    private var bloomTexA = 0
    private var bloomFboB = 0
    private var bloomTexB = 0

    var renderWidth = 0
        private set
    var renderHeight = 0
        private set
    private var bloomW = 0
    private var bloomH = 0
    private var screenW = 0
    private var screenH = 0
    private var valid = false

    private var currentHalfFloat = true

    /** Linear HDR encode multiplier for scene shaders (1.0 on RGBA16F, 0.25 on RGBA8 so [0..4.0] HDR never clips). */
    val hdrEncodeScale: Float
        get() = if (currentHalfFloat) 1.0f else 0.25f

    /** Inverse multiplier to restore linear HDR radiance in bloom prefilter & ACES composite. */
    val hdrDecodeScale: Float
        get() = if (currentHalfFloat) 1.0f else 4.0f

    fun resize(
        screenWidth: Int,
        screenHeight: Int,
        scale: Float,
        maxLongEdgePx: Int = 1440,
        preferHalfFloat: Boolean = true
    ) {
        screenW = screenWidth.coerceAtLeast(2)
        screenH = screenHeight.coerceAtLeast(2)
        var rw = (screenW * scale).toInt().coerceAtLeast(2)
        var rh = (screenH * scale).toInt().coerceAtLeast(2)
        val longEdge = maxOf(rw, rh)
        if (longEdge > maxLongEdgePx) {
            val clampRatio = maxLongEdgePx.toFloat() / longEdge.toFloat()
            rw = (rw * clampRatio).toInt().coerceAtLeast(2)
            rh = (rh * clampRatio).toInt().coerceAtLeast(2)
        }
        // Align to even pixel dimensions
        rw = (rw and 1.inv()).coerceAtLeast(2)
        rh = (rh and 1.inv()).coerceAtLeast(2)
        if (valid && rw == renderWidth && rh == renderHeight && preferHalfFloat == currentHalfFloat) return

        release()
        renderWidth = rw
        renderHeight = rh
        bloomW = (rw / 4).coerceAtLeast(2)
        bloomH = (rh / 4).coerceAtLeast(2)

        // Try HDR RGBA16F on hardware GPUs; use linear-packed RGBA8 on software/emulated GPUs
        if (preferHalfFloat && createTargets(useHalfFloat = true)) {
            currentHalfFloat = true
            valid = true
        } else {
            currentHalfFloat = false
            valid = createTargets(useHalfFloat = false)
        }
    }

    private fun createTargets(useHalfFloat: Boolean): Boolean {
        val internalFmt = if (useHalfFloat) GLES30.GL_RGBA16F else GLES30.GL_RGBA8
        val dataType = if (useHalfFloat) GLES30.GL_HALF_FLOAT else GLES30.GL_UNSIGNED_BYTE

        sceneFbo = genFbo()
        sceneTex = createColorTex(renderWidth, renderHeight, internalFmt, dataType)
        sceneDepth = genRbo()
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, sceneDepth)
        GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT24, renderWidth, renderHeight)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, sceneFbo)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, sceneTex, 0)
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, sceneDepth)

        val sceneStatus = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (sceneStatus != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            release()
            return false
        }

        bloomFboA = genFbo()
        bloomTexA = createColorTex(bloomW, bloomH, internalFmt, dataType)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, bloomFboA)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, bloomTexA, 0)

        bloomFboB = genFbo()
        bloomTexB = createColorTex(bloomW, bloomH, internalFmt, dataType)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, bloomFboB)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, bloomTexB, 0)

        val ok = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        if (!ok) release()
        return ok
    }

    fun beginScenePass() {
        if (valid) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, sceneFbo)
            GLES30.glViewport(0, 0, renderWidth, renderHeight)
        } else {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glViewport(0, 0, screenW, screenH)
        }
        val bg = hdrEncodeScale
        GLES30.glClearColor(0.004f * bg, 0.007f * bg, 0.016f * bg, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
    }

    fun finishAndPresent(
        bloomEnabled: Boolean,
        exposure: Float,
        timeSec: Float,
        blurIterations: Int = 2
    ) {
        if (!valid) return

        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDisable(GLES30.GL_CULL_FACE)

        val dec = hdrDecodeScale
        val enc = hdrEncodeScale

        if (bloomEnabled) {
            // 1. Prefilter bright HDR highlights into bloomA
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, bloomFboA)
            GLES30.glViewport(0, 0, bloomW, bloomH)
            preProg.use()
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sceneTex)
            preProg.setInt("uScene", 0)
            preProg.setVec2("uTexel", 1f / renderWidth, 1f / renderHeight)
            preProg.setFloat("uHdrDecode", dec)
            preProg.setFloat("uHdrEncode", enc)
            fullscreenMesh.draw()

            // 2. Separable Gaussian blur passes
            blurProg.use()
            blurProg.setInt("uSrc", 0)
            val iters = blurIterations.coerceIn(1, 2)
            for (pass in 0 until iters) {
                val spread = if (pass == 0) 1.55f else 2.85f
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, bloomFboB)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, bloomTexA)
                blurProg.setVec2("uDir", spread / bloomW, 0f)
                fullscreenMesh.draw()

                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, bloomFboA)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, bloomTexB)
                blurProg.setVec2("uDir", 0f, spread / bloomH)
                fullscreenMesh.draw()
            }
        }

        // 3. Final ACES Filmic Tone Mapping + Micro-Sharpen + Subtle Chromatic Aberration + Dither
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, screenW, screenH)
        compProg.use()
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sceneTex)
        compProg.setInt("uScene", 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (bloomEnabled) bloomTexA else sceneTex)
        compProg.setInt("uBloom", 1)
        compProg.setVec2("uTexel", 1f / renderWidth, 1f / renderHeight)
        compProg.setFloat("uBloomStrength", if (bloomEnabled) 0.35f else 0f)
        compProg.setFloat("uExposure", exposure)
        compProg.setFloat("uTime", timeSec)
        compProg.setFloat("uHdrDecode", dec)
        fullscreenMesh.draw()
    }

    fun release() {
        val fbos = intArrayOf(sceneFbo, bloomFboA, bloomFboB).filter { it != 0 }.toIntArray()
        if (fbos.isNotEmpty()) GLES30.glDeleteFramebuffers(fbos.size, fbos, 0)
        val texs = intArrayOf(sceneTex, bloomTexA, bloomTexB).filter { it != 0 }.toIntArray()
        if (texs.isNotEmpty()) GLES30.glDeleteTextures(texs.size, texs, 0)
        if (sceneDepth != 0) GLES30.glDeleteRenderbuffers(1, intArrayOf(sceneDepth), 0)
        sceneFbo = 0; sceneTex = 0; sceneDepth = 0
        bloomFboA = 0; bloomTexA = 0; bloomFboB = 0; bloomTexB = 0
        valid = false
    }

    private fun genFbo(): Int {
        val a = IntArray(1); GLES30.glGenFramebuffers(1, a, 0); return a[0]
    }

    private fun genRbo(): Int {
        val a = IntArray(1); GLES30.glGenRenderbuffers(1, a, 0); return a[0]
    }

    private fun createColorTex(w: Int, h: Int, internalFmt: Int, dataType: Int): Int {
        val a = IntArray(1)
        GLES30.glGenTextures(1, a, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, a[0])
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, internalFmt, w, h, 0, GLES30.GL_RGBA, dataType, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        return a[0]
    }
}
