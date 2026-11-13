package com.somnaflow.app.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.somnaflow.app.audio.Beat
import com.somnaflow.app.audio.Fader
import com.somnaflow.app.audio.MixConfig
import com.somnaflow.app.audio.SoundEngine
import com.somnaflow.app.data.Store
import com.somnaflow.app.data.Track
import org.json.JSONObject
import java.util.Locale

/** App-wide playback state. Lives for the life of the process so audio persists across every screen. */
object Player {
    var track by mutableStateOf<Track?>(null); private set
    var playing by mutableStateOf(false); private set
    var mix by mutableStateOf(MixConfig()); private set
    /** Epoch millis when the sleep timer will fade the audio out; 0 = play until stopped. */
    var timerEnd by mutableLongStateOf(0L); private set
    var narrating by mutableStateOf(false); internal set
    var caption by mutableStateOf(""); internal set
    var ttsMissing by mutableStateOf(false); internal set
    /** True when the current playback was started automatically by the EEG closed loop. */
    var startedByLoop = false; private set

    private val handler = Handler(Looper.getMainLooper())
    private val timerRun = Runnable { appCtx?.let { stop(it, 30f) } }
    private var appCtx: Context? = null

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        Store.loadMix()?.let { mix = it; SoundEngine.mixer.config = it }
    }

    fun updateMix(c: MixConfig, persist: Boolean = true) {
        mix = c; SoundEngine.mixer.config = c
        if (persist) Store.saveMix(c)
    }

    fun play(ctx: Context, t: Track?, byLoop: Boolean = false, timerMin: Int = Store.timerMin) {
        val app = ctx.applicationContext; appCtx = app
        Narrator.stop()
        if (t != null) {
            track = t
            // Keep the listener's own master level when switching tracks.
            updateMix(t.mix.withFader(Fader.MASTER, mix.faders[Fader.MASTER]), persist = false)
            Store.add("sessions", JSONObject().put("t", System.currentTimeMillis()).put("id", t.id))
        }
        startedByLoop = byLoop
        SoundEngine.start(3f)
        playing = true
        setTimer(timerMin)
        try { app.startForegroundService(Intent(app, PlaybackService::class.java)) } catch (_: Throwable) {}
        if (t != null && t.script.isNotEmpty()) Narrator.speak(app, t.script)
    }

    fun toggle(ctx: Context) { if (playing) stop(ctx) else play(ctx, null) }

    fun stop(ctx: Context, fadeSec: Float = 2f) {
        Narrator.stop()
        SoundEngine.stop(fadeSec)
        playing = false; timerEnd = 0L; startedByLoop = false
        handler.removeCallbacks(timerRun)
        val app = ctx.applicationContext
        handler.postDelayed({ if (!playing) try { app.stopService(Intent(app, PlaybackService::class.java)) } catch (_: Throwable) {} }, (fadeSec * 1000).toLong() + 500)
    }

    fun setTimer(min: Int) {
        handler.removeCallbacks(timerRun)
        if (min <= 0 || !playing) { timerEnd = 0L; return }
        timerEnd = System.currentTimeMillis() + min * 60_000L
        handler.postDelayed(timerRun, min * 60_000L)
    }

    /** Closed-loop helper: make sure a 6 Hz theta beat is sounding. */
    fun ensureTheta(ctx: Context) {
        if (!playing) play(ctx, com.somnaflow.app.data.Content.byId("theta"), byLoop = true, timerMin = 0)
        else if (mix.beat != Beat.THETA) updateMix(mix.copy(beat = Beat.THETA), persist = false)
    }
}

/** Slow, low narration through the phone's built-in text-to-speech engine (works offline, no cost). */
object Narrator {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: List<String>? = null
    private val captions = HashMap<String, String>()
    private var lastId = ""

    fun speak(ctx: Context, script: List<String>) {
        pending = script
        if (tts == null) {
            tts = TextToSpeech(ctx.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) { configure(); pending?.let { enqueue(it) } } else { Player.ttsMissing = true; tts = null }
            }
        } else if (ready) enqueue(script)
    }

    private fun configure() {
        val t = tts ?: return
        try {
            val r = t.setLanguage(Locale.US)
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) t.language = Locale.getDefault()
            t.setSpeechRate(0.78f); t.setPitch(0.88f)
            t.setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
        } catch (_: Throwable) {}
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) { captions[id]?.let { Player.caption = it } }
            override fun onDone(id: String) { if (id == lastId) finished() }
            @Deprecated("Deprecated in Java") override fun onError(id: String) { if (id == lastId) finished() }
        })
    }

    private fun finished() { Player.narrating = false; Player.caption = ""; SoundEngine.mixer.duck = 1f }

    private fun enqueue(script: List<String>) {
        val t = tts ?: return
        pending = null; captions.clear()
        t.stop()
        var n = 0
        fun say(text: String) { val id = "u${n++}"; captions[id] = text; t.speak(text, TextToSpeech.QUEUE_ADD, Bundle(), id); lastId = id }
        fun pause(ms: Long) { val id = "p${n++}"; t.playSilentUtterance(ms, TextToSpeech.QUEUE_ADD, id); lastId = id }
        pause(2500)
        for (para in script) {
            // Split on "[n]" pause marks, then into sentences, so pacing stays slow and even.
            var rest = para
            while (rest.isNotEmpty()) {
                val m = Regex("\\[(\\d+)]").find(rest)
                val chunk = (if (m == null) rest else rest.substring(0, m.range.first)).trim()
                for (s in chunk.split(Regex("(?<=[.?!])\\s+"))) if (s.isNotBlank()) { say(s.trim()); pause(900) }
                if (m == null) break
                pause(m.groupValues[1].toLong() * 1000)
                rest = rest.substring(m.range.last + 1)
            }
            pause(2200)
        }
        Player.narrating = true
        SoundEngine.mixer.duck = 0.8f
    }

    fun stop() {
        pending = null
        try { tts?.stop() } catch (_: Throwable) {}
        if (Player.narrating) finished()
    }
}
