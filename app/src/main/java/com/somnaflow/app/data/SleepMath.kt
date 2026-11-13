package com.somnaflow.app.data

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** Pure sleep-science calculations (unit-tested). */
object SleepMath {
    /**
     * Rolling sleep debt: each night adds (need - slept); surplus sleep pays debt back but
     * debt never goes negative. Nights with no data are skipped.
     */
    fun debt(hours: List<Double?>, need: Double): Double {
        var d = 0.0
        for (h in hours) if (h != null) d = max(0.0, d + need - h)
        return d
    }

    /**
     * Predicted alertness (0..100) for each quarter hour of the day, from the two-process model:
     * homeostatic sleep pressure S rising through the waking day, plus the circadian rhythm C
     * (24 h cycle peaking early evening, with the 12 h harmonic that gives the post-lunch dip).
     * Short sleep and accumulated debt raise the starting pressure, lowering the whole curve.
     */
    fun energyCurve(wakeMin: Int, bedMin: Int, lastSleepH: Double, need: Double, debt: Double): FloatArray {
        val s0 = (0.12 + 0.5 * ((need - lastSleepH) / need).coerceIn(0.0, 1.0) + min(0.25, debt / 40.0)).coerceIn(0.05, 0.9)
        val awakeLen = ((bedMin - wakeMin + 1440) % 1440).let { if (it == 0) 1440 else it }
        return FloatArray(96) { i ->
            val m = i * 15
            val sinceWake = (m - wakeMin + 1440) % 1440
            val h = m / 60.0
            val c = 0.5 + 0.5 * (0.55 * cos(2 * PI * (h - 18) / 24) + 0.45 * cos(4 * PI * (h - 9.5) / 24))
            if (sinceWake <= awakeLen) {
                val s = 1 - (1 - s0) * exp(-(sinceWake / 60.0) / 18.2)
                // Sleep inertia in the first 45 minutes after waking.
                val inertia = if (sinceWake < 45) 0.25 * (1 - sinceWake / 45.0) else 0.0
                (120 * (0.62 * (1 - s) + 0.5 * c - inertia)).coerceIn(3.0, 100.0).toFloat()
            } else 3f
        }
    }

    /**
     * Actigraphy staging from per-minute movement counts.
     * A minute is Awake when smoothed movement is high; Deep after sustained stillness; otherwise Light.
     */
    fun stage(activity: IntArray): String {
        val n = activity.size
        val sb = StringBuilder(n)
        var still = 0
        for (i in 0 until n) {
            var w = 0f
            for (k in -4..2) { val j = i + k; if (j in 0 until n) w += activity[j] * (if (k == 0) 2f else 1f / (1 + kotlin.math.abs(k))) }
            val awake = w > 9f
            still = if (activity[i] == 0 && !awake) still + 1 else 0
            sb.append(if (awake) 'W' else if (still >= 12) 'D' else 'L')
        }
        return sb.toString()
    }

    /** Sleep onset latency: minutes until the first run of 10 consecutive asleep minutes. */
    fun sleepOnset(epochs: String): Int {
        var run = 0
        for (i in epochs.indices) { run = if (epochs[i] != 'W') run + 1 else 0; if (run >= 10) return i - 9 }
        return epochs.length
    }

    /** Sleep Readiness 0..100 from today's habits. */
    fun readiness(tasksDone: Int, tasksTotal: Int, daylightMin: Int, checkedIn: Boolean, debtH: Double): Int {
        val t = if (tasksTotal == 0) 0.0 else tasksDone.toDouble() / tasksTotal
        val d = min(1.0, daylightMin / 80.0)
        val debt = (1 - debtH / 10.0).coerceIn(0.0, 1.0)
        return (100 * (0.55 * t + 0.18 * d + 0.10 * (if (checkedIn) 1 else 0) + 0.17 * debt)).toInt().coerceIn(0, 100)
    }

    fun fmtClock(min: Int): String { val m = ((min % 1440) + 1440) % 1440; val h = m / 60; val ap = if (h < 12) "AM" else "PM"; val h12 = if (h % 12 == 0) 12 else h % 12; return "%d:%02d %s".format(h12, m % 60, ap) }
    fun fmtDur(min: Int): String = if (min >= 60) "${min / 60}h ${min % 60}m" else "${min}m"
}
