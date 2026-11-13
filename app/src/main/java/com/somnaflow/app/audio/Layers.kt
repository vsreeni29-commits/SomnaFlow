package com.somnaflow.app.audio

import kotlin.math.exp

/**
 * A procedurally synthesized sound layer. render() ADDS n frames into l/r at roughly
 * unit loudness; the mixer applies gain. Each layer is randomized so it never loops.
 */
abstract class Layer {
    /** How much of this layer is sent to the shared reverb. */
    open val reverbSend: Float = 0f
    abstract fun render(l: FloatArray, r: FloatArray, n: Int)
}

class NoiseLayer(private val kind: Int, seed: Int) : Layer() {
    private val wl = Rng(seed); private val wr = Rng(seed + 7)
    private val pl = PinkNoise(seed + 1); private val pr = PinkNoise(seed + 2)
    private val bl = BrownNoise(seed + 3); private val br = BrownNoise(seed + 4)
    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        when (kind) {
            0 -> for (i in 0 until n) { l[i] += wl.bi() * 0.16f; r[i] += wr.bi() * 0.16f }
            1 -> for (i in 0 until n) { l[i] += pl.next() * 0.28f; r[i] += pr.next() * 0.28f }
            else -> for (i in 0 until n) { l[i] += bl.next() * 0.42f; r[i] += br.next() * 0.42f }
        }
    }
}

class Rain(seed: Int) : Layer() {
    private val rng = Rng(seed)
    private val hpL = Biquad().highpass(450f, 0.7f); private val hpR = Biquad().highpass(450f, 0.7f)
    private val lpL = Biquad().lowpass(7500f, 0.7f); private val lpR = Biquad().lowpass(7500f, 0.7f)
    private val intensity = Wander(rng, 0.7f, 1.0f, 4f, 10f)
    private val nV = 10
    private val ph = FloatArray(nV); private val inc = FloatArray(nV); private val amp = FloatArray(nV)
    private val dec = FloatArray(nV); private val pan = FloatArray(nV)
    private var next = 0
    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        for (i in 0 until n) {
            if (rng.uni() < 26f / SR) {
                val k = next; next = (next + 1) % nV
                ph[k] = 0f; inc[k] = rng.range(1800f, 6500f) / SR
                amp[k] = rng.range(0.03f, 0.22f); dec[k] = exp(-1f / (SR * rng.range(0.004f, 0.02f))); pan[k] = rng.uni()
            }
            var dl = 0f; var dr = 0f
            for (k in 0 until nV) {
                val a = amp[k]
                if (a > 1e-4f) {
                    val s = Sine.at(ph[k]) * a
                    ph[k] += inc[k]; amp[k] = a * dec[k]
                    dl += s * (1f - pan[k]); dr += s * pan[k]
                }
            }
            val g = intensity.next() * 0.34f
            l[i] += lpL.run(hpL.run(rng.bi())) * g + dl
            r[i] += lpR.run(hpR.run(rng.bi())) * g + dr
        }
    }
}

class Ocean(seed: Int) : Layer() {
    private val rng = Rng(seed)
    private val bl = BrownNoise(seed + 1); private val br = BrownNoise(seed + 2)
    private val pl = PinkNoise(seed + 3); private val pr = PinkNoise(seed + 4)
    private val foamL = Biquad().highpass(2500f, 0.7f); private val foamR = Biquad().highpass(2500f, 0.7f)
    private var p = 0f; private var period = 9f; private var peak = 0.9f
    private var yl = 0f; private var yr = 0f
    private fun shape(x: Float): Float = if (x < 0.38f) { val t = x / 0.38f; t * t } else exp(-(x - 0.38f) * 5.2f)
    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        for (i in 0 until n) {
            p += 1f / (period * SR)
            if (p >= 1f) { p -= 1f; period = rng.range(7f, 13f); peak = rng.range(0.55f, 1f) }
            val eL = shape(p) * peak
            val eR = shape(if (p + 0.04f >= 1f) p - 0.96f else p + 0.04f) * peak
            yl += (0.015f + 0.30f * eL) * ((bl.next() + pl.next() * 1.6f) - yl)
            yr += (0.015f + 0.30f * eR) * ((br.next() + pr.next() * 1.6f) - yr)
            l[i] += yl * (0.22f + eL) * 0.36f + foamL.run(rng.bi()) * eL * eL * 0.10f
            r[i] += yr * (0.22f + eR) * 0.36f + foamR.run(rng.bi()) * eR * eR * 0.10f
        }
    }
}

