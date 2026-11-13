package com.somnaflow.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process

/** Process-wide audio output: one streaming AudioTrack fed by the [Mixer] on a dedicated thread. */
object SoundEngine {
    val mixer = Mixer()
    @Volatile var running = false; private set
    @Volatile private var thread: Thread? = null
    @Volatile private var stopWhenSilent = false

    @Synchronized fun start(fadeSeconds: Float = 3f) {
        stopWhenSilent = false
        mixer.fadeTo(1f, fadeSeconds)
        if (running) return
        running = true
        thread = Thread({ loop() }, "somnaflow-audio").also { it.start() }
    }

    /** Fades out and then releases the audio device. */
    fun stop(fadeSeconds: Float = 2f) {
        if (!running) return
        mixer.fadeTo(0f, fadeSeconds)
        stopWhenSilent = true
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val frames = 2048
        val buf = FloatArray(frames * 2)
        var track: AudioTrack? = null
        try {
            val format = AudioFormat.Builder().setSampleRate(SR).setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build()
            val min = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
            track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(format).setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(maxOf(min, frames * 2 * 4 * 2))
                .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE).build()
            track.play()
            while (running) {
                mixer.render(buf, frames)
                if (track.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING) < 0) break
                if (stopWhenSilent && mixer.isSilent) {
                    synchronized(this) { if (stopWhenSilent) running = false }
                }
            }
        } catch (_: Throwable) {
            running = false
        } finally {
            try { track?.stop() } catch (_: Throwable) {}
            try { track?.release() } catch (_: Throwable) {}
        }
    }
}
