package com.somnaflow.app.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object Haptics {
    private fun vib(ctx: Context): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") (ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
    } catch (_: Throwable) { null }

    private fun run(ctx: Context, timings: LongArray, amps: IntArray, repeat: Int = -1) {
        val v = vib(ctx) ?: return
        try {
            val e = VibrationEffect.createWaveform(timings, amps, repeat)
            if (Build.VERSION.SDK_INT >= 33) v.vibrate(e, VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_ALARM).build())
            else @Suppress("DEPRECATION") v.vibrate(e, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        } catch (_: Throwable) {}
    }

    /** Sub-awakening micro-haptic: a few short, soft pulses. Level 1..3 lengthens and strengthens them slightly. */
    fun micro(ctx: Context, level: Int) {
        val a = when (level) { 1 -> 50; 2 -> 85; else -> 130 }
        val on = 70L + 30L * level
        run(ctx, longArrayOf(0, on, 220, on, 220, on, 400, on), intArrayOf(0, a, 0, a, 0, a, 0, a))
    }
    fun alarm(ctx: Context) = run(ctx, longArrayOf(0, 400, 1600), intArrayOf(0, 140, 0), 0)
    fun cancel(ctx: Context) { try { vib(ctx)?.cancel() } catch (_: Throwable) {} }
}

object Tones {
    private fun track(pcm: ShortArray, usage: Int): AudioTrack =
        AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(22050).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build().also { it.write(pcm, 0, pcm.size) }

    /** Soft low buzzer for the snore micro-intervention (an alternative to vibration). */
    fun softBuzz(level: Int) {
        try {
            val n = 22050 * 6 / 10
            val pcm = ShortArray(n) { i -> val t = i / 22050.0; val env = sin(PI * i / n)
                (sin(2 * PI * 170 * t) * env * env * 2500 * level).toInt().toShort() }
            val tr = track(pcm, AudioAttributes.USAGE_MEDIA); tr.play()
            Handler(Looper.getMainLooper()).postDelayed({ try { tr.release() } catch (_: Throwable) {} }, 1500)
        } catch (_: Throwable) {}
    }

    /** Four-second gentle chime phrase used by the wake-up alarm. */
    fun chimeLoop(): AudioTrack {
        val sr = 22050; val n = sr * 4; val pcm = ShortArray(n)
        val notes = listOf(523.25 to 0.0, 659.25 to 0.55, 783.99 to 1.1, 1046.5 to 1.75)
        for ((f, at) in notes) for (i in (at * sr).toInt() until n) {
            val t = (i - at * sr) / sr
            val v = (sin(2 * PI * f * t) + 0.3 * sin(4 * PI * f * t)) * exp(-t * 2.2) * minOf(1.0, t * 200) * 5200
            pcm[i] = (pcm[i] + v).toInt().coerceIn(-32000, 32000).toShort()
        }
        return track(pcm, AudioAttributes.USAGE_ALARM).also { it.setLoopPoints(0, n, -1) }
    }
}

/** Gentle ascending wake-up: chimes that fade in over 90 seconds, with vibration joining after 30 seconds. */
object AlarmRinger {
    var ringing by mutableStateOf(false); private set
    private var track: AudioTrack? = null
    private val handler = Handler(Looper.getMainLooper())
    private var startedAt = 0L
    private var appCtx: Context? = null
    private val ramp = object : Runnable {
        override fun run() {
            val sec = (System.currentTimeMillis() - startedAt) / 1000f
            try { track?.setVolume((0.04f + sec / 90f).coerceAtMost(1f)) } catch (_: Throwable) {}
            if (sec in 30f..31f) appCtx?.let { Haptics.alarm(it) }
            if (ringing && sec < 600f) handler.postDelayed(this, 1000) else if (ringing) appCtx?.let { stop(it) }
        }
    }
    fun start(ctx: Context) {
        if (ringing) return
        appCtx = ctx.applicationContext; ringing = true; startedAt = System.currentTimeMillis()
        try { track = Tones.chimeLoop().also { it.setVolume(0.04f); it.play() } } catch (_: Throwable) {}
        handler.post(ramp)
    }
    fun stop(ctx: Context) {
        ringing = false
        handler.removeCallbacks(ramp); Haptics.cancel(ctx)
        try { track?.stop(); track?.release() } catch (_: Throwable) {}
        track = null
    }
}
