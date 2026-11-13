package com.somnaflow.app.bio

import kotlin.math.abs
import kotlin.math.sqrt

/** Heart-rate variability from beat-to-beat (RR) intervals in milliseconds. */
class HrvTracker(private val windowMs: Float = 60_000f) {
    private val rr = ArrayDeque<Float>()
    private var sum = 0f
    var heartRate = 0f; private set
    var rmssd = 0f; private set
    /** RMSSD of the first full window: the reference for the parasympathetic shift. */
    var baseline = 0f
    var beats = 0; private set

    /** Percentage change of RMSSD against baseline; a 15–20 % rise marks deep relaxation. */
    val changePct: Float get() = if (baseline > 0f && rmssd > 0f) (rmssd / baseline - 1f) * 100f else 0f
    val relaxed: Boolean get() = changePct >= 15f

    @Synchronized fun add(ms: Float) {
        if (ms < 300f || ms > 2000f) return
        // Reject ectopic beats / missed detections: more than 25 % away from the previous interval.
        val last = rr.lastOrNull()
        if (last != null && abs(ms - last) > 0.25f * last && rr.size > 2) { if (abs(ms - sum / rr.size) > 0.3f * sum / rr.size) return }
        rr.addLast(ms); sum += ms; beats++
        while (sum > windowMs && rr.size > 2) sum -= rr.removeFirst()
        heartRate = 60_000f / (sum / rr.size)
        if (rr.size >= 8) {
            rmssd = rmssd(rr.toList())
            if (baseline == 0f && sum >= windowMs * 0.9f) baseline = rmssd
        }
    }

    companion object {
        fun rmssd(rr: List<Float>): Float {
            if (rr.size < 2) return 0f
            var s = 0.0
            for (i in 1 until rr.size) { val d = rr[i] - rr[i - 1]; s += d * d }
            return sqrt(s / (rr.size - 1)).toFloat()
        }
    }
}

/**
 * Turns a stream of camera brightness samples (fingertip over lens + flash) into RR intervals.
 * Detrend -> smooth -> adaptive peak detection with parabolic interpolation for sub-frame timing.
 */
class PpgProcessor(private val onBeat: (rrMs: Float) -> Unit) {
    private var slow = 0.0; private var fast = 0.0; private var init = false
    private var p1 = 0.0; private var p2 = 0.0; private var t1 = 0.0; private var t2 = 0.0
    private var amp = 0.0; private var lastPeak = -1.0
    /** 0..1 pulse quality (how regular recent beats are). */
    var quality = 0f; private set
    var wave = 0f; private set
    private var lastRr = 0.0

    fun add(timeMs: Double, brightness: Double) {
        if (!init) { slow = brightness; fast = brightness; init = true }
        slow += (brightness - slow) * 0.03; fast += (brightness - fast) * 0.35
        val v = fast - slow
        wave = v.toFloat()
        amp = maxOf(amp * 0.995, kotlin.math.abs(v))
        // p1 is a local maximum if it is above both neighbours.
        if (p1 > p2 && p1 >= v && p1 > amp * 0.3 && amp > 0.02) {
            val denom = p2 - 2 * p1 + v
            val shift = if (denom != 0.0) 0.5 * (p2 - v) / denom else 0.0
            val tp = t1 + shift * (timeMs - t1)
            if (lastPeak < 0 || tp - lastPeak > 330) {
                if (lastPeak > 0) {
                    val rr = tp - lastPeak
                    if (rr < 2000) {
                        onBeat(rr.toFloat())
                        val ok = lastRr > 0 && kotlin.math.abs(rr - lastRr) < 0.2 * lastRr
                        quality += ((if (ok) 1f else 0f) - quality) * 0.2f
                        lastRr = rr
                    }
                }
                lastPeak = tp
            }
        }
        p2 = p1; t2 = t1; p1 = v; t1 = timeMs
    }
}
