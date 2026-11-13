package com.somnaflow.app

import com.somnaflow.app.data.Questionnaires
import com.somnaflow.app.data.SleepMath
import org.junit.Assert.*
import org.junit.Test

class SleepMathTest {
    @Test fun debtAccumulatesAndRepays() {
        assertEquals(0.0, SleepMath.debt(List(14) { 8.0 }, 8.0), 1e-9)
        assertEquals(14.0, SleepMath.debt(List(14) { 7.0 }, 8.0), 1e-9)
        assertEquals(1.0, SleepMath.debt(listOf(6.0, null, 9.0), 8.0), 1e-9)
        assertEquals(0.0, SleepMath.debt(listOf(10.0, 10.0), 8.0), 1e-9)
    }
    @Test fun energyCurveHasAfternoonDipAndDebtPenalty() {
        val good = SleepMath.energyCurve(390, 1350, 8.0, 8.0, 0.0); val bad = SleepMath.energyCurve(390, 1350, 5.0, 8.0, 10.0)
        assertTrue(good[10 * 4] > bad[10 * 4] + 10)
        assertEquals(3f, good[3 * 4], 0.01f)
        assertTrue(good.all { it in 0f..100f })
        println("energy good: " + (6..22).joinToString { "%d:%.0f".format(it, good[it * 4]) })
    }
    @Test fun stagingAndOnset() {
        val act = IntArray(120) { if (it < 18) 6 else if (it == 70) 12 else 0 }
        val e = SleepMath.stage(act); println(e)
        val sol = SleepMath.sleepOnset(e)
        assertTrue("sol=$sol", sol in 15..24); assertTrue(e.contains('D')); assertEquals('W', e[70])
    }
    @Test fun psqiScoring() {
        val best = IntArray(18).also { it[0] = 23 * 60; it[1] = 10; it[2] = 7 * 60; it[3] = 78 }
        assertEquals(0, Questionnaires.PSQI.score(best))
        val worst = IntArray(18) { 3 }.also { it[0] = 22 * 60; it[1] = 90; it[2] = 8 * 60; it[3] = 40 }
        assertEquals(21, Questionnaires.PSQI.score(worst))
        assertEquals(18, Questionnaires.PSQI.questions.size)
        assertEquals(27, Questionnaires.PHQ9.score(IntArray(9) { 3 })); assertEquals(21, Questionnaires.GAD7.score(IntArray(7) { 3 }))
    }
}
