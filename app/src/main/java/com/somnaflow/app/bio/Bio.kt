package com.somnaflow.app.bio

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.somnaflow.app.audio.Beat
import com.somnaflow.app.audio.SoundEngine
import com.somnaflow.app.data.Store
import com.somnaflow.app.service.Player
import org.json.JSONObject
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Biofeedback hub. Any EEG source (BLE headband or the built-in demo signal) pushes samples here;
 * any heart source (BLE strap or the camera pulse sensor) pushes RR intervals.
 * Runs the closed loop: sustained high beta -> 6 Hz theta protocol until alpha/theta dominance returns.
 */
object Bio {
    var eegSource by mutableStateOf(""); internal set
    var bands by mutableStateOf(FloatArray(5)); private set
    var brain by mutableStateOf(BrainState.NO_SIGNAL); private set
    var loopActive by mutableStateOf(false); private set
    var loopCount by mutableIntStateOf(0); private set
    /** Recent fast-wave (beta+gamma) share, newest last: drawn as the arousal trend. */
    var trend by mutableStateOf(listOf<Float>()); private set

    var hrSource by mutableStateOf(""); internal set
    var heartRate by mutableFloatStateOf(0f); private set
    var rmssd by mutableFloatStateOf(0f); private set
    var baseline by mutableFloatStateOf(0f); private set
    var changePct by mutableFloatStateOf(0f); private set
    var beats by mutableIntStateOf(0); private set

    private var appCtx: Context? = null
    private val analyzers = HashMap<Int, EegAnalyzer>()
    private val loop = ClosedLoop()
    private var hrv = HrvTracker()
    private var demo: Thread? = null

    fun init(ctx: Context) { appCtx = ctx.applicationContext }

    /** One EEG sample in microvolts from channel [ch] sampled at [fs] Hz. Thread-safe. */
    @Synchronized fun onEeg(ch: Int, uV: Float, fs: Int) {
        val a = analyzers.getOrPut(ch) { EegAnalyzer(fs) }
        if (!a.push(uV) || ch != analyzers.keys.min()) return
        // Average relative band power over every channel with a clean signal.
        val clean = analyzers.values.filter { it.state != BrainState.NO_SIGNAL }
        if (clean.isEmpty()) { brain = BrainState.NO_SIGNAL; return }
        val avg = FloatArray(5) { b -> clean.map { it.bands[b] }.average().toFloat() }
        bands = avg
        val fast = avg[3] + avg[4]
        val state = clean.groupingBy { it.state }.eachCount().maxByOrNull { it.value }!!.key
        brain = state
        trend = (trend + fast).takeLast(240)
        if (!Store.closedLoop) { if (loop.intervening) { loop.reset(); loopActive = false }; return }
        when (loop.update(state, 0.5f)) {
            ClosedLoop.Action.START -> { loopActive = true; loopCount = loop.interventions; appCtx?.let { c -> main { Player.ensureTheta(c) } } }
            ClosedLoop.Action.STOP -> { loopActive = false; appCtx?.let { c -> main { if (Player.startedByLoop) Player.stop(c, 20f) } } }
            null -> {}
        }
    }

    private fun main(f: () -> Unit) = android.os.Handler(android.os.Looper.getMainLooper()).post(f)

    @Synchronized fun clearEeg() { analyzers.clear(); loop.reset(); loopActive = false; brain = BrainState.NO_SIGNAL; bands = FloatArray(5); trend = emptyList(); eegSource = "" }

    @Synchronized fun onRr(ms: Float) {
        hrv.add(ms)
        heartRate = hrv.heartRate; rmssd = hrv.rmssd; baseline = hrv.baseline; changePct = hrv.changePct; beats = hrv.beats
    }
    @Synchronized fun resetHeart() { hrv = HrvTracker(); heartRate = 0f; rmssd = 0f; baseline = 0f; changePct = 0f; beats = 0 }
    fun saveHeartSession() {
        if (rmssd <= 0f || beats < 30) return
        Store.add("hrv", JSONObject().put("t", System.currentTimeMillis()).put("hr", heartRate.toDouble()).put("rmssd", rmssd.toDouble())
            .put("base", baseline.toDouble()).put("src", hrSource))
    }

    // ---- Built-in demo EEG, so the closed loop can be tried without a headband ----
    val demoRunning get() = demo != null
    fun startDemo() {
        if (demo != null) return
        clearEeg(); eegSource = "Demo signal"
        demo = Thread({
            val r = Random(); val fs = 256; var arousal = 0.92; var i = 0L; var pink = 0.0
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val calming = SoundEngine.running && SoundEngine.mixer.config.beat != Beat.OFF
                    repeat(32) {
                        // Entrainment audio pulls the simulated brain from beta toward alpha, then theta.
                        arousal += ((if (calming) 0.05 else 0.92) - arousal) / (fs * (if (calming) 22.0 else 45.0))
                        val t = i++ / fs.toDouble()
                        pink = pink * 0.97 + r.nextGaussian() * 1.5
                        val beta = 22 * arousal * sin(2 * PI * 21 * t) + 9 * arousal * sin(2 * PI * 27 * t + 1)
                        val alpha = 20 * (1 - abs(arousal - 0.45) * 2).coerceAtLeast(0.0) * sin(2 * PI * 10 * t)
                        val theta = 22 * (1 - arousal).let { it * it } * sin(2 * PI * 6 * t)
                        onEeg(0, (beta + alpha + theta + pink + r.nextGaussian() * 2).toFloat(), fs)
                    }
                    Thread.sleep(125)
                }
            } catch (_: InterruptedException) {}
        }, "somnaflow-demo-eeg").also { it.start() }
    }
    fun stopDemo() { demo?.interrupt(); demo = null; clearEeg() }
}
