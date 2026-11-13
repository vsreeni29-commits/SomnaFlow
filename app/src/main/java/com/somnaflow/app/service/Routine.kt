package com.somnaflow.app.service

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.somnaflow.app.data.Content
import com.somnaflow.app.data.SleepMath
import com.somnaflow.app.data.Store
import java.time.LocalDateTime
import java.time.ZoneId

/** All time-based automation: wind-down start, wake-up alarm / morning restore, daylight sampling. */
object Scheduler {
    private const val REQ_WIND = 21; private const val REQ_WAKE = 22; private const val REQ_DAY = 23; private const val REQ_SNOOZE = 24

    /** Next time the clock shows [minOfDay], at least one minute from now. */
    fun nextAt(minOfDay: Int): Long {
        val m = ((minOfDay % 1440) + 1440) % 1440
        var t = LocalDateTime.now().withHour(m / 60).withMinute(m % 60).withSecond(0).withNano(0)
        if (!t.isAfter(LocalDateTime.now().plusMinutes(1))) t = t.plusDays(1)
        return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun exact(ctx: Context, at: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: Throwable) { try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) } catch (_: Throwable) {} }
    }

    fun all(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val wind = Notif.action(ctx, SomnaReceiver.WIND_DOWN, REQ_WIND)
        if (Store.windDownEnabled) exact(ctx, nextAt(Store.bedtimeMin - Store.windDownLead), wind) else am.cancel(wind)

        val wake = Notif.action(ctx, SomnaReceiver.WAKE, REQ_WAKE)
        val at = nextAt(Store.wakeMin)
        try {
            if (Store.alarmEnabled && (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()))
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, Notif.open(ctx, "", 8)), wake)
            else exact(ctx, at, wake)
        } catch (_: Throwable) { exact(ctx, at, wake) }

        val day = Notif.action(ctx, SomnaReceiver.DAYLIGHT, REQ_DAY)
        if (Store.daylightPassive) am.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 60_000, AlarmManager.INTERVAL_FIFTEEN_MINUTES, day)
        else am.cancel(day)
    }

    fun snooze(ctx: Context, minutes: Int) = exact(ctx, System.currentTimeMillis() + minutes * 60_000L, Notif.action(ctx, SomnaReceiver.SNOOZE, REQ_SNOOZE))
}

/** The wind-down routine: Do Not Disturb, dimmed display, optional audio, guided stretches. */
object WindDown {
    var active by mutableStateOf(false); private set

    fun refresh() { active = System.currentTimeMillis() < Store.obj("settings").optLong("wdUntil", 0) }
    fun dndGranted(ctx: Context) = ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
    fun canDimSystem(ctx: Context) = Settings.System.canWrite(ctx)

    fun start(ctx: Context, auto: Boolean) {
        val app = ctx.applicationContext
        Store.set("wdUntil", Scheduler.nextAt(Store.wakeMin))
        val nm = app.getSystemService(NotificationManager::class.java)
        if (Store.wdDnd && dndGranted(app)) try {
            if (Store.int("dndPrev", -1) < 0) Store.set("dndPrev", nm.currentInterruptionFilter)
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        } catch (_: Throwable) {}
        if (Store.wdDim && canDimSystem(app)) try {
            val cr = app.contentResolver
            if (Store.int("brightPrev", -1) < 0) {
                Store.set("brightPrev", Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 120))
                Store.set("brightModePrev", Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 1))
            }
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, 12)
        } catch (_: Throwable) {}
        if (auto) {
            Notif.channels(app)
            nm.notify(Notif.ID_WIND, Notif.build(app, Notif.CH_ROUTINE, "Wind-down has started",
                "Bedtime is ${SleepMath.fmtClock(Store.bedtimeMin)}. Notifications are quiet — tap for tonight's routine.", route = "winddown", ongoing = false))
            if (Store.wdAudio && !Player.playing) Player.play(app, Content.recommended(Store.severity()).first())
        }
        refresh()
    }

    fun end(ctx: Context) {
        val app = ctx.applicationContext
        val nm = app.getSystemService(NotificationManager::class.java)
        val dnd = Store.int("dndPrev", -1)
        if (dnd >= 0) { try { if (dndGranted(app)) nm.setInterruptionFilter(dnd) } catch (_: Throwable) {}; Store.set("dndPrev", -1) }
        val b = Store.int("brightPrev", -1)
        if (b >= 0) {
            try { if (canDimSystem(app)) { Settings.System.putInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS, b)
                Settings.System.putInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Store.int("brightModePrev", 1)) } } catch (_: Throwable) {}
            Store.set("brightPrev", -1)
        }
        Store.set("wdUntil", 0L)
        nm.cancel(Notif.ID_WIND)
        refresh()
    }
}