class Waterfall(seed: Int) : Layer() {
    private val rng = Rng(seed)
    private val pl = PinkNoise(seed + 1); private val pr = PinkNoise(seed + 2)
    private val bl = BrownNoise(seed + 3); private val br = BrownNoise(seed + 4)
    private val hl = Biquad().highpass(1500f, 0.7f); private val hr = Biquad().highpass(1500f, 0.7f)
    private val mod = Wander(rng, 0.85f, 1f, 1.5f, 4f)
    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        for (i in 0 until n) {
            val m = mod.next()
            l[i] += (pl.next() * 0.42f + bl.next() * 0.30f + hl.run(rng.bi()) * 0.085f) * m * 0.65f
            r[i] += (pr.next() * 0.42f + br.next() * 0.30f + hr.run(rng.bi()) * 0.085f) * m * 0.65f
        }
    }
}

class Wind(seed: Int, private val level: Float = 1f) : Layer() {
    private val rng = Rng(seed)
    private val pl = PinkNoise(seed + 1); private val pr = PinkNoise(seed + 2)
    private val fl = Biquad(); private val fr = Biquad()
    private val center = Wander(rng, 220f, 900f, 2.5f, 7f)
    private val gust = Wander(rng, 0.25f, 1f, 2f, 6f)
    private var c = 0
    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        for (i in 0 until n) {
            val f = center.next(); val g = gust.next()
            if (c++ % 64 == 0) { fl.bandpass(f, 1.1f); fr.bandpass(f * 1.07f, 1.1f) }
            l[i] += fl.run(pl.next()) * g * 1.3f * level
            r[i] += fr.run(pr.next()) * g * 1.3f * level
        }
    }
}

class Forest(seed: Int) : Layer() {
    private val rng = Rng(seed)
    private val wind = Wind(seed + 11, 0.9f)
    private val leafL = Biquad().highpass(4000f, 0.7f); private val leafR = Biquad().highpass(4000f, 0.7f)
    private val rustle = Wander(rng, 0f, 1f, 1.5f, 5f)
    private val nB = 3
    private val wait = IntArray(nB) { (rng.range(0.5f, 5f) * SR).toInt() }
    private val chirpsLeft = IntArray(nB); private val t = IntArray(nB); private val dur = IntArray(nB); private val gap = IntArray(nB)
    private val f0 = FloatArray(nB); private val sweep = FloatArray(nB); private val amp = FloatArray(nB)
    private val pan = FloatArray(nB); private val ph = FloatArray(nB)
    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        wind.render(l, r, n)
        for (i in 0 until n) {
            val ru = rustle.next(); val lg = ru * ru * 0.035f
            var sl = leafL.run(rng.bi()) * lg; var sr = leafR.run(rng.bi()) * lg
            for (b in 0 until nB) {
                if (wait[b] > 0) {
                    if (--wait[b] == 0) {
                        if (chirpsLeft[b] == 0) {
                            chirpsLeft[b] = 2 + rng.int(5); f0[b] = rng.range(2200f, 5200f)
                            sweep[b] = rng.range(0.7f, 1.4f); amp[b] = rng.range(0.03f, 0.11f); pan[b] = rng.uni()
                            dur[b] = (rng.range(0.05f, 0.17f) * SR).toInt(); gap[b] = (rng.range(0.04f, 0.14f) * SR).toInt()
                        }
                        t[b] = 0
                    }
                    continue
                }
                val x = t[b].toFloat() / dur[b]
                val e = Sine.at(x * 0.5f); val f = f0[b] * (1f + (sweep[b] - 1f) * x)
                ph[b] += f / SR; if (ph[b] > 1f) ph[b] -= 1f
                val s = Sine.at(ph[b]) * e * e * amp[b]
                sl += s * (1f - pan[b]); sr += s * pan[b]
                if (++t[b] >= dur[b]) {
                    chirpsLeft[b]--
                    wait[b] = if (chirpsLeft[b] > 0) gap[b] else (rng.range(2f, 9f) * SR).toInt()
                }
            }
            l[i] += sl; r[i] += sr
        }
    }
}

