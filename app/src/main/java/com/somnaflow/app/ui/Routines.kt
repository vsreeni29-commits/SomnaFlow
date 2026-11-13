package com.somnaflow.app.ui

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.somnaflow.app.bio.Health
import com.somnaflow.app.data.Content
import com.somnaflow.app.data.SleepMath
import com.somnaflow.app.data.Store
import com.somnaflow.app.service.Daylight
import com.somnaflow.app.service.Narrator
import com.somnaflow.app.service.Player
import com.somnaflow.app.service.Scheduler
import com.somnaflow.app.service.WindDown
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

private fun open(ctx: Context, action: String, withPackage: Boolean = false) = try {
    ctx.startActivity(Intent(action).apply { if (withPackage) data = Uri.parse("package:${ctx.packageName}"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
} catch (_: Throwable) {}

private fun setAndReschedule(ctx: Context, key: String, v: Any) { Store.set(key, v); try { Scheduler.all(ctx) } catch (_: Throwable) {} }

@Composable fun WindDownScreen() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1500); tick++ } }   // re-check permissions after returning from Settings
    val dnd = remember(tick) { WindDown.dndGranted(ctx) }; val dim = remember(tick) { WindDown.canDimSystem(ctx) }
    Screen("Wind-down", "Starts automatically at ${SleepMath.fmtClock(Store.bedtimeMin - Store.windDownLead)}") {
        Panel(color = C.raised) {
            T(if (WindDown.active) "Wind-down is on" else "Wind-down is off", 17, weight = FontWeight.SemiBold)
            T(if (WindDown.active) "Notifications are quiet and the display is dimmed until morning." else "Start it now, or let it begin by itself ${Store.windDownLead} minutes before bed.", 13, C.muted)
            Spacer(Modifier.height(12.dp))
            if (WindDown.active) BigButton("End wind-down", secondary = true) { WindDown.end(ctx) } else BigButton("Start wind-down now", icon = Icons.Rounded.SelfImprovement) { WindDown.start(ctx, auto = false) }
        }
        Section("Tonight's steps")
        BigButton("1 · State-of-mind check-in", secondary = true, icon = Icons.Rounded.Mood) { Nav.go("checkin") }
        Spacer(Modifier.height(8.dp)); BigButton("2 · Guided stretches (5 min)", secondary = true, icon = Icons.Rounded.AccessibilityNew) { Nav.go("stretch") }
        Spacer(Modifier.height(8.dp)); BigButton("3 · Play tonight's sleep audio", secondary = true, icon = Icons.Rounded.PlayArrow) { Player.play(ctx, Content.recommended(Store.severity()).first()); Nav.go("player") }
        Spacer(Modifier.height(8.dp)); BigButton("4 · Start sleep session", secondary = true, icon = Icons.Rounded.NightsStay) { Nav.go("tonight") }

        Section("Automation")
        Panel {
            Toggle("Automatic wind-down", "Runs every evening before bedtime", Store.windDownEnabled) { setAndReschedule(ctx, "wdEnabled", it) }
            Stepper("Lead time before bed", "${Store.windDownLead} min", { setAndReschedule(ctx, "windDownLead", (Store.windDownLead - 5).coerceAtLeast(30)) }, { setAndReschedule(ctx, "windDownLead", (Store.windDownLead + 5).coerceAtMost(60)) })
            Toggle("Silence notifications", if (dnd) "Turns on Do Not Disturb (alarms still ring)" else "Needs Do Not Disturb access — tap the button below", Store.wdDnd) { Store.set("wdDnd", it) }
            if (Store.wdDnd && !dnd) BigButton("Allow Do Not Disturb access", secondary = true) { open(ctx, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) }
            Toggle("Dim the display", if (dim) "Dims SomnaFlow and lowers system brightness" else "Dims SomnaFlow now; allow system settings to dim the whole phone", Store.wdDim) { Store.set("wdDim", it) }
            if (Store.wdDim && !dim) BigButton("Allow changing system brightness", secondary = true) { open(ctx, Settings.ACTION_MANAGE_WRITE_SETTINGS, true) }
            Toggle("Start sleep audio automatically", "Begins your recommended track when wind-down starts", Store.wdAudio) { Store.set("wdAudio", it) }
        }
    }
}

