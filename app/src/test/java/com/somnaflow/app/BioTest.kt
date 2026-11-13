package com.somnaflow.app

import com.somnaflow.app.bio.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

class BioTest {
    private fun eeg(fs: Int, sec: Int, hz: Double, amp: Double, an: EegAnalyzer, r: Random) {
        for (i in 0 until fs * sec) an.push((amp * sin(2 * PI * hz * i / fs) + 4 * r.nextGaussian() + 15 * sin(2 * PI * 50.0 * i / fs)).toFloat())
    }
    @Test fun eegBandsAndStates() {
        for (fs in listOf(250, 256, 500)) {
            val r = Random(1)
            val a = EegAnalyzer(fs); eeg(fs, 12, 22.0, 20.0, a, r)
            println("fs=$fs beta: ${a.bands.joinToString { "%.2f".format(it) }} ${a.state}")
            assertEquals(BrainState.HYPERAROUSAL, a.state); assertTrue(a.bands[3] > 0.6f)
            eeg(fs, 12, 10.0, 25.0, a, r); assertEquals(BrainState.RELAXED, a.state)
            eeg(fs, 12, 6.0, 25.0, a, r); assertEquals(BrainState.DROWSY, a.state); assertTrue(a.bands[1] > 0.6f)
            eeg(fs, 12, 2.0, 40.0, a, r); assertEquals(BrainState.DEEP, a.state)
        }
    }
    @Test fun closedLoopStartsOnBetaStopsOnTheta() {
        val c = ClosedLoop(); var a: ClosedLoop.Action? = null
        repeat(10) { a = c.update(BrainState.HYPERAROUSAL, 0.5f) ?: a }
        assertNull(a)
        repeat(8) { a = c.update(BrainState.HYPERAROUSAL, 0.5f) ?: a }
        assertEquals(ClosedLoop.Action.START, a); assertTrue(c.intervening)
        a = null; repeat(80) { a = c.update(BrainState.RELAXED, 0.5f) ?: a }; assertNull(a)
        repeat(20) { a = c.update(BrainState.DROWSY, 0.5f) ?: a }
        assertEquals(ClosedLoop.Action.STOP, a); assertFalse(c.intervening)
    }
    @Test fun rmssdAndRelaxationShift() {
        assertEquals(10f, HrvTracker.rmssd(listOf(800f, 810f, 800f, 810f)), 0.001f)
        val h = HrvTracker(); val r = Random(2)
        repeat(90) { h.add(850f + (r.nextGaussian() * 20).toFloat()) }
        assertEquals(70f, h.heartRate, 3f); assertTrue(h.baseline > 15f)
        repeat(90) { h.add(950f + (r.nextGaussian() * 32).toFloat()) }
        println("rmssd base=${h.baseline} now=${h.rmssd} change=${h.changePct}%")
        assertTrue(h.relaxed)
    }
    @Test fun ppgFindsBeats() {
        val rr = ArrayList<Float>(); val p = PpgProcessor { rr.add(it) }
        var phase = 0.0
        for (i in 0 until 30 * 40) { val t = i * 1000.0 / 30; val f = 1.2 + 0.08 * sin(2 * PI * 0.25 * t / 1000); phase += f / 30
            p.add(t, 120 + 3 * sin(2 * PI * phase) + 1.0 * sin(4 * PI * phase) + 0.01 * t / 1000) }
        val mean = rr.drop(3).average()
        println("ppg beats=${rr.size} meanRR=$mean quality=${p.quality}")
        assertEquals(833.0, mean, 30.0); assertTrue(rr.size > 40); assertTrue(p.quality > 0.8f)
    }

    private val rnd = Random(3)
    private fun feed(d: SnoreDetector, seconds: Float, gen: (Int) -> Float): Int {
        var alerts = 0; val frame = ShortArray(SnoreDetector.FRAME); var n = 0
        repeat((seconds / SnoreDetector.FRAME_SEC).toInt()) {
            for (i in frame.indices) frame[i] = (gen(n++) * 32767).toInt().coerceIn(-32768, 32767).toShort()
            if (d.process(frame)) alerts++
        }
        return alerts
    }
    private var lp = 0f
    private fun snoreSample(n: Int, period: Float = 4f): Float {
        val t = n / 16000f; val inBurst = (t % period) < 1.1f
        lp += (rnd.nextGaussian().toFloat() - lp) * 0.08f
        val bg = rnd.nextGaussian().toFloat() * 0.002f
        return if (inBurst) lp * 0.5f * (0.6f + 0.4f * sin(2 * PI * 35 * t).toFloat()) + 0.08f * sin(2 * PI * 110 * t).toFloat() + bg else bg
    }
    @Test fun snoringDetectedAbove90Percent() {
        val d = SnoreDetector(); feed(d, 40f) { snoreSample(it) }
        println("snore conf=${d.confidence} br=${d.breathsPerMin} snoreSec=${d.snoreSeconds}")
        assertTrue(d.confidence > 0.9f); assertTrue(d.snoring); assertEquals(15f, d.breathsPerMin, 1.5f)
    }
    @Test fun pauseDetectedAndAlertedOnce() {
        val d = SnoreDetector(); var alerts = feed(d, 40f) { snoreSample(it) }
        alerts += feed(d, 20f) { rnd.nextGaussian().toFloat() * 0.002f }
        alerts += feed(d, 12f) { snoreSample(it) }
        println("pauses=${d.pauses} alerts=$alerts")
        assertEquals(1, d.pauses); assertEquals(1, alerts)
    }
    @Test fun steadyNoiseAndIrregularSpeechAreNotSnoring() {
        val d = SnoreDetector(); feed(d, 60f) { rnd.nextGaussian().toFloat() * 0.05f }
        assertFalse(d.snoring); assertEquals(0f, d.snoreSeconds, 0.01f)
        val d2 = SnoreDetector(); var on = false; var left = 0; var hp = 0f; var maxConf = 0f
        repeat(15) { feed(d2, 4f) { if (left-- <= 0) { on = !on; left = (16000 * (0.15f + rnd.nextFloat() * (if (on) 0.9f else 1.6f))).toInt() }
            val w = rnd.nextGaussian().toFloat(); val x = w - hp; hp = w; if (on) x * 0.2f else w * 0.002f }; maxConf = maxOf(maxConf, d2.confidence) }
        println("speech-like max conf=$maxConf")
        assertTrue(maxConf < 0.9f)
    }
}
