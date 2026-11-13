package com.somnaflow.app.audio

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/** Indices of the 10 console faders. All faders are 0..1; EQ faders are centred at 0.5 (= 0 dB, range ±12 dB). */
object Fader {
    const val MASTER = 0; const val BINAURAL = 1; const val NATURE = 2; const val MELODY = 3
    const val WHITE = 4; const val PINK = 5; const val BROWN = 6
    const val BASS = 7; const val MID = 8; const val TREBLE = 9
    val LABELS = listOf("Master", "Beats", "Nature", "Melody", "White", "Pink", "Brown", "Bass", "Mid", "Treble")
    fun defaults() = floatArrayOf(0.8f, 0.5f, 0.75f, 0.6f, 0f, 0f, 0f, 0.5f, 0.5f, 0.5f)
}

object Beat { const val OFF = 0; const val THETA = 1; const val SWEEP = 2 }

/** Immutable snapshot of everything the user can set on the console. */
data class MixConfig(
    val faders: List<Float> = Fader.defaults().toList(),
    val nature: Set<String> = emptySet(),
    val melody: String = "",
    val beat: Int = Beat.OFF,
    /** Enforce the Combined-Stimuli protocol: binaural beat held 30 dB under the nature/ASMR bed. */
    val csLock: Boolean = true,
    /** Rotate randomly through the five CS nature triggers every few minutes. */
    val randomize: Boolean = false
) {
    fun withFader(i: Int, v: Float) = copy(faders = faders.toMutableList().also { it[i] = v.coerceIn(0f, 1f) })
}

/**
 * Pure-Kotlin real-time mixer (no Android dependencies, so it is unit-testable).
 *
 * Binaural engine: left ear 250 Hz carrier, right ear 250 Hz + beat.
 *  - THETA: fixed 6 Hz beat (right = 256 Hz) for NREM-1 induction.
 *  - SWEEP: beat glides 4.0 -> 0.5 -> 4.0 Hz (delta..low theta) for the sleep-spindle protocol.
 * With csLock on, the beat's RMS is continuously servoed to sit exactly 30 dB below the measured
 * RMS of the nature bed, which is the 30:60 dB Combined Stimuli ratio.
 */
class Mixer(seed: Int = (System.nanoTime() and 0xFFFFFF).toInt()) {
    companion object {
        const val CARRIER_HZ = 250.0
        const val THETA_BEAT_HZ = 6.0
        const val SWEEP_LO = 0.5; const val SWEEP_HI = 4.0
        const val SWEEP_PERIOD_SEC = 600.0
        const val CS_RATIO_DB = -30f
        private const val BLOCK = 512
    }

    @Volatile var config = MixConfig()
    /** Extra attenuation of the bed while a narrator is speaking (1 = none). */
    @Volatile var duck = 1f
    @Volatile var outLevel = 0f; private set
    @Volatile var beatHz = 0f; private set
    /** Measured beat-to-bed ratio in dB (NaN when not applicable): shown on the console. */
    @Volatile var measuredRatioDb = Float.NaN; private set
    @Volatile var activeNature: Set<String> = emptySet(); private set

    private val rng = Rng(seed)
    private var seedCounter = seed
    private class Slot(val layer: Layer) { var gain = 0f }
    private val slots = HashMap<String, Slot>()
    private val tl = FloatArray(BLOCK); private val tr = FloatArray(BLOCK)
    private val bedL = FloatArray(BLOCK); private val bedR = FloatArray(BLOCK)
    private val revL = FloatArray(BLOCK); private val revR = FloatArray(BLOCK)
    private val reverb = Reverb()
    private val eq = Array(6) { Biquad() }
    private var eqKey = -1
    private var bedMs = 0f; private var beatAmp = 0f; private var duckNow = 1f
    private var phL = 0.0; private var phR = 0.0; private var sweepT = 0.0
    private var master = 0f
    private var fade = 0f; private var fadeTarget = 0f; private var fadeStep = 0f
    private var rotateLeft = 0L
    private var rotation: Set<String> = emptySet()
    private var lastBeat = Beat.OFF