class River(seed: Int) : Layer() {
    private val rng = Rng(seed)
    private val nBands = 5
    private val base = floatArrayOf(350f, 620f, 980f, 1550f, 2450f)
    private val fl = Array(nBands) { Biquad().bandpass(base[it], 3f) }
    private val fr = Array(nBands) { Biquad().bandpass(base[it] * 1.05f, 3f) }
    private val tune = Array(nBands) { Wander(rng, 0.75f, 1.3f, 0.3f, 1.2f) }
    private val bub = Array(nBands) { Wander(rng, 0.15f, 1f, 0.08f, 0.35f) }
    private val pl = PinkNoise(seed + 1); private val pr = PinkNoise(seed + 2)
    private var c = 0
    private val g = FloatArray(nBands)
    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        for (i in 0 until n) {
            val retune = c++ % 128 == 0
            val xl = rng.bi(); val xr = rng.bi()
            var sl = pl.next() * 0.16f; var sr = pr.next() * 0.16f
            for (b in 0 until nBands) {
                val tv = tune[b].next(); g[b] = bub[b].next()
                if (retune) { fl[b].bandpass(base[b] * tv, 3f); fr[b].bandpass(base[b] * tv * 1.05f, 3f) }
                val pn = if (b % 2 == 0) 0.7f else 0.3f
                sl += fl[b].run(xl) * g[b] * (1f - pn) * 0.9f
                sr += fr[b].run(xr) * g[b] * pn * 0.9f
            }
            l[i] += sl; r[i] += sr
        }
    }
}

class Campfire(seed: Int) : Layer() {
    private val rng = Rng(seed)
    private val bl = BrownNoise(seed + 1); private val br = BrownNoise(seed + 2)
    private val lp = Biquad().lowpass(260f, 0.7f); private val lp2 = Biquad().lowpass(260f, 0.7f)
    private val hp = Biquad().highpass(1200f, 0.7f); private val hp2 = Biquad().highpass(1200f, 0.7f)
    private val pk = PinkNoise(seed + 3)
    private val flick = Wander(rng, 0.5f, 1f, 0.2f, 1.2f)
    private val nV = 6
    private val amp = FloatArray(nV); private val dec = FloatArray(nV); private val pan = FloatArray(nV)
    private var next = 0
    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        for (i in 0 until n) {
            if (rng.uni() < 7f / SR) {
                val k = next; next = (next + 1) % nV
                val u = rng.uni(); amp[k] = u * u * u * 0.9f + 0.04f
                dec[k] = exp(-1f / (SR * rng.range(0.001f, 0.009f))); pan[k] = rng.uni()
            }
            var cl = 0f; var cr = 0f
            for (k in 0 until nV) {
                val a = amp[k]
                if (a > 1e-4f) { val s = rng.bi() * a; amp[k] = a * dec[k]; cl += s * (1f - pan[k]); cr += s * pan[k] }
            }
            val f = flick.next(); val hiss = pk.next() * 0.10f * f
            l[i] += lp.run(bl.next()) * 0.8f * f + hp.run(cl) * 0.4f + hiss
            r[i] += lp2.run(br.next()) * 0.8f * f + hp2.run(cr) * 0.4f + hiss
        }
    }
}

