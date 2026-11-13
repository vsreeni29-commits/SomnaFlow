package com.somnaflow.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.somnaflow.app.data.Content
import com.somnaflow.app.data.Night
import com.somnaflow.app.data.SleepMath
import com.somnaflow.app.data.Store
import com.somnaflow.app.service.Player
import com.somnaflow.app.service.SleepTrackingService
import com.somnaflow.app.service.Tracker
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun TonightScreen() {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { SleepTrackingService.start(ctx) }
    Screen("Sleep session", if (Tracker.running) "Tracking — sleep well" else "Track tonight and wake in light sleep") {
        if (!Tracker.running) {
            Tracker.lastNight?.let { n ->
                Panel(onClick = { Nav.go("night/${Store.nights().size - 1}") }, color = C.raised) {
                    T("Last session saved", 15, weight = FontWeight.SemiBold)
                    T("Asleep ${SleepMath.fmtDur(n.asleepMin)} · fell asleep in ${n.solMin} min · tap for the full report", 13, C.muted)
                }
                Spacer(Modifier.height(12.dp))
            }
            Panel {
                T("Before you start", 16, weight = FontWeight.SemiBold)
                listOf("Plug the phone in and place it face-down on the mattress beside your pillow.",
                    "Movement is sensed through the mattress; breathing and snoring through the microphone. Sound is analysed live and never recorded.",
                    "If snoring is detected with over 90% confidence, a gentle ${if (Store.snoreMode == "buzzer") "low buzzer" else "vibration"} nudges you to shift position without waking you.",
                    if (Store.alarmEnabled) "Smart alarm: between ${SleepMath.fmtClock(Store.wakeMin - Store.smartWindow)} and ${SleepMath.fmtClock(Store.wakeMin)}, you are woken at the first sign of light sleep."
                    else "The alarm is off — the session runs until you stop it.").forEach { T("•  $it", 13, C.muted, modifier = Modifier.padding(top = 6.dp)) }
            }
            Spacer(Modifier.height(14.dp))
            BigButton("Start sleep session", icon = Icons.Rounded.NightsStay) {
                val perms = buildList { add(Manifest.permission.RECORD_AUDIO); if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS) }
                launcher.launch(perms.toTypedArray())
            }
            Spacer(Modifier.height(10.dp))
            if (!Player.playing) BigButton("Play sleep audio first", secondary = true, icon = Icons.Rounded.PlayArrow) { Player.play(ctx, Content.recommended(Store.severity()).first()) }
            Spacer(Modifier.height(10.dp))
            BigButton("Alarm and tracking settings", secondary = true, icon = Icons.Rounded.Settings) { Nav.go("schedule") }
        } else {
            val el = ((now - Tracker.startedAt) / 60000).toInt()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Time in bed", SleepMath.fmtDur(el), Modifier.weight(1f))
                Stat(if (Tracker.alarmAt > 0) "Wake window ends" else "Alarm", if (Tracker.alarmAt > 0) SleepMath.fmtClock(Store.wakeMin) else "Off", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Movements this minute", "${Tracker.movesThisMinute}", Modifier.weight(1f))
                Stat("Breaths per minute", if (Tracker.breaths > 0) "%.0f".format(Tracker.breaths) else "—", Modifier.weight(1f))
            }
            Section("Snore monitor")
            Panel {
                if (!Tracker.micOn) T("Microphone not available — movement tracking and the alarm still work.", 13, C.warm)
                else if (Player.playing) T("Listening starts when your sleep audio fades out, so the app does not mistake its own sounds for breathing.", 13, C.muted)
                else {
                    T("Snore confidence ${(Tracker.confidence * 100).toInt()}%  ·  intervention above 90%", 13)
                    Spacer(Modifier.height(8.dp)); Bar(Tracker.confidence, mark = 0.9f, color = if (Tracker.confidence > 0.9f) C.warm else C.primary)
                    Spacer(Modifier.height(10.dp)); T("Room level", 12, C.muted); Bar(((Tracker.levelDb + 70f) / 60f))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("Snoring", "${Tracker.snoreMin} min", Modifier.weight(1f)); Stat("Breathing pauses", "${Tracker.pauses}", Modifier.weight(1f)); Stat("Gentle nudges", "${Tracker.interventions}", Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(16.dp))
            BigButton("I'm awake · end session", icon = Icons.Rounded.WbTwilight) { SleepTrackingService.instance?.finish(); if (Tracker.lastNight != null) Nav.go("night/${Store.nights().size - 1}") }
            Note("You can lock the screen now. Tracking continues from the notification.")
        }
    }
}

@Composable fun Hypnogram(epochs: String, modifier: Modifier) = Canvas(modifier) {
    if (epochs.isEmpty()) return@Canvas
    val w = size.width / epochs.length; val rowH = size.height / 3
    epochs.forEachIndexed { i, c ->
        val (row, col) = when (c) { 'W' -> 0 to C.warm; 'L' -> 1 to C.primary; else -> 2 to C.deep }
        drawRect(col, Offset(i * w, row * rowH + 3f), Size(w + 0.6f, rowH - 6f))
    }
}

@Composable fun NightScreen(index: Int) {
    val n: Night? = Store.nights().getOrNull(index)
    val fmt = DateTimeFormatter.ofPattern("EEE d MMM · h:mm a")
    fun at(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(fmt)
    Screen("Night report", n?.let { at(it.start) } ?: "") {
        if (n == null) { T("No report found.", 14, C.muted); return@Screen }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Fell asleep in", "${n.solMin} min", Modifier.weight(1f), C.soft)
            Stat("Total sleep", SleepMath.fmtDur(n.asleepMin), Modifier.weight(1f))
            Stat("Efficiency", "${n.efficiency}%", Modifier.weight(1f))
        }
        Section("Sleep stages (estimated from movement)")
        Panel {
            Row { Column(Modifier.width(52.dp).height(120.dp), verticalArrangement = Arrangement.SpaceAround) { T("Awake", 11, C.warm); T("Light", 11, C.primary); T("Deep", 11, C.soft) }
                Hypnogram(n.epochs, Modifier.weight(1f).height(120.dp)) }
            Row(Modifier.fillMaxWidth().padding(start = 52.dp)) { T(at(n.start).substringAfter("· "), 11, C.muted, modifier = Modifier.weight(1f)); T(at(n.end).substringAfter("· "), 11, C.muted) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Deep sleep", SleepMath.fmtDur(n.deepMin), Modifier.weight(1f)); Stat("Awakenings", "${n.awakenings}", Modifier.weight(1f))
            Stat("Woken by", when (n.wokeBy) { "smart" -> "Smart"; "alarm" -> "Alarm"; else -> "You" }, Modifier.weight(1f))
        }
        Section("Breathing")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Snoring", "${n.snoreMin} min", Modifier.weight(1f)); Stat("Pauses >10 s", "${n.pauses}", Modifier.weight(1f)); Stat("Gentle nudges", "${n.interventions}", Modifier.weight(1f))
        }
        if (n.breathRate > 0) Note("Average breathing cadence: ${"%.0f".format(n.breathRate)} breaths per minute.")
        Note("Stages are estimated from mattress movement (actigraphy), which cannot separate REM from light sleep. Frequent breathing pauses or loud nightly snoring deserve a conversation with a doctor — this app is not a medical device and cannot diagnose sleep apnea.")
    }
}