    val isSilent: Boolean get() = fade <= 0f && fadeTarget <= 0f

    @Synchronized fun fadeTo(target: Float, seconds: Float) {
        fadeTarget = target.coerceIn(0f, 1f)
        fadeStep = if (seconds <= 0f) 1f else 1f / (seconds * SR)
    }

    private fun targetGain(id: String, c: MixConfig, nature: Set<String>): Float {
        fun sq(i: Int) = c.faders[i] * c.faders[i]
        return when (id) {
            "white" -> sq(Fader.WHITE); "pink" -> sq(Fader.PINK); "brown" -> sq(Fader.BROWN)
            in LayerFactory.MELODY -> if (c.melody == id) sq(Fader.MELODY) else 0f
            else -> if (id in nature) sq(Fader.NATURE) / sqrt(nature.size.coerceAtLeast(1).toFloat()) * 1.2f else 0f
        }
    }

    /** Renders [frames] stereo frames into [out] as interleaved floats. */
    @Synchronized fun render(out: FloatArray, frames: Int) {
        var done = 0
        while (done < frames) {
            val n = minOf(BLOCK, frames - done)
            block(out, done * 2, n)
            done += n
        }
    }

    private fun block(out: FloatArray, off: Int, n: Int) {
        val c = config
        // Randomized dynamic nature triggers.
        if (c.randomize) {
            rotateLeft -= n
            if (rotateLeft <= 0 || rotation.isEmpty()) {
                val pool = LayerFactory.CS_TRIGGERS.filter { it !in rotation }
                val first = pool[rng.int(pool.size)]
                rotation = if (rng.uni() < 0.35f) setOf(first, pool.filter { it != first }[rng.int(pool.size - 1)]) else setOf(first)
                rotateLeft = (rng.range(240f, 420f) * SR).toLong()
            }
        } else rotation = emptySet()
        val nature = if (c.randomize) rotation else c.nature
        activeNature = nature

        java.util.Arrays.fill(bedL, 0, n, 0f); java.util.Arrays.fill(bedR, 0, n, 0f)
        java.util.Arrays.fill(revL, 0, n, 0f); java.util.Arrays.fill(revR, 0, n, 0f)

        val wanted = HashSet<String>(nature)
        if (c.melody.isNotEmpty()) wanted.add(c.melody)
        if (c.faders[Fader.WHITE] > 0f) wanted.add("white")
        if (c.faders[Fader.PINK] > 0f) wanted.add("pink")
        if (c.faders[Fader.BROWN] > 0f) wanted.add("brown")
        for (id in wanted) if (id !in slots) slots[id] = Slot(LayerFactory.create(id, ++seedCounter * 31))

        val it = slots.entries.iterator()
        while (it.hasNext()) {
            val e = it.next(); val s = e.value
            val target = if (e.key in wanted) targetGain(e.key, c, nature) else 0f
            if (s.gain < 1e-5f && target < 1e-5f) { if (e.key !in wanted) it.remove(); continue }
            java.util.Arrays.fill(tl, 0, n, 0f); java.util.Arrays.fill(tr, 0, n, 0f)
            s.layer.render(tl, tr, n)
            // ~1.5 s crossfade so layers never click in or out.
            val g1 = s.gain + (target - s.gain) * 0.008f.coerceAtLeast(n / (1.5f * SR) * 4f)
            val g0 = s.gain; val dg = (g1 - g0) / n; val send = s.layer.reverbSend
            for (i in 0 until n) {
                val g = g0 + dg * i
                val a = tl[i] * g; val b = tr[i] * g
                bedL[i] += a; bedR[i] += b
                if (send > 0f) { revL[i] += a * send; revR[i] += b * send }
            }
            s.gain = g1
        }
        reverb.process(revL, revR, n)

        // Tone shaping (bass / mid / treble) on the bed only, so the binaural carriers stay pure.
        val key = (c.faders[Fader.BASS] * 100).toInt() * 10000 + (c.faders[Fader.MID] * 100).toInt() * 100 + (c.faders[Fader.TREBLE] * 100).toInt()
        if (key != eqKey) {
            eqKey = key
            for (ch in 0..1) {
                eq[ch * 3].lowShelf(150f, (c.faders[Fader.BASS] - 0.5f) * 24f)
                eq[ch * 3 + 1].peaking(1000f, 0.8f, (c.faders[Fader.MID] - 0.5f) * 24f)
                eq[ch * 3 + 2].highShelf(5000f, (c.faders[Fader.TREBLE] - 0.5f) * 24f)
            }
        }
        var ms = 0f
        val duckTarget = duck
        for (i in 0 until n) {
            duckNow += (duckTarget - duckNow) * 0.0002f
            val a = eq[2].run(eq[1].run(eq[0].run(bedL[i] + revL[i]))) * duckNow
            val b = eq[5].run(eq[4].run(eq[3].run(bedR[i] + revR[i]))) * duckNow
            bedL[i] = a; bedR[i] = b
            ms += a * a + b * b
        }
        ms /= (2 * n)
        bedMs += (ms - bedMs) * (n / (2.5f * SR)).coerceAtMost(1f)

        // Binaural beat.
        if (c.beat != lastBeat) { lastBeat = c.beat; sweepT = 0.0 }
        val bedRms = sqrt(bedMs)
        val locked = c.csLock && bedRms > 1e-4f
        val beatTarget = when {
            c.beat == Beat.OFF -> 0f
            locked -> bedRms * dbToAmp(CS_RATIO_DB) * 1.41421f
            else -> c.faders[Fader.BINAURAL] * c.faders[Fader.BINAURAL] * 0.25f
        }
        val masterTarget = c.faders[Fader.MASTER] * c.faders[Fader.MASTER] * 1.6f
        var hz = 0.0
        var outMs = 0f
        for (i in 0 until n) {
            beatAmp += (beatTarget - beatAmp) * 0.00008f
            var bl = 0f; var br = 0f
            if (beatAmp > 1e-7f) {
                hz = if (c.beat == Beat.SWEEP) {
                    sweepT += 1.0 / SR
                    val x = (sweepT % SWEEP_PERIOD_SEC) / SWEEP_PERIOD_SEC
                    val tri = if (x < 0.5) x * 2 else 2 - x * 2
                    SWEEP_HI - (SWEEP_HI - SWEEP_LO) * tri
                } else THETA_BEAT_HZ
                phL += CARRIER_HZ / SR; if (phL >= 1.0) phL -= 1.0
                phR += (CARRIER_HZ + hz) / SR; if (phR >= 1.0) phR -= 1.0
                bl = (sin(phL * 2 * Math.PI) * beatAmp).toFloat()
                br = (sin(phR * 2 * Math.PI) * beatAmp).toFloat()
            }
            master += (masterTarget - master) * 0.0005f
            if (fade < fadeTarget) fade = minOf(fadeTarget, fade + fadeStep) else if (fade > fadeTarget) fade = maxOf(fadeTarget, fade - fadeStep)
            val g = master * fade * fade
            val l = clip((bedL[i] + bl) * g); val r = clip((bedR[i] + br) * g)
            out[off + i * 2] = l; out[off + i * 2 + 1] = r
            outMs += l * l + r * r
        }
        beatHz = if (c.beat == Beat.OFF) 0f else hz.toFloat()
        measuredRatioDb = if (c.beat != Beat.OFF && bedRms > 1e-4f && beatAmp > 1e-7f) 20f * log10(beatAmp / 1.41421f / bedRms) else Float.NaN
        outLevel += (sqrt(outMs / (2 * n)) - outLevel) * 0.3f
    }

    private fun clip(x: Float): Float {
        val a = abs(x)
        return if (a <= 0.7f) x else (0.7f + 0.3f * tanh((a - 0.7f) / 0.3f)) * if (x < 0) -1f else 1f
    }
}
