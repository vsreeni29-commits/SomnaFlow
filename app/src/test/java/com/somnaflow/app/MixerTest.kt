package com.somnaflow.app

import com.somnaflow.app.audio.*
import com.somnaflow.app.bio.Fft
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

class MixerTest {
    private fun rms(x: FloatArray) = sqrt(x.fold(0.0) { a, v -> a + v * v } / x.size).toFloat()

    @Test fun layerLoudnessIsBalanced() {
        val bad = ArrayList<String>()
        for (id in LayerFactory.NATURE + LayerFactory.MELODY + listOf("white", "pink", "brown")) {
            val layer = LayerFactory.create(id, 42)
            val n = SR * 30; val l = FloatArray(n); val r = FloatArray(n)
            layer.render(l, r, n)
            val v = (rms(l) + rms(r)) / 2; val peak = l.maxOf { abs(it) }
            println("layer %-10s rms=%.4f (%.1f dB) peak=%.3f".format(id, v, 20 * log10(v), peak))
            if (v < 0.01f || v > 0.3f || peak > 1.5f) bad += id
            assertFalse(l.any { it.isNaN() })
        }
        assertTrue("unbalanced: $bad", bad.isEmpty())
    }

    private fun peakBin(x: FloatArray): Double {
        val n = 65536; val fft = Fft(n); val p = FloatArray(n / 2)
        fft.power(x, x.size - n, p)
        var best = 1; for (i in 1 until n / 2) if (p[i] > p[best]) best = i
        return best * SR.toDouble() / n
    }

    @Test fun thetaBeatIs250and256Hz() {
        val m = Mixer(1); m.config = MixConfig(beat = Beat.THETA, csLock = false); m.fadeTo(1f, 0f)
        val frames = SR * 6; val out = FloatArray(frames * 2); m.render(out, frames)
        val l = FloatArray(frames) { out[it * 2] }; val r = FloatArray(frames) { out[it * 2 + 1] }
        assertEquals(250.0, peakBin(l), 0.7); assertEquals(256.0, peakBin(r), 0.7)
        assertEquals(6f, m.beatHz, 0.001f)
    }

    @Test fun combinedStimuliHoldsBeat30dBUnderBed() {
        val m = Mixer(2); m.config = MixConfig(beat = Beat.THETA, nature = setOf("rain"), csLock = true); m.fadeTo(1f, 0f)
        val out = FloatArray(SR * 2 * 20); m.render(out, SR * 20)
        println("measured CS ratio = ${m.measuredRatioDb} dB, out level ${m.outLevel}")
        assertEquals(-30f, m.measuredRatioDb, 1.0f)
        assertTrue(out.all { abs(it) <= 1f })
    }

    @Test fun sweepStaysInDeltaRangeAndRandomizeRotates() {
        val m = Mixer(3); m.config = MixConfig(beat = Beat.SWEEP, randomize = true, faders = Fader.defaults().toList().toMutableList().also { it[Fader.PINK] = 0.6f }); m.fadeTo(1f, 0f)
        val out = FloatArray(SR * 2); var lo = 99f; var hi = 0f
        repeat(320) { m.render(out, SR); lo = minOf(lo, m.beatHz); hi = maxOf(hi, m.beatHz) }
        println("sweep range $lo..$hi, nature=${m.activeNature}")
        assertTrue(lo >= 0.5f && lo < 0.6f); assertTrue(hi <= 4.0f && hi > 3.9f)
        assertTrue(m.activeNature.isNotEmpty() && LayerFactory.CS_TRIGGERS.containsAll(m.activeNature))
    }

    @Test fun realtimeHeadroom() {
        val m = Mixer(4); m.config = MixConfig(beat = Beat.THETA, nature = setOf("rain", "forest", "river", "chimes"), melody = "piano",
            faders = listOf(0.8f, 0.5f, 0.8f, 0.6f, 0.3f, 0.3f, 0.3f, 0.6f, 0.4f, 0.6f)); m.fadeTo(1f, 0f)
        val out = FloatArray(SR * 2); m.render(out, SR)
        val t = System.nanoTime(); repeat(10) { m.render(out, SR) }
        val ms = (System.nanoTime() - t) / 1e6 / 10
        println("heavy mix: $ms ms CPU per second of audio")
        assertTrue(ms < 250)
    }
}