@Composable fun StretchScreen() {
    val ctx = LocalContext.current
    var i by remember { mutableIntStateOf(0) }
    var left by remember { mutableIntStateOf(Content.STRETCHES[0].second) }
    var running by remember { mutableStateOf(false) }
    LaunchedEffect(i, running) {
        if (!running) return@LaunchedEffect
        Narrator.speak(ctx, listOf(Content.STRETCHES[i].first + ". " + Content.STRETCHES[i].third))
        while (left > 0) { delay(1000); left-- }
        if (i < Content.STRETCHES.size - 1) { i++; left = Content.STRETCHES[i].second } else { running = false; Store.setTask("stretch", true); i = Content.STRETCHES.size }
    }
    DisposableEffect(Unit) { onDispose { Narrator.stop() } }
    Screen("Guided stretches", "Six slow stretches to release the day") {
        if (i >= Content.STRETCHES.size) {
            Panel(color = C.raised) { T("Routine complete", 18, weight = FontWeight.SemiBold); T("Your body is ready for bed. This is ticked off on tonight's worklist.", 13, C.muted) }
            Spacer(Modifier.height(12.dp)); BigButton("Done") { Nav.back() }
            return@Screen
        }
        val (name, secs, how) = Content.STRETCHES[i]
        T("Stretch ${i + 1} of ${Content.STRETCHES.size}", 13, C.muted)
        T(name, 24, weight = FontWeight.SemiBold)
        Box(Modifier.fillMaxWidth().height(200.dp), Alignment.Center) {
            Canvas(Modifier.size(170.dp)) {
                val st = Stroke(12.dp.toPx(), cap = StrokeCap.Round)
                drawArc(C.raised, 0f, 360f, false, style = st); drawArc(C.primary, -90f, 360f * left / secs, false, style = st)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) { T("$left", 40, weight = FontWeight.Bold); T("seconds", 12, C.muted) }
        }
        Panel { T(how, 15, align = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        Spacer(Modifier.height(14.dp))
        BigButton(if (running) "Pause" else if (left < secs) "Resume" else "Begin", icon = if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow) { running = !running; if (!running) Narrator.stop() }
        Spacer(Modifier.height(8.dp))
        BigButton("Skip this stretch", secondary = true) { if (i < Content.STRETCHES.size - 1) { i++; left = Content.STRETCHES[i].second } else { Store.setTask("stretch", true); i = Content.STRETCHES.size; running = false } }
    }
}

@Composable fun DaylightScreen() {
    val ctx = LocalContext.current
    var lux by remember { mutableFloatStateOf(-1f) }
    var session by remember { mutableStateOf(false) }
    var brightSec by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(SensorManager::class.java); val s = sm.getDefaultSensor(Sensor.TYPE_LIGHT)
        val l = object : SensorEventListener { override fun onSensorChanged(e: SensorEvent) { lux = e.values[0] }; override fun onAccuracyChanged(s: Sensor?, a: Int) {} }
        if (s != null) sm.registerListener(l, s, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { sm.unregisterListener(l) }
    }
    LaunchedEffect(session) { while (session) { delay(1000); if (lux >= Daylight.OUTDOOR_LUX) { brightSec++; if (brightSec % 60 == 0) Store.addDaylight(1) } } }
    val min = Store.daylightMin()
    Screen("Daylight", "Target ${Daylight.TARGET_MIN}–${Daylight.TARGET_MAX} minutes outdoors each day") {
        Box(Modifier.fillMaxWidth().height(210.dp), Alignment.Center) {
            Canvas(Modifier.size(180.dp)) {
                val st = Stroke(14.dp.toPx(), cap = StrokeCap.Round)
                drawArc(C.raised, 0f, 360f, false, style = st); drawArc(C.warm, -90f, 360f * (min / Daylight.TARGET_MIN.toFloat()).coerceAtMost(1f), false, style = st)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) { T("$min", 44, weight = FontWeight.Bold); T("minutes today", 12, C.muted) }
        }
        T(if (min >= Daylight.TARGET_MAX) "Excellent — your body clock has a strong daytime signal." else if (min >= Daylight.TARGET_MIN) "Target reached. Daylight is anchoring tonight's melatonin release."
          else "${Daylight.TARGET_MIN - min} more minutes of outdoor light will strengthen tonight's sleep signal. Morning light counts most.", 14, C.muted, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Section("Light right now")
        Panel {
            T(if (lux < 0) "This phone has no light sensor — log minutes by hand below." else "${lux.toInt()} lux · " + if (lux >= Daylight.OUTDOOR_LUX) "outdoor-level daylight" else if (lux >= 300) "bright indoor light" else "dim light", 15,
                if (lux >= Daylight.OUTDOOR_LUX) C.warm else C.text)
            if (lux >= 0) { Spacer(Modifier.height(8.dp)); Bar((kotlin.math.log10(lux + 1f) / 5f), color = C.warm, mark = 0.6f) }
            Spacer(Modifier.height(12.dp))
            BigButton(if (session) "Stop outdoor session · ${brightSec / 60} min counted" else "I'm outside · count my daylight", secondary = !session, icon = Icons.Rounded.WbSunny, enabled = lux >= 0) { session = !session }
            if (session) Note("Keep the screen facing the sky. Minutes count only while the sensor reads outdoor-level light.")
        }
        Spacer(Modifier.height(10.dp))
        Panel {
            Stepper("Log by hand", "$min min", { Store.addDaylight(-10) }, { Store.addDaylight(10) })
            Toggle("Passive daylight sampling", "Checks the light sensor every 15 minutes between 6 AM and 8 PM", Store.daylightPassive) { setAndReschedule(ctx, "daylightPassive", it) }
        }
        Section("Last 7 days")
        val days = (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }
        Panel { Bars(days.map { Store.daylightMin(it.toString()).toFloat() }, days.map { it.dayOfWeek.name.take(1) }, Daylight.TARGET_MIN.toFloat(), 160f, Modifier.fillMaxWidth().height(110.dp), warm = true) }
    }
}

@Composable fun Bars(values: List<Float?>, labels: List<String>, target: Float, max: Float, modifier: Modifier, warm: Boolean = false) = Column {
    Canvas(modifier) {
        val w = size.width / values.size
        val ty = size.height * (1 - target / max)
        values.forEachIndexed { i, v ->
            if (v == null) drawRoundRect(C.raised, Offset(i * w + w * 0.3f, size.height - 6f), Size(w * 0.4f, 6f), CornerRadius(3f))
            else { val h = (size.height * (v / max).coerceIn(0.02f, 1f)); drawRoundRect(if (v >= target) (if (warm) C.warm else C.primary) else C.deep, Offset(i * w + w * 0.22f, size.height - h), Size(w * 0.56f, h), CornerRadius(8f)) }
        }
        drawLine(C.soft.copy(alpha = 0.7f), Offset(0f, ty), Offset(size.width, ty), 2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
    }
    Row(Modifier.fillMaxWidth()) { labels.forEach { T(it, 10, C.muted, modifier = Modifier.weight(1f), align = TextAlign.Center) } }
}

@Composable fun ScheduleScreen() {
    val ctx = LocalContext.current
    Screen("Schedule & alarm", "Your sleep window drives every automation") {
        Panel {
            TimeStepper("Bedtime", Store.bedtimeMin) { setAndReschedule(ctx, "bedtimeMin", it) }
            TimeStepper("Wake-up time", Store.wakeMin) { setAndReschedule(ctx, "wakeMin", it) }
            Stepper("Sleep need", "%.1f h".format(Store.sleepNeedH), { Store.set("sleepNeedH", (Store.sleepNeedH - 0.5).coerceAtLeast(6.0)) }, { Store.set("sleepNeedH", (Store.sleepNeedH + 0.5).coerceAtMost(10.0)) })
            val window = (Store.wakeMin - Store.bedtimeMin + 1440) % 1440
            Note("Time in bed: ${SleepMath.fmtDur(window)}." + if (window < Store.sleepNeedH * 60) " That is shorter than your sleep need — sleep debt will build." else "")
        }
        Section("Wake-up alarm")
        Panel {
            Toggle("Alarm", "Gentle chimes that rise slowly at ${SleepMath.fmtClock(Store.wakeMin)}", Store.alarmEnabled) { setAndReschedule(ctx, "alarmEnabled", it) }
            Stepper("Smart wake window", "${Store.smartWindow} min", { Store.set("smartWindow", (Store.smartWindow - 5).coerceAtLeast(10)) }, { Store.set("smartWindow", (Store.smartWindow + 5).coerceAtMost(45)) })
            Note("During a sleep session the alarm may ring up to ${Store.smartWindow} minutes early, at the first movement or breathing change that signals light sleep.")
        }
        Section("Snore & breathing-pause response")
        Pills(listOf("haptic", "buzzer", "off"), { Store.snoreMode == it }, { when (it) { "haptic" -> "Gentle vibration"; "buzzer" -> "Soft buzzer"; else -> "Monitor only" } }) { Store.set("snoreMode", it) }
        Section("Default sleep timer")
        Pills(listOf(15, 30, 45, 60, 90, 0), { Store.timerMin == it }, { if (it == 0) "Until stopped" else "$it min" }) { Store.set("timerMin", it) }
        Spacer(Modifier.height(14.dp))
        BigButton("Battery: allow background running", secondary = true, icon = Icons.Rounded.BatteryChargingFull) { open(ctx, Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }
        Note("Some phones stop apps overnight. Setting SomnaFlow to “Unrestricted / Don't optimise” keeps tracking and alarms reliable.")
    }
}

@Composable fun HealthScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("") }
    var granted by remember { mutableStateOf(false) }
    val status = remember { Health.status(ctx) }
    LaunchedEffect(Unit) { if (status == 0) granted = Health.granted(ctx) }
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { g -> granted = g.isNotEmpty(); if (granted) scope.launch { msg = Health.sync(ctx) } }
    val hc = Store.obj("hc")
    Screen("Health data", "Passive tracking from your watch and phone") {
        Panel {
            T("Health Connect", 16, weight = FontWeight.SemiBold)
            T("Android's shared health store (the counterpart of Apple HealthKit). Watches and apps such as Fitbit, Samsung Health, Garmin, Oura and Google Fit write to it; SomnaFlow only reads.", 13, C.muted)
            Spacer(Modifier.height(10.dp))
            when {
                status == 2 -> T("Health Connect is not available on this phone. You can still log sleep by hand below.", 13, C.warm)
                status == 1 -> BigButton("Install or update Health Connect", secondary = true) { try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${Health.PROVIDER}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {} }
                !granted -> BigButton("Connect Health Connect", icon = Icons.Rounded.Favorite) { try { launcher.launch(Health.PERMISSIONS) } catch (_: Throwable) { msg = "Could not open Health Connect" } }
                else -> BigButton("Sync now", secondary = true, icon = Icons.Rounded.Sync) { scope.launch { msg = "Syncing…"; msg = Health.sync(ctx) } }
            }
            if (msg.isNotEmpty()) Note(msg)
        }
        if (hc.has("syncedAt")) {
            Section("Latest readings")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Steps today", if (hc.has("steps")) "${hc.optLong("steps")}" else "—", Modifier.weight(1f))
                Stat("Resting heart rate", if (hc.has("restingHr")) "${hc.optLong("restingHr")} bpm" else "—", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Blood oxygen", if (hc.has("spo2")) "${hc.optDouble("spo2").toInt()}%" else "—", Modifier.weight(1f))
                Stat("Respiratory rate", if (hc.has("resp")) "%.0f /min".format(hc.optDouble("resp")) else "—", Modifier.weight(1f))
            }
            hc.optJSONObject("sleep")?.let { s ->
                Section("Last recorded sleep · ${s.optString("date")}")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("Core", SleepMath.fmtDur(s.optInt("core")), Modifier.weight(1f)); Stat("Deep", SleepMath.fmtDur(s.optInt("deep")), Modifier.weight(1f)); Stat("REM", SleepMath.fmtDur(s.optInt("rem")), Modifier.weight(1f))
                }
                Note("Time in bed ${SleepMath.fmtDur(s.optInt("inBed"))} · asleep ${SleepMath.fmtDur(s.optInt("asleep"))}")
            }
        }
        Section("Log last night by hand")
        Panel {
            val d = Store.today(); val h = Store.manualSleep(d) ?: 7.5
            Stepper("Hours slept", "%.1f h".format(h), { Store.setManualSleep(d, (h - 0.5).coerceAtLeast(0.0)) }, { Store.setManualSleep(d, (h + 0.5).coerceAtMost(14.0)) })
            Note(if (Store.manualSleep(d) == null) "Not logged yet. Used for sleep debt when no tracked or synced night exists." else "Saved for today.")
        }
    }
}
