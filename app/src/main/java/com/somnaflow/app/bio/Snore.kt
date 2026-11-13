package com.somnaflow.app.bio

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Night-time respiration monitor working on 16 kHz microphone frames of 1024 samples (64 ms).
 * No audio is stored: each frame is reduced to a level and a low-band energy ratio.
 *
 * A snore is a 0.3–3 s sound burst dominated by energy under 500 Hz. Snoring is declared only when
 * several such bursts recur at a breathing cadence (one every 2–8 s) and the combined confidence
 * exceeds 0.9. A breathing pause is a 10–90 s silence inside a snoring run.
 */
class SnoreDetector {
    companion object { const val FS = 16000; const val FRAME = 1024; const val FRAME_SEC = FRAME.toFloat() / FS; const val THRESHOLD = 0.9f }
    private val fft = Fft(FRAME); private val buf = FloatArray(FRAME); private val spec = FloatArray(FRAME / 2)
    private var t = 0f
    private var floorDb = 0f; private var floorInit = false
    private var inEvent = false; private var evStart = 0f; private var evFrames = 0; private var evLow = 0f; private var quiet = 0
    private val snoreOnsets = ArrayDeque<Float>(); private val snoreRatios = ArrayDeque<Float>()
    private val breathOnsets = ArrayDeque<Float>()
    private var lastEventEnd = -1f; private var lastSnoringAt = -1000f; private var gapAlerted = false

    @Volatile var confidence = 0f; private set
    @Volatile var snoring = false; private set
    @Volatile var levelDb = -90f; private set
    @Volatile var breathsPerMin = 0f; private set
    var snoreSeconds = 0f; private set
    var episodes = 0; private set
    var pauses = 0; private set
    /** Seconds since the last breath sound while in a snoring run (0 otherwise). */
    @Volatile var gapSec = 0f; private set

    /** Returns true exactly once when a suspected breathing pause passes 12 seconds. */
    fun process(pcm: ShortArray): Boolean {
        for (i in 0 until FRAME) buf[i] = pcm[i] / 32768f
        fft.power(buf, 0, spec)
        val hz = FS.toFloat() / FRAME
        var total = 1e-12f; var low = 0f
        for (k in (60 / hz).toInt()..(4000 / hz).toInt()) { total += spec[k]; if (k * hz <= 500f) low += spec[k] }
        val db = 10f * log10(total)
        levelDb = db
        if (!floorInit) { floorDb = db; floorInit = true }
        floorDb = if (db < floorDb) floorDb + (db - floorDb) * 0.2f else floorDb + 0.01f
        val active = db > floorDb + 9f
        t += FRAME_SEC
        var alert = false

        if (active) {
            if (!inEvent) { inEvent = true; evStart = t; evFrames = 0; evLow = 0f }
            evFrames++; evLow += low / total; quiet = 0
        } else if (inEvent && ++quiet >= 3) {
            inEvent = false
            val dur = evFrames * FRAME_SEC; val ratio = evLow / evFrames
            if (dur in 0.3f..3.5f) {
                val inRun = t - lastSnoringAt < 90f
                if (inRun && lastEventEnd > 0 && evStart - lastEventEnd in 10f..90f) pauses++
                breathOnsets.addLast(evStart)
                if (ratio >= 0.55f && dur <= 3f) { snoreOnsets.addLast(evStart); snoreRatios.addLast(ratio) }
                lastEventEnd = t; gapAlerted = false
            }
        }
        while (snoreOnsets.isNotEmpty() && t - snoreOnsets.first() > 40f) { snoreOnsets.removeFirst(); snoreRatios.removeFirst() }
        while (breathOnsets.isNotEmpty() && t - breathOnsets.first() > 60f) breathOnsets.removeFirst()

        confidence = score()
        val was = snoring
        snoring = confidence > THRESHOLD
        if (snoring) { lastSnoringAt = t; snoreSeconds += FRAME_SEC; if (!was) episodes++ }
        breathsPerMin = cadence()

        val inRun = t - lastSnoringAt < 90f
        gapSec = if (inRun && lastEventEnd > 0 && !inEvent) t - lastEventEnd else 0f
        if (gapSec >= 12f && !gapAlerted) { gapAlerted = true; alert = true }
        return alert
    }

    private fun score(): Float {
        val n = snoreOnsets.size
        if (n < 3) return 0f
        val iv = FloatArray(n - 1) { snoreOnsets[it + 1] - snoreOnsets[it] }
        val regular = iv.count { it in 2f..8f }.toFloat() / iv.size
        val mean = iv.average().toFloat()
        val cv = sqrt(iv.fold(0f) { a, v -> a + (v - mean) * (v - mean) } / iv.size) / mean
        val spectral = (0.6f + (snoreRatios.average().toFloat() - 0.55f) / 0.2f * 0.4f).coerceIn(0f, 1f)
        return spectral * regular * (1f - (cv - 0.3f).coerceIn(0f, 0.6f)) * minOf(1f, n / 4f)
    }

    private fun cadence(): Float {
        if (breathOnsets.size < 4) return 0f
        val iv = (1 until breathOnsets.size).map { breathOnsets[it] - breathOnsets[it - 1] }.filter { it in 2f..8f }.sorted()
        return if (iv.size < 3) 0f else 60f / iv[iv.size / 2]
    }
}