class Chimes(seed: Int) : Layer() {
    override val reverbSend = 0.5f
    private val rng = Rng(seed)
    private val notes = floatArrayOf(1046.5f, 1174.7f, 1318.5f, 1568f, 1760f, 2093f)
    private val ratio = floatArrayOf(1f, 2.76f, 5.4f)
    private val tau = floatArrayOf(2.6f, 1.2f, 0.45f)
    private val nV = 6
    private val ph = FloatArray(nV * 3); private val amp = FloatArray(nV * 3); private val inc = FloatArray(nV * 3)
    private val dec = FloatArray(3) { exp(-1f / (SR * tau[it])) }
    private val pan = FloatArray(nV)
    private var next = 0; private var gustWait = SR; private var strikes = 0; private var strikeWait = 0
    private fun strike() {
        val v = next; next = (next + 1) % nV
        val f = notes[rng.int(notes.size)]; val a = rng.range(0.08f, 0.22f); pan[v] = rng.range(0.2f, 0.8f)
        for (k in 0..2) { ph[v * 3 + k] = 0f; inc[v * 3 + k] = f * ratio[k] / SR; amp[v * 3 + k] = a / (1 + k * 1.5f) }
    }
    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        for (i in 0 until n) {
            if (strikes > 0) {
                if (--strikeWait <= 0) { strike(); strikes--; strikeWait = (rng.range(0.08f, 0.6f) * SR).toInt() }
            } else if (--gustWait <= 0) {
                strikes = 1 + rng.int(5); strikeWait = 0; gustWait = (rng.range(4f, 12f) * SR).toInt()
            }
            for (v in 0 until nV) {
                var s = 0f
                for (k in 0..2) {
                    val j = v * 3 + k; val a = amp[j]
                    if (a > 1e-4f) { s += Sine.at(ph[j]) * a; ph[j] += inc[j]; if (ph[j] > 1f) ph[j] -= 1f; amp[j] = a * dec[k] }
                }
                l[i] += s * (1f - pan[v]); r[i] += s * pan[v]
            }
        }
    }
}

/** Slow generative piano: sparse pentatonic notes with decaying harmonics. */
class Piano(seed: Int) : Layer() {
    override val reverbSend = 0.4f
    private val rng = Rng(seed)
    private val scale = intArrayOf(48, 50, 52, 55, 57, 60, 62, 64, 67, 69, 72)
    private val nV = 8; private val nP = 5
    private val ph = FloatArray(nV * nP); private val inc = FloatArray(nV * nP); private val amp = FloatArray(nV * nP)
    private val dec = FloatArray(nV * nP); private val age = IntArray(nV); private val pan = FloatArray(nV)
    private var next = 0; private var wait = SR / 2; private var idx = 5
    private fun note(i: Int, vel: Float) {
        val v = next; next = (next + 1) % nV
        val f = midiHz(scale[i]); age[v] = 0; pan[v] = 0.25f + 0.5f * i / scale.size
        val base = rng.range(2.4f, 3.8f)
        for (k in 0 until nP) {
            val j = v * nP + k; val h = k + 1
            ph[j] = 0f; inc[j] = f * h * (1f + 0.0004f * h * h) / SR
            amp[j] = vel / Math.pow(h.toDouble(), 1.5).toFloat(); dec[j] = exp(-1f / (SR * base / h))
        }
    }
    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        for (i in 0 until n) {
            if (--wait <= 0) {
                idx = (idx + rng.int(5) - 2).coerceIn(0, scale.size - 1)
                val vel = rng.range(0.16f, 0.3f); note(idx, vel)
                if (rng.uni() < 0.3f) note((idx + if (rng.uni() < 0.5f) 2 else -3).coerceIn(0, scale.size - 1), vel * 0.6f)
                wait = (rng.range(1.8f, 4.6f) * SR).toInt()
            }
            for (v in 0 until nV) {
                if (amp[v * nP] < 1e-4f) continue
                var s = 0f
                for (k in 0 until nP) {
                    val j = v * nP + k
                    s += Sine.at(ph[j]) * amp[j]; ph[j] += inc[j]; if (ph[j] > 1f) ph[j] -= 1f; amp[j] *= dec[j]
                }
                val a = age[v]; if (a < 220) { s *= a / 220f; age[v] = a + 1 }
                l[i] += s * (1f - pan[v]); r[i] += s * pan[v]
            }
        }
    }
}

