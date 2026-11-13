package com.somnaflow.app.bio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Biquad for arbitrary sample rates (EEG / PPG conditioning). */
class Iir private constructor(private val b0: Double, private val b1: Double, private val b2: Double, private val a1: Double, private val a2: Double) {
    private var z1 = 0.0; private var z2 = 0.0
    fun run(x: Double): Double { val y = b0 * x + z1; z1 = b1 * x - a1 * y + z2; z2 = b2 * x - a2 * y; return y }
    companion object {
        fun highpass(fs: Double, f: Double, q: Double = 0.707): Iir { val w = 2 * PI * f / fs; val c = cos(w); val al = sin(w) / (2 * q); val a0 = 1 + al
            return Iir((1 + c) / 2 / a0, -(1 + c) / a0, (1 + c) / 2 / a0, -2 * c / a0, (1 - al) / a0) }
        fun lowpass(fs: Double, f: Double, q: Double = 0.707): Iir { val w = 2 * PI * f / fs; val c = cos(w); val al = sin(w) / (2 * q); val a0 = 1 + al
            return Iir((1 - c) / 2 / a0, (1 - c) / a0, (1 - c) / 2 / a0, -2 * c / a0, (1 - al) / a0) }
        fun notch(fs: Double, f: Double, q: Double = 8.0): Iir { val w = 2 * PI * f / fs; val c = cos(w); val al = sin(w) / (2 * q); val a0 = 1 + al
            return Iir(1 / a0, -2 * c / a0, 1 / a0, -2 * c / a0, (1 - al) / a0) }
    }
}

enum class BrainState(val label: String, val hint: String) {
    NO_SIGNAL("No signal", "Waiting for clean EEG"),
    HYPERAROUSAL("High beta", "Active, racing mind — hyper-arousal"),
    ALERT("Alert", "Awake and engaged"),
    RELAXED("Alpha", "Relaxed wakefulness"),
    DROWSY("Theta", "Drifting toward sleep (NREM 1)"),
    DEEP("Delta", "Slow-wave activity")
}

/**
 * Real-time EEG spectral decomposition for one channel.
 * Conditioning: 0.5 Hz high-pass, 50 + 60 Hz mains notches, 45 Hz low-pass.
 * Then a Hann-windowed FFT over ~2 s, updated every 0.5 s, gives relative power in the five classic bands.
 */
class EegAnalyzer(val fs: Int) {
    companion object {
        val BANDS = listOf("Delta" to (0.5f to 4f), "Theta" to (4f to 8f), "Alpha" to (8f to 13f), "Beta" to (13f to 30f), "Gamma" to (30f to 45f))
    }
    private val n = Integer.highestOneBit(fs * 2 - 1) shl 1
    private val fft = Fft(n)
    private val ring = FloatArray(n); private val win = FloatArray(n); private val spec = FloatArray(n / 2)
    private var pos = 0; private var filled = 0; private var sinceLast = 0
    private val hp = Iir.highpass(fs.toDouble(), 0.5); private val lp = Iir.lowpass(fs.toDouble(), 45.0)
    private val n50 = Iir.notch(fs.toDouble(), 50.0); private val n60 = Iir.notch(fs.toDouble(), 60.0)

    /** Smoothed relative band power, order delta..gamma, summing to ~1. */
    val bands = FloatArray(5)
    @Volatile var state = BrainState.NO_SIGNAL; private set
    @Volatile var artifact = false; private set

    /** Feed one sample (µV). Returns true when [bands] and [state] were just updated. */
    fun push(uV: Float): Boolean {
        val y = lp.run(n60.run(n50.run(hp.run(uV.toDouble())))).toFloat()
        ring[pos] = y; pos = (pos + 1) % n
        if (filled < n) filled++
        if (++sinceLast < fs / 2 || filled < n) return false
        sinceLast = 0
        var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE
        for (i in 0 until n) { val v = ring[(pos + i) % n]; win[i] = v; if (v < lo) lo = v; if (v > hi) hi = v }
        artifact = hi - lo > 400f || hi - lo < 0.5f
        if (artifact) { state = BrainState.NO_SIGNAL; return true }
        fft.power(win, 0, spec)
        val hzPerBin = fs.toFloat() / n
        val p = FloatArray(5); var total = 0f
        for (b in 0..4) {
            val (f0, f1) = BANDS[b].second
            var s = 0f
            for (k in (f0 / hzPerBin).toInt().coerceAtLeast(1)..(f1 / hzPerBin).toInt().coerceAtMost(n / 2 - 1)) s += spec[k]
            p[b] = s; total += s
        }
        if (total <= 0f) return true
        for (b in 0..4) bands[b] += (p[b] / total - bands[b]) * 0.25f
        state = classify(bands)
        return true
    }

    private fun classify(b: FloatArray): BrainState {
        val delta = b[0]; val theta = b[1]; val alpha = b[2]; val fast = b[3] + b[4]
        return when {
            fast > 0.35f && fast > (alpha + theta) -> BrainState.HYPERAROUSAL
            delta > 0.5f -> BrainState.DEEP
            theta >= alpha && theta + delta > fast -> BrainState.DROWSY
            alpha > fast -> BrainState.RELAXED
            else -> BrainState.ALERT
        }
    }
}

/**
 * Closed-loop intervention logic: sustained high beta starts the calming protocol,
 * sustained alpha/theta dominance ends it.
 */
class ClosedLoop(private val startAfterSec: Float = 8f, private val stopAfterSec: Float = 45f) {
    enum class Action { START, STOP }
    var intervening = false; private set
    var interventions = 0; private set
    private var hot = 0f; private var calm = 0f

    fun update(state: BrainState, dtSec: Float): Action? {
        when (state) {
            BrainState.HYPERAROUSAL -> { hot += dtSec; calm = 0f }
            BrainState.RELAXED, BrainState.DROWSY, BrainState.DEEP -> { calm += dtSec; hot = 0f }
            BrainState.ALERT -> { hot = maxOf(0f, hot - dtSec); calm = maxOf(0f, calm - dtSec) }
            BrainState.NO_SIGNAL -> {}
        }
        if (!intervening && hot >= startAfterSec) { intervening = true; interventions++; calm = 0f; return Action.START }
        if (intervening && calm >= stopAfterSec) { intervening = false; hot = 0f; return Action.STOP }
        return null
    }
    fun reset() { intervening = false; hot = 0f; calm = 0f }
}
