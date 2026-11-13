package com.somnaflow.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.somnaflow.app.data.Content
import com.somnaflow.app.data.SleepMath
import com.somnaflow.app.data.Store
import com.somnaflow.app.service.Daylight
import com.somnaflow.app.service.Player
import com.somnaflow.app.service.Tracker
import com.somnaflow.app.service.WindDown
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

fun checkedInToday(): Boolean = Store.list("moods").lastOrNull()?.let {
    Instant.ofEpochMilli(it.optLong("t")).atZone(ZoneId.systemDefault()).toLocalDate().toString() == Store.today() } ?: false

/** Tasks that complete themselves from tracked data, merged with the ones the user ticks. */
fun tasksDoneToday(): Set<String> {
    val s = Store.tasksDone().toMutableSet()
    if (Store.daylightMin() >= Daylight.TARGET_MIN) s.add("daylight")
    if (checkedInToday()) s.add("checkin")
    return s
}

fun readinessToday(): Int = SleepMath.readiness(tasksDoneToday().size, Content.TASKS.size, Store.daylightMin(), checkedInToday(), Store.debtHours())

@Composable fun HomeScreen() {
    val ctx = LocalContext.current
    val hour = LocalTime.now().hour
    val greeting = if (hour < 5) "Rest well" else if (hour < 12) "Good morning" else if (hour < 18) "Good afternoon" else "Good evening"
    val severity = Store.severity()
    Screen(greeting, "Tonight's plan and your sleep worklist", back = false) {
        Panel(color = C.raised) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T("Sleep Readiness", 13, C.muted)
                    Spacer(Modifier.height(6.dp))
                    PlanRow(Icons.Rounded.SelfImprovement, "Wind-down", SleepMath.fmtClock(Store.bedtimeMin - Store.windDownLead))
                    PlanRow(Icons.Rounded.Bedtime, "Bedtime", SleepMath.fmtClock(Store.bedtimeMin))
                    PlanRow(Icons.Rounded.Alarm, if (Store.alarmEnabled) "Smart wake" else "Wake", SleepMath.fmtClock(Store.wakeMin))
                }
                Gauge(readinessToday(), "ready", Modifier.size(118.dp))
            }
            Spacer(Modifier.height(14.dp))
            BigButton(if (Tracker.running) "Sleep session running · open" else "Start tonight's sleep session", icon = Icons.Rounded.NightsStay) { Nav.go("tonight") }
            if (WindDown.active) { Spacer(Modifier.height(8.dp)); BigButton("Wind-down in progress · open routine", secondary = true) { Nav.go("winddown") } }
        }

        if (severity == 0) {
            Spacer(Modifier.height(12.dp))
            Panel(onClick = { Nav.go("assess/psqi") }) {
                T("Set your sleep baseline", 16, weight = FontWeight.SemiBold)
                T("A 3-minute questionnaire tailors your stories, meditations and sounds to how you actually sleep.", 13, C.muted)
                Spacer(Modifier.height(6.dp)); T("Start the Sleep Quality Index  →", 13, C.soft)
            }
        }

        Section(when (severity) { 3 -> "Picked for restless nights"; 2 -> "Picked for your sleep profile"; else -> "Recommended tonight" }, "Library") { Nav.home(1) }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Content.recommended(severity).forEach { t ->
                Column(Modifier.width(150.dp).clickable { Player.play(ctx, t); Nav.go("player") }.padding(bottom = 4.dp)) {
                    Artwork(t.hue, Modifier.fillMaxWidth().height(104.dp), 18.dp)
                    T(t.title, 14, weight = FontWeight.SemiBold, lines = 2, modifier = Modifier.padding(top = 6.dp))
                    T(t.kind.label, 12, C.muted)
                }
            }
        }

        Section("Tonight's routine")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Quick(Icons.Rounded.Mood, "Check-in", Modifier.weight(1f)) { Nav.go("checkin") }
            Quick(Icons.Rounded.SelfImprovement, "Wind-down", Modifier.weight(1f)) { Nav.go("winddown") }
            Quick(Icons.Rounded.WbSunny, "Daylight", Modifier.weight(1f)) { Nav.go("daylight") }
            Quick(Icons.Rounded.Sensors, "Biofeedback", Modifier.weight(1f)) { Nav.go("bio") }
        }

        val done = tasksDoneToday()
        Section("Sleep worklist · ${done.size} of ${Content.TASKS.size}")
        Panel {
            Content.TASKS.forEach { (id, title, group) ->
                val isDone = id in done
                val auto = id == "daylight" || id == "checkin"
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                    when (id) { "daylight" -> Nav.go("daylight"); "checkin" -> Nav.go("checkin"); "stretch" -> if (isDone) Store.setTask(id, false) else Nav.go("stretch")
                        else -> Store.setTask(id, !isDone) }
                }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (isDone) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (isDone) C.good else C.muted)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        T(title, 14, if (isDone) C.muted else C.text)
                        T(if (id == "daylight") "$group · ${Store.daylightMin()} of ${Daylight.TARGET_MIN} min today" else if (auto) "$group · completes automatically" else group, 11, C.muted)
                    }
                }
            }
        }
    }
}

@Composable private fun PlanRow(icon: ImageVector, label: String, value: String) =
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = C.soft, modifier = Modifier.size(18.dp))
        T(label, 13, C.muted, modifier = Modifier.padding(start = 8.dp).width(84.dp)); T(value, 14, weight = FontWeight.SemiBold)
    }

@Composable private fun Quick(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) =
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(C.surface).clickable(onClick = onClick).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(C.raised), Alignment.Center) { Icon(icon, null, tint = C.soft) }
        T(label, 11, C.text, modifier = Modifier.padding(top = 6.dp), lines = 1)
    }