/** Evolving ambient pad: four slow chords crossfading, each note a detuned sine pair. */
class Pad(seed: Int) : Layer() {
    override val reverbSend = 0.35f
    private val rng = Rng(seed)
    private val chords = arrayOf(
        intArrayOf(48, 55, 64, 71, 74), intArrayOf(45, 52, 60, 67, 72),
        intArrayOf(41, 53, 60, 64, 69), intArrayOf(43, 55, 60, 62, 69)
    )
    private val nN = 5
    private val phA = FloatArray(20); private val phB = FloatArray(20); private val lfo = FloatArray(20) { rng.uni() }
    private val lfoInc = FloatArray(20) { rng.range(0.05f, 0.14f) / SR }
    private val gain = FloatArray(4)
    private var chord = rng.int(4); private var left = 0
    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        for (i in 0 until n) {
            if (--left <= 0) { chord = (chord + 1 + rng.int(2)) % 4; left = (rng.range(18f, 28f) * SR).toInt() }
            var sl = 0f; var sr = 0f
            for (c in 0..3) {
                gain[c] += ((if (c == chord) 1f else 0f) - gain[c]) * (1f / (3.5f * SR))
                val g = gain[c]; if (g < 1e-4f) continue
                for (k in 0 until nN) {
                    val j = c * nN + k; val f = midiHz(chords[c][k])
                    phA[j] += f * 0.9985f / SR; if (phA[j] > 1f) phA[j] -= 1f
                    phB[j] += f * 1.0015f / SR; if (phB[j] > 1f) phB[j] -= 1f
                    lfo[j] += lfoInc[j]; if (lfo[j] > 1f) lfo[j] -= 1f
                    val a = g * (0.6f + 0.4f * Sine.at(lfo[j])) * 0.085f
                    sl += Sine.at(phA[j]) * a; sr += Sine.at(phB[j]) * a
                }
            }
            l[i] += sl; r[i] += sr
        }
    }
}

object LayerFactory {
    val NATURE = listOf("rain", "ocean", "waterfall", "forest", "river", "campfire", "chimes", "wind")
    /** The five ASMR triggers used by the Combined Stimuli protocol. */
    val CS_TRIGGERS = listOf("rain", "ocean", "waterfall", "forest", "river")
    val MELODY = listOf("piano", "pad")
    fun create(id: String, seed: Int): Layer = when (id) {
        "rain" -> Rain(seed); "ocean" -> Ocean(seed); "waterfall" -> Waterfall(seed); "forest" -> Forest(seed)
        "river" -> River(seed); "campfire" -> Campfire(seed); "chimes" -> Chimes(seed); "wind" -> Wind(seed)
        "piano" -> Piano(seed); "pad" -> Pad(seed)
        "white" -> NoiseLayer(0, seed); "pink" -> NoiseLayer(1, seed); else -> NoiseLayer(2, seed)
    }
    fun label(id: String): String = when (id) {
        "rain" -> "Rain"; "ocean" -> "Ocean waves"; "waterfall" -> "Waterfall"; "forest" -> "Forest"; "river" -> "River"
        "campfire" -> "Campfire"; "chimes" -> "Wind chimes"; "wind" -> "Wind"; "piano" -> "Piano"; "pad" -> "Ambient pads"
        else -> id
    }
}
