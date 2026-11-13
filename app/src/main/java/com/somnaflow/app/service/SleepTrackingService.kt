package com.somnaflow.app.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.somnaflow.app.audio.SoundEngine
import com.somnaflow.app.bio.SnoreDetector
import com.somnaflow.app.data.Night
import com.somnaflow.app.data.SleepMath
import com.somnaflow.app.data.Store
import kotlin.math.abs
import kotlin.math.sqrt

/** Live state of tonight's tracking session, observed by the UI. */
object Tracker {
    var running by mutableStateOf(false); internal set
    var startedAt by mutableLongStateOf(0L); internal set
    var alarmAt by mutableLongStateOf(0L); internal set
    var micOn by mutableStateOf(false); internal set
    var confidence by mutableFloatStateOf(0f); internal set
    var levelDb by mutableFloatStateOf(-90f); internal set
    var breaths by mutableFloatStateOf(0f); internal set
    var snoreMin by mutableIntStateOf(0); internal set
    var pauses by mutableIntStateOf(0); internal set
    var interventions by mutableIntStateOf(0); internal set
    var movesThisMinute by mutableIntStateOf(0); internal set
    var lastNight by mutableStateOf<Night?>(null); internal set
}

/**
 * Overnight foreground service. Phone on the mattress near the pillow, plugged in.
 *  - Accelerometer -> per-minute micro-movement counts -> sleep onset, staging and the smart alarm.
 *  - Microphone -> [SnoreDetector] (features only, audio is never saved) -> micro-haptic / soft buzzer interventions.
 */
class SleepTrackingService : Service(), SensorEventListener {
    companion object {
        @Volatile var instance: SleepTrackingService? = null; private set
        fun start(ctx: Context) { try { ctx.startForegroundService(Intent(ctx, SleepTrackingService::class.java)) } catch (_: Throwable) {} }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var wake: PowerManager.WakeLock? = null
    private val activity = ArrayList<Int>()
    private var moves = 0; private var lastMag = 0f; private var smooth = 0f; private var haveMag = false
    @Volatile private var micRun = false
    private var micThread: Thread? = null
    private val detector = SnoreDetector()
    private var lastIv = 0L; private var level = 1
    private var rang = false; private var wokeBy = "manual"; private var finished = false
    private val breathHistory = ArrayList<Float>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (instance != null) return START_NOT_STICKY
        Store.init(applicationContext); Notif.channels(this)
        val hasMic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val n = Notif.build(this, Notif.CH_TRACK, "Sleep tracking is on", "Sleep well · tap to open", route = "tonight",
            actionLabel = "Stop", actionIntent = Notif.action(this, SomnaReceiver.STOP_TRACK, 13))
        try {
            when {
                hasMic && Build.VERSION.SDK_INT >= 30 -> startForeground(Notif.ID_TRACK, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                Build.VERSION.SDK_INT >= 34 -> startForeground(Notif.ID_TRACK, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                else -> startForeground(Notif.ID_TRACK, n)
            }
        } catch (_: Throwable) { stopSelf(); return START_NOT_STICKY }
        instance = this
        wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "somnaflow:track").also { it.acquire(14 * 3600_000L) }
        val sm = getSystemService(SensorManager::class.java)
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(this, it, 200_000) }
        Tracker.running = true; Tracker.startedAt = System.currentTimeMillis(); Tracker.micOn = hasMic
        Tracker.snoreMin = 0; Tracker.pauses = 0; Tracker.interventions = 0; Tracker.confidence = 0f
        Tracker.alarmAt = if (Store.alarmEnabled) Scheduler.nextAt(Store.wakeMin) else 0L
        Scheduler.all(this)
        if (hasMic) startMic()
        handler.postDelayed(minute, 60_000)
        return START_NOT_STICKY
    }

