package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.domain.CosmosMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Native real-time 8D Binaural Spatial Audio Synthesizer.
 * Generates orbiting multi-harmonic cosmic pads, relativistic black-hole tidal sub-bass,
 * filtered solar-wind noise, and crystalline interaction chimes with dynamic stereo HRTF panning.
 */
class SpatialAudio8DEngine {
    @Volatile private var running = false
    @Volatile private var mode: CosmosMode = CosmosMode.SPACE
    @Volatile private var blackHoleDistanceRs: Float = 14.0f
    @Volatile private var orbitAngleRad: Float = 0.0f
    @Volatile private var chimeFreq: Float = 0f
    @Volatile private var chimeEnergy: Float = 0f

    private var audioThread: Thread? = null

    fun setEnabled(enabled: Boolean) {
        if (enabled && !running) {
            start()
        } else if (!enabled && running) {
            stop()
        }
    }

    fun updateTelemetry(currentMode: CosmosMode, bhDistRs: Float, cameraYawRad: Float) {
        mode = currentMode
        blackHoleDistanceRs = bhDistRs.coerceAtLeast(1.02f)
        orbitAngleRad = cameraYawRad
    }

    fun triggerChime(freqHz: Float = 528f) {
        if (!running) return
        chimeFreq = freqHz
        chimeEnergy = 1.0f
    }

    private fun start() {
        running = true
        audioThread = Thread(::renderLoop, "Cosmos8DAudio").apply {
            priority = Thread.NORM_PRIORITY + 1
            start()
        }
    }

    fun stop() {
        running = false
        audioThread?.join(250)
        audioThread = null
    }

    private fun renderLoop() {
        val sampleRate = 44_100
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (_: Exception) {
            running = false
            return
        }

        val frameCount = 1024
        val pcm = ShortArray(frameCount * 2)
        val dt = 1.0 / sampleRate

        var phase1 = 0.0
        var phase2 = 0.0
        var phase3 = 0.0
        var phaseSub = 0.0
        var phaseChime = 0.0
        var panOrbit = 0.0
        var masterGain = 0.0
        var noiseLp = 0.0
        var noiseLp2 = 0.0
        val rng = Random(42)

        try {
            track.play()
            while (running) {
                val isBh = mode == CosmosMode.BLACK_HOLE
                val isEarth = mode == CosmosMode.EARTH_SKY
                val prox = if (isBh) (14.0 / blackHoleDistanceRs.toDouble().coerceAtLeast(1.05)).coerceIn(0.35, 5.0) else 0.0

                val f1 = if (isBh) 44.0 + prox * 12.0 else if (isEarth) 82.4 else 73.42
                val f2 = if (isBh) 66.0 + prox * 21.0 else if (isEarth) 123.47 else 110.0
                val f3 = if (isBh) 146.8 + prox * 38.0 else 185.0
                val fSub = if (isBh) 28.0 + prox * 5.5 else 36.71
                val windAmp = if (isBh) (0.09 + prox * 0.045).coerceAtMost(0.28) else if (isEarth) 0.10 else 0.06
                val panSpeed = if (isBh) 0.55 + prox * 0.28 else 0.34

                for (i in 0 until frameCount) {
                    masterGain += (0.42 - masterGain) * 0.0005
                    panOrbit += panSpeed * dt

                    val az1 = panOrbit + orbitAngleRad * 0.4
                    val az2 = -panOrbit * 0.72 + orbitAngleRad * 0.65 + 1.4

                    // Equal-power binaural panning with gentle head-shadowing crossfeed
                    val pan1 = sin(az1)
                    val l1 = sqrtApprox(0.5 * (1.0 - pan1 * 0.86))
                    val r1 = sqrtApprox(0.5 * (1.0 + pan1 * 0.86))

                    val pan2 = cos(az2)
                    val l2 = sqrtApprox(0.5 * (1.0 - pan2 * 0.88))
                    val r2 = sqrtApprox(0.5 * (1.0 + pan2 * 0.88))

                    phase1 = (phase1 + 2.0 * PI * f1 * dt) % (2.0 * PI)
                    phase2 = (phase2 + 2.0 * PI * f2 * dt) % (2.0 * PI)
                    phase3 = (phase3 + 2.0 * PI * f3 * dt) % (2.0 * PI)
                    phaseSub = (phaseSub + 2.0 * PI * fSub * dt) % (2.0 * PI)

                    val droneA = sin(phase1) * 0.32 + sin(phase2) * 0.18 + sin(phase2 * 2.0) * 0.04
                    val shimmerB = sin(phase3) * (0.09 + 0.04 * sin(panOrbit * 1.7))
                    val sub = sin(phaseSub) * (if (isBh) 0.32 else 0.20)

                    // Smooth pink/cosmic wind noise
                    val w = rng.nextDouble(-1.0, 1.0)
                    noiseLp = noiseLp * 0.985 + w * 0.015
                    noiseLp2 = noiseLp2 * 0.94 + noiseLp * 0.06
                    val wind = (noiseLp2 * 4.5) * windAmp

                    var chimeSample = 0.0
                    if (chimeEnergy > 0.0005f) {
                        phaseChime = (phaseChime + 2.0 * PI * chimeFreq * dt) % (2.0 * PI)
                        chimeSample = (sin(phaseChime) * 0.7 + sin(phaseChime * 2.0) * 0.3) * chimeEnergy * 0.22
                        chimeEnergy *= 0.99915f
                    }

                    val sampleL = (droneA * l1 + (shimmerB + wind) * l2 + sub * 0.72 + chimeSample) * masterGain
                    val sampleR = (droneA * r1 + (shimmerB + wind) * r2 + sub * 0.72 + chimeSample) * masterGain

                    pcm[i * 2] = (sampleL.coerceIn(-0.98, 0.98) * 32767.0).toInt().toShort()
                    pcm[i * 2 + 1] = (sampleR.coerceIn(-0.98, 0.98) * 32767.0).toInt().toShort()
                }
                track.write(pcm, 0, pcm.size)
            }
        } catch (_: Exception) {
            // Audio device interrupted cleanly
        } finally {
            runCatching {
                track.stop()
                track.release()
            }
        }
    }

    private fun sqrtApprox(v: Double): Double = kotlin.math.sqrt(v.coerceAtLeast(0.0))
}
