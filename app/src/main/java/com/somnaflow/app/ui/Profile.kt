package com.somnaflow.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.somnaflow.app.data.SleepMath
import com.somnaflow.app.data.Store
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable private fun EnergyChart(curve: FloatArray, nowMin: Int, modifier: Modifier) = Canvas(modifier) {
    val p = Path(); val fill = Path()
    curve.forEachIndexed { i, v -> val x = size.width * i / (curve.size - 1); val y = size.height * (1 - v / 100f)
        if (i == 0) { p.moveTo(x, y); fill.moveTo(x, size.height); fill.lineTo(x, y) } else { p.lineTo(x, y); fill.lineTo(x, y) } }
    fill.lineTo(size.width, size.height); fill.close()
    drawPath(fill, Brush.verticalGradient(listOf(C.primary.copy(alpha = 0.35f), Color.Transparent)))
    drawPath(p, C.soft, style = Stroke(4f))
    val nx = size.width * nowMin / 1440f; val ny = size.height * (1 - curve[(nowMin / 15).coerceIn(0, 95)] / 100f)
    drawLine(C.warm.copy(alpha = 0.5f), Offset(nx, 0f), Offset(nx, size.height), 2f)
    drawCircle(C.warm, 8f, Offset(nx, ny))
}

@Composable private fun Link(icon: ImageVector, title: String, subtitle: String, route: String) =
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { Nav.go(route) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = C.soft)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { T(title, 15); T(subtitle, 12, C.muted) }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = C.muted)
    }

@Composable fun ProfileScreen() {
    val nights = Store.nights(); val last14 = Store.last14(); val debt = Store.debtHours(); val need = Store.sleepNeedH
    val nowMin = LocalTime.now().toSecondOfDay() / 60
    val lastSleep = last14.lastOrNull { it != null } ?: need
    val curve = SleepMath.energyCurve(Store.wakeMin, Store.bedtimeMin, lastSleep, need, debt)
    val logged = last14.filterNotNull()
    Screen("Profile", "Streaks, sleep debt and your body's signals", back = false) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Day streak", "${Store.streak()}", Modifier.weight(1f), C.warm)
            Stat("Sleep debt (14 days)", "%.1f h".format(debt), Modifier.weight(1f), if (debt > 5) C.warm else C.soft)
            Stat("Avg sleep", if (logged.isEmpty()) "—" else "%.1f h".format(logged.average()), Modifier.weight(1f))
        }
        Section("Sleep · last 14 nights")
        Panel {
            val days = (13 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }
            Bars(last14.map { it?.toFloat() }, days.map { it.dayOfMonth.toString() }, need.toFloat(), 11f, Modifier.fillMaxWidth().height(120.dp))
            Note(if (logged.isEmpty()) "No nights yet. Run a sleep session, connect Health Connect, or log hours by hand. The dashed line is your ${"%.1f".format(need)} h sleep need."
                 else if (debt < 1) "You are meeting your sleep need — no meaningful debt." else "You owe about ${"%.1f".format(debt)} hours. An extra 30–60 minutes a night clears debt more gently than one long lie-in.")
        }
        Section("Today's predicted energy")
        Panel {
            EnergyChart(curve, nowMin, Modifier.fillMaxWidth().height(120.dp))
            Row(Modifier.fillMaxWidth()) { listOf("12 AM", "6 AM", "12 PM", "6 PM", "").forEach { T(it, 10, C.muted, modifier = Modifier.weight(1f)) } }
            val wakeIdx = Store.wakeMin / 15; val bedIdx = Store.bedtimeMin / 15
            val awake = (0 until 96).filter { val s = (it - wakeIdx + 96) % 96; s > 8 && s < (bedIdx - wakeIdx + 96) % 96 - 8 }
            val peak = awake.maxByOrNull { curve[it] }; val dip = awake.filter { it in 48..72 }.minByOrNull { curve[it] }
            Note("Now: ${curve[(nowMin / 15).coerceIn(0, 95)].toInt()}/100." + (peak?.let { " Peak focus near ${SleepMath.fmtClock(it * 15)}." } ?: "") + (dip?.let { " Expect a sluggish dip around ${SleepMath.fmtClock(it * 15)}." } ?: "")
                + if (debt > 3) " Sleep debt is lowering the whole curve." else "")
        }
        if (nights.isNotEmpty()) {
            Section("Recent nights")
            val fmt = DateTimeFormatter.ofPattern("EEE d MMM")
            Panel { nights.indices.reversed().take(7).forEach { i -> val n = nights[i]
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { Nav.go("night/$i") }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { T(Instant.ofEpochMilli(n.end).atZone(ZoneId.systemDefault()).format(fmt), 14); T("Asleep in ${n.solMin} min · ${n.awakenings} awakenings · snoring ${n.snoreMin} min", 12, C.muted) }
                    T(SleepMath.fmtDur(n.asleepMin), 15, C.soft, FontWeight.SemiBold)
                } } }
        }
        Section("Biometrics")
        val hrv = Store.list("hrv").lastOrNull(); val hc = Store.obj("hc")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("HRV (RMSSD)", hrv?.let { "${it.optDouble("rmssd").toInt()} ms" } ?: "—", Modifier.weight(1f))
            Stat("Resting HR", if (hc.has("restingHr")) "${hc.optLong("restingHr")} bpm" else hrv?.let { "${it.optDouble("hr").toInt()} bpm" } ?: "—", Modifier.weight(1f))
            Stat("Steps today", if (hc.has("steps")) "${hc.optLong("steps")}" else "—", Modifier.weight(1f))
        }
        Section("More")
        Panel {
            Link(Icons.Rounded.Assignment, "Assessments", "PSQI, PHQ-9 and GAD-7 baselines and progress", "assessments")
            Link(Icons.Rounded.Sensors, "Biofeedback & devices", "EEG headband, heart-rate strap, camera pulse", "bio")
            Link(Icons.Rounded.Favorite, "Health data", "Health Connect sync and manual sleep log", "health")
            Link(Icons.Rounded.Schedule, "Schedule & alarm", "Bedtime, wake time, smart alarm, snore response", "schedule")
            Link(Icons.Rounded.SelfImprovement, "Wind-down automation", "Do Not Disturb, dimming, audio", "winddown")
            Link(Icons.Rounded.Info, "About & the science", "How each feature works, and its limits", "about")
        }
    }
}

