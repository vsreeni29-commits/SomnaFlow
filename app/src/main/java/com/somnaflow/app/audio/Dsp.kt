package com.somnaflow.app.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Engine sample rate. Everything is synthesized on-device, so no audio assets are needed. */
const val SR = 44100

/** Small, fast xorshift PRNG so the audio thread never touches java.util.Random locks. */
class Rng(seed: Int) {
    private var s = (seed * 747796405 + 12345).let { if (it == 0) 0x2545F491 else it }
    fun nextInt(): Int {
        var x = s
        x = x xor (x shl 13); x = x xor (x ushr 17); x = x xor (x shl 5)
        s = x
        return x
    }
    /** Uniform 0..1 */
    fun uni(): Float = (nextInt() ushr 8) * (1f / 16777216f)
    /** Uniform -1..1 */
    fun bi(): Float = uni() * 2f - 1f
    fun range(a: Float, b: Float): Float = a + (b - a) * uni()
    fun int(n: Int): Int = ((nextInt() ushr 1) % n)
}

/** Table sine: phase is in cycles (0..1). */
object Sine {
    private const val N = 4096
    private val tab = FloatArray(N + 1) { sin(2.0 * PI * it / N).toFloat() }
    fun at(phase: Float): Float {
        val p = (phase - kotlin.math.floor(phase)) * N
        val i = p.toInt()
        val f = p - i
        return tab[i] + (tab[i + 1] - tab[i]) * f
    }
}

class PinkNoise(seed: Int) {
    private val rng = Rng(seed)
    private var b0 = 0f; private var b1 = 0f; private var b2 = 0f
    fun next(): Float {
        val w = rng.bi()
        b0 = 0.99765f * b0 + w * 0.0990460f
        b1 = 0.96300f * b1 + w * 0.2965164f
        b2 = 0.57000f * b2 + w * 1.0526913f
        return (b0 + b1 + b2 + w * 0.1848f) * 0.22f
    }
}

class BrownNoise(seed: Int) {
    private val rng = Rng(seed)
    private var b = 0f
    fun next(): Float {
        b = (b + 0.02f * rng.bi()) / 1.02f
        return b * 3.5f
    }
}

/** RBJ cookbook biquad. */
class Biquad {
    private var b0 = 1f; private var b1 = 0f; private var b2 = 0f; private var a1 = 0f; private var a2 = 0f
    private var z1 = 0f; private var z2 = 0f

    private fun set(nb0: Double, nb1: Double, nb2: Double, a0: Double, na1: Double, na2: Double) {
        b0 = (nb0 / a0).toFloat(); b1 = (nb1 / a0).toFloat(); b2 = (nb2 / a0).toFloat()
        a1 = (na1 / a0).toFloat(); a2 = (na2 / a0).toFloat()
    }

    fun lowpass(f: Float, q: Float): Biquad {
        val w = 2.0 * PI * f / SR; val c = cos(w); val al = sin(w) / (2 * q)
        set((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + al, -2 * c, 1 - al); return this
    }
    fun highpass(f: Float, q: Float): Biquad {
        val w = 2.0 * PI * f / SR; val c = cos(w); val al = sin(w) / (2 * q)
        set((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + al, -2 * c, 1 - al); return this
    }
    fun bandpass(f: Float, q: Float): Biquad {
        val w = 2.0 * PI * f / SR; val c = cos(w); val al = sin(w) / (2 * q)
        set(al, 0.0, -al, 1 + al, -2 * c, 1 - al); return this
    }
    fun peaking(f: Float, q: Float, dB: Float): Biquad {
        val a = 10.0.pow(dB / 40.0); val w = 2.0 * PI * f / SR; val c = cos(w); val al = sin(w) / (2 * q)
        set(1 + al * a, -2 * c, 1 - al * a, 1 + al / a, -2 * c, 1 - al / a); return this
    }
    fun lowShelf(f: Float, dB: Float): Biquad {
        val a = 10.0.pow(dB / 40.0); val w = 2.0 * PI * f / SR; val c = cos(w)
        val al = sin(w) / 2 * sqrt(2.0); val sq = 2 * sqrt(a) * al
        set(a * ((a + 1) - (a - 1) * c + sq), 2 * a * ((a - 1) - (a + 1) * c), a * ((a + 1) - (a - 1) * c - sq),
            (a + 1) + (a - 1) * c + sq, -2 * ((a - 1) + (a + 1) * c), (a + 1) + (a - 1) * c - sq); return this
    }
    fun highShelf(f: Float, dB: Float): Biquad {
        val a = 10.0.pow(dB / 40.0); val w = 2.0 * PI * f / SR; val c = cos(w)
        val al = sin(w) / 2 * sqrt(2.0); val sq = 2 * sqrt(a) * al
        set(a * ((a + 1) + (a - 1) * c + sq), -2 * a * ((a - 1) + (a + 1) * c), a * ((a + 1) + (a - 1) * c - sq),
            (a + 1) - (a - 1) * c + sq, 2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - sq); return this
    }

    fun run(x: Float): Float {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        if (z1 > -1e-20f && z1 < 1e-20f) z1 = 0f
        return y
    }
}

/** Random value that glides to a new target every few seconds: used for "alive" natural modulation. */
class Wander(private val rng: Rng, private val lo: Float, private val hi: Float, private val minSec: Float, private val maxSec: Float) {
    var value = rng.range(lo, hi); private set
    private var step = 0f
    private var left = 0
    fun next(): Float {
        if (left <= 0) {
            left = (rng.range(minSec, maxSec) * SR).toInt()
            step = (rng.range(lo, hi) - value) / left
        }
        left--; value += step
        return value
    }
}

/** Small stereo Schroeder reverb used for piano, pads and chimes. */
class Reverb {
    private val combLen = intArrayOf(1557, 1617, 1491, 1422)
    private val apLen = intArrayOf(225, 556)
    private val combs = Array(8) { FloatArray(combLen[it % 4] + (it / 4) * 23) }
    private val combIdx = IntArray(8)
    private val combLp = FloatArray(8)
    private val aps = Array(4) { FloatArray(apLen[it % 2] + (it / 2) * 23) }
    private val apIdx = IntArray(4)

    fun process(l: FloatArray, r: FloatArray, n: Int) {
        for (i in 0 until n) {
            for (ch in 0..1) {
                val x = (if (ch == 0) l[i] else r[i]) * 0.25f
                var acc = 0f
                for (k in 0..3) {
                    val c = ch * 4 + k; val buf = combs[c]; val idx = combIdx[c]
                    val y = buf[idx]
                    combLp[c] = y * 0.8f + combLp[c] * 0.2f
                    buf[idx] = x + combLp[c] * 0.86f
                    combIdx[c] = if (idx + 1 >= buf.size) 0 else idx + 1
                    acc += y
                }
                for (k in 0..1) {
                    val a = ch * 2 + k; val buf = aps[a]; val idx = apIdx[a]
                    val bo = buf[idx]
                    buf[idx] = acc + bo * 0.5f
                    acc = bo - acc * 0.5f
                    apIdx[a] = if (idx + 1 >= buf.size) 0 else idx + 1
                }
                if (ch == 0) l[i] = acc else r[i] = acc
            }
        }
    }
}

fun dbToAmp(db: Float): Float = 10.0.pow(db / 20.0).toFloat()
fun midiHz(m: Int): Float = (440.0 * 2.0.pow((m - 69) / 12.0)).toFloat()