object Alarms {
    fun ring(ctx: Context, reason: String) {
        if (AlarmRinger.ringing) return
        Store.set("alarmHandled", Store.today())
        val svc = SleepTrackingService.instance
        if (svc != null) svc.ring(reason)
        else try { ctx.startForegroundService(Intent(ctx, AlarmService::class.java)) } catch (_: Throwable) {
            AlarmRinger.start(ctx)
            ctx.getSystemService(NotificationManager::class.java).notify(Notif.ID_ALARM, alarmNotification(ctx))
        }
    }
    fun alarmNotification(ctx: Context) = Notif.build(ctx, Notif.CH_ALARM, "Good morning", "Time to wake up · tap to open", route = "alarm",
        actionLabel = "Stop alarm", actionIntent = Notif.action(ctx, SomnaReceiver.DISMISS, 12), fullScreen = true)

    fun dismiss(ctx: Context) {
        AlarmRinger.stop(ctx)
        ctx.getSystemService(NotificationManager::class.java).cancel(Notif.ID_ALARM)
        try { ctx.stopService(Intent(ctx, AlarmService::class.java)) } catch (_: Throwable) {}
        SleepTrackingService.instance?.finish()
        WindDown.end(ctx)
    }
    fun snooze(ctx: Context) {
        AlarmRinger.stop(ctx)
        ctx.getSystemService(NotificationManager::class.java).cancel(Notif.ID_ALARM)
        try { ctx.stopService(Intent(ctx, AlarmService::class.java)) } catch (_: Throwable) {}
        Scheduler.snooze(ctx, 9)
    }
}

/** Rings the alarm when no tracking session is running. */
class AlarmService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notif.channels(this)
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(Notif.ID_ALARM, Alarms.alarmNotification(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            else startForeground(Notif.ID_ALARM, Alarms.alarmNotification(this))
        } catch (_: Throwable) {}
        AlarmRinger.start(this)
        return START_NOT_STICKY
    }
}

class SomnaReceiver : BroadcastReceiver() {
    companion object {
        const val WIND_DOWN = "com.somnaflow.WIND_DOWN"; const val WAKE = "com.somnaflow.WAKE"; const val DAYLIGHT = "com.somnaflow.DAYLIGHT"
        const val SNOOZE = "com.somnaflow.SNOOZE"; const val DISMISS = "com.somnaflow.DISMISS"; const val STOP_AUDIO = "com.somnaflow.STOP_AUDIO"
        const val STOP_TRACK = "com.somnaflow.STOP_TRACK"
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.applicationContext
        Store.init(app); Notif.channels(app)
        when (intent.action) {
            WIND_DOWN -> { WindDown.start(app, auto = true); Scheduler.all(app) }
            WAKE -> {
                val tracking = SleepTrackingService.instance != null
                if (Store.alarmEnabled && Store.str("alarmHandled", "") != Store.today()) Alarms.ring(app, "alarm")
                else if (!tracking) WindDown.end(app)
                Scheduler.all(app)
            }
            SNOOZE -> { Store.set("alarmHandled", ""); Alarms.ring(app, "snooze") }
            DISMISS -> Alarms.dismiss(app)
            STOP_AUDIO -> Player.stop(app)
            STOP_TRACK -> SleepTrackingService.instance?.finish()
            DAYLIGHT -> sampleDaylight(app)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Scheduler.all(app)
        }
    }

    /** Reads the ambient light sensor for a moment; bright outdoor-level light credits the sampling interval. */
    private fun sampleDaylight(ctx: Context) {
        val hour = LocalDateTime.now().hour
        if (!Store.daylightPassive || hour < 6 || hour >= 20) return
        val sm = ctx.getSystemService(SensorManager::class.java)
        val light = sm.getDefaultSensor(Sensor.TYPE_LIGHT) ?: return
        val pending = goAsync()
        var peak = 0f
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { if (e.values[0] > peak) peak = e.values[0] }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.registerListener(l, light, SensorManager.SENSOR_DELAY_FASTEST)
        Handler(Looper.getMainLooper()).postDelayed({
            sm.unregisterListener(l)
            if (peak >= Daylight.OUTDOOR_LUX) Store.addDaylight(15)
            Store.set("lastLux", peak.toDouble())
            pending.finish()
        }, 2500)
    }
}

object Daylight {
    /** Shade outdoors is 1,000+ lux; indoor lighting is typically under 500. */
    const val OUTDOOR_LUX = 1000f
    const val TARGET_MIN = 80; const val TARGET_MAX = 120
}