@Composable fun AboutScreen() = Screen("About SomnaFlow", "Version 1.0 · everything stays on your phone") {
    listOf(
        "Theta induction" to "Left ear 250 Hz, right ear 256 Hz: the brain perceives the 6 Hz difference, in the theta band of the wake-to-sleep transition. The beat is held 30 dB beneath a nature sound bed (the Combined Stimuli ratio) because beats on their own are fatiguing. Use headphones.",
        "Spindle accelerator" to "A binaural beat that glides between 0.5 and 4 Hz over steady pink noise, aimed at the slow rhythms that accompany stable stage-2 sleep.",
        "Stories, not daytime meditation, at night" to "Sleep Stories and lying-down sleep meditations interrupt rumination without demanding focus. Upright, alert meditation is kept for the daytime because it can raise pre-sleep arousal.",
        "Closed loop" to "With an EEG headband, sustained high-beta activity automatically starts theta entrainment, which fades once alpha and theta dominate.",
        "Snore response" to "The microphone signal is reduced to loudness and low-frequency ratio on the spot; no audio is stored or sent anywhere. A gentle vibration is given only above 90% confidence.",
        "Sleep debt and energy" to "Debt is the running shortfall against your sleep need over 14 days. The energy curve uses the two-process model: sleep pressure plus your circadian rhythm.",
        "Privacy" to "SomnaFlow has no account, no ads, no server and no internet permission. All data is stored only on this phone."
    ).forEach { (h, b) -> Panel(Modifier.padding(bottom = 10.dp)) { T(h, 15, weight = FontWeight.SemiBold); T(b, 13, C.muted) } }
    Note("SomnaFlow is a wellness tool, not a medical device. It does not diagnose or treat insomnia, sleep apnea, depression or anxiety. Do not use binaural beats if you have epilepsy or a seizure disorder without medical advice. If poor sleep persists, please see a doctor.")
}