    // ---- Micro-movements ----
    override fun onSensorChanged(e: SensorEvent) {
        val m = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
        if (!haveMag) { haveMag = true; smooth = m; lastMag = m; return }
        smooth += (m - smooth) * 0.5f
        if (abs(smooth - lastMag) > 0.06f) { moves++; Tracker.movesThisMinute = moves }
        lastMag = smooth
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private val minute = object : Runnable {
        override fun run() {
            if (finished) return
            activity.add(moves); moves = 0; Tracker.movesThisMinute = 0
            Tracker.snoreMin = (detector.snoreSeconds / 60f).toInt(); Tracker.pauses = detector.pauses
            if (detector.breathsPerMin > 0f) breathHistory.add(detector.breathsPerMin)
            checkSmartAlarm()
            handler.postDelayed(this, 60_000)
        }
    }

    /** Inside the wake window, ring as soon as movement or a change in breathing cadence signals light sleep. */
    private fun checkSmartAlarm() {
        val at = Tracker.alarmAt
        if (at <= 0L || rang) return
        val now = System.currentTimeMillis()
        if (now >= at) { Alarms.ring(this, "alarm"); return }
        if (now < at - Store.smartWindow * 60_000L || activity.size < 30) return
        val recent = activity.takeLast(3)
        val moving = recent.last() >= 2 || recent.sum() >= 4
        val br = breathHistory.takeLast(12)
        val breathShift = br.size >= 6 && abs(br.last() - br.dropLast(1).average().toFloat()) > 0.2f * br.dropLast(1).average().toFloat()
        if (moving || breathShift) Alarms.ring(this, "smart")
    }

    fun ring(reason: String) {
        if (rang && AlarmRinger.ringing) return
        rang = true; wokeBy = if (reason == "smart") "smart" else "alarm"
        AlarmRinger.start(this)
        getSystemService(NotificationManager::class.java).notify(Notif.ID_ALARM, Alarms.alarmNotification(this))
    }

    // ---- Respiration / snore monitoring ----
    @SuppressLint("MissingPermission")
    private fun startMic() {
        micRun = true
        micThread = Thread({
            var rec: AudioRecord? = null
            try {
                val min = AudioRecord.getMinBufferSize(SnoreDetector.FS, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SnoreDetector.FS, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 8192))
                rec.startRecording()
                val frame = ShortArray(SnoreDetector.FRAME); var count = 0
                while (micRun) {
                    var got = 0
                    while (got < frame.size && micRun) { val r = rec.read(frame, got, frame.size - got); if (r <= 0) break; got += r }
                    if (got < frame.size) { Thread.sleep(50); continue }
                    // While our own soundscape is audible the microphone would hear it, so analysis waits for the sleep timer.
                    if (SoundEngine.running) { if (count++ % 16 == 0) Tracker.confidence = 0f; continue }
                    val pauseAlert = detector.process(frame)
                    if (count++ % 8 == 0) { Tracker.confidence = detector.confidence; Tracker.levelDb = detector.levelDb; Tracker.breaths = detector.breathsPerMin }
                    val now = System.currentTimeMillis()
                    if (Store.snoreMode != "off" && !AlarmRinger.ringing && ((detector.snoring && now - lastIv > 90_000) || pauseAlert)) {
                        level = if (now - lastIv < 300_000) minOf(3, level + 1) else 1
                        lastIv = now
                        if (Store.snoreMode == "buzzer") Tones.softBuzz(level) else Haptics.micro(this, level)
                        Tracker.interventions++
                    }
                }
            } catch (_: Throwable) { Tracker.micOn = false } finally {
                try { rec?.stop() } catch (_: Throwable) {}
                try { rec?.release() } catch (_: Throwable) {}
            }
        }, "somnaflow-mic").also { it.start() }
    }

    /** Ends the session and saves the night. */
    fun finish() {
        if (finished) return
        finished = true
        handler.removeCallbacks(minute)
        micRun = false
        try { getSystemService(SensorManager::class.java).unregisterListener(this) } catch (_: Throwable) {}
        activity.add(moves)
        val end = System.currentTimeMillis()
        if (end - Tracker.startedAt >= 2 * 60_000L) {
            val epochs = SleepMath.stage(activity.toIntArray())
            val night = Night(Tracker.startedAt, end, epochs, SleepMath.sleepOnset(epochs), (detector.snoreSeconds / 60f).toInt(), detector.episodes,
                detector.pauses, Tracker.interventions, if (breathHistory.isEmpty()) 0f else breathHistory.average().toFloat(), wokeBy)
            Store.add("nights", night.json())
            Tracker.lastNight = night
        }
        if (AlarmRinger.ringing) AlarmRinger.stop(this)
        getSystemService(NotificationManager::class.java).cancel(Notif.ID_ALARM)
        Tracker.running = false
        instance = null
        try { wake?.release() } catch (_: Throwable) {}
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    override fun onDestroy() { if (!finished) finish(); super.onDestroy() }
}
