package com.somnaflow.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.somnaflow.app.data.SleepMath
import com.somnaflow.app.data.Store
import com.somnaflow.app.service.AlarmRinger
import com.somnaflow.app.service.Alarms
import com.somnaflow.app.service.Player
import com.somnaflow.app.service.WindDown

@Composable fun SomnaRoot() = SomnaTheme {
    val ctx = LocalContext.current
    Store.rev // recompose the tree on any data change
    Box(Modifier.fillMaxSize().background(C.bg)) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val route = Nav.stack.lastOrNull()
                if (route == null) when (Nav.tab) { 0 -> HomeScreen(); 1 -> LibraryScreen(); else -> ProfileScreen() }
                else Route(route)
            }
            if (Nav.stack.lastOrNull() != "player" && (Player.playing || Player.track != null)) MiniPlayer()
            BottomBar()
        }
        // In-app dimming during wind-down: a warm dark veil that lowers contrast without hiding anything.
        if (WindDown.active && Store.wdDim) Box(Modifier.fillMaxSize().background(Color(0x66080400)))
        if (AlarmRinger.ringing) {
            BackHandler {}
            Column(Modifier.fillMaxSize().background(C.bg.copy(alpha = 0.97f)).systemBarsPadding().padding(28.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.WbTwilight, null, tint = C.warm, modifier = Modifier.size(72.dp))
                Spacer(Modifier.height(16.dp))
                T("Good morning", 32, weight = FontWeight.SemiBold)
                T(SleepMath.fmtClock(java.time.LocalTime.now().toSecondOfDay() / 60), 20, C.muted)
                Spacer(Modifier.height(40.dp))
                BigButton("I'm awake · stop alarm", icon = Icons.Rounded.Check) { Alarms.dismiss(ctx); Nav.stack.clear(); Nav.go("tonight") }
                Spacer(Modifier.height(12.dp))
                BigButton("Snooze 9 minutes", secondary = true, icon = Icons.Rounded.Snooze) { Alarms.snooze(ctx) }
            }
        }
    }
}

@Composable private fun Route(route: String) {
    val arg = route.substringAfter('/', "")
    when (route.substringBefore('/')) {
        "player" -> PlayerScreen(); "mixer" -> MixerScreen(); "checkin" -> CheckInScreen()
        "assessments" -> AssessmentsScreen(); "assess" -> AssessScreen(arg)
        "tonight" -> TonightScreen(); "night" -> NightScreen(arg.toIntOrNull() ?: -1)
        "bio" -> BiofeedbackScreen(); "winddown" -> WindDownScreen(); "stretch" -> StretchScreen()
        "daylight" -> DaylightScreen(); "schedule" -> ScheduleScreen(); "health" -> HealthScreen(); "about" -> AboutScreen()
        else -> { Nav.back() }
    }
}

@Composable private fun BottomBar() {
    val items = listOf(Triple("Home", Icons.Rounded.Bedtime, 0), Triple("Library", Icons.Rounded.LibraryMusic, 1), Triple("Profile", Icons.Rounded.Insights, 2))
    Row(Modifier.fillMaxWidth().background(C.surface).padding(horizontal = 8.dp, vertical = 6.dp)) {
        items.forEach { (label, icon, i) ->
            val on = Nav.tab == i && Nav.stack.isEmpty()
            Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { Nav.home(i) }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.clip(RoundedCornerShape(50)).background(if (on) C.primary.copy(alpha = 0.25f) else Color.Transparent).padding(horizontal = 18.dp, vertical = 4.dp)) {
                    Icon(icon, null, tint = if (on) C.soft else C.muted)
                }
                T(label, 12, if (on) C.text else C.muted, if (on) FontWeight.SemiBold else FontWeight.Normal, align = TextAlign.Center)
            }
        }
    }
}

/** Ambient persistent player: stays on screen, with live waveform and artwork, across every page. */
@Composable private fun MiniPlayer() {
    val ctx = LocalContext.current
    val t = Player.track
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).clip(RoundedCornerShape(18.dp)).background(C.raised)
        .clickable { Nav.go("player") }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(t?.hue ?: 270f, Modifier.size(44.dp), 12.dp)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            T(t?.title ?: "My mix", 14, weight = FontWeight.SemiBold, lines = 1)
            Waveform(Player.playing, Modifier.fillMaxWidth().height(16.dp), bars = 32)
        }
        Column(Modifier.clip(CircleShape).clickable { Player.toggle(ctx) }.padding(horizontal = 10.dp, vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(if (Player.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = C.soft, modifier = Modifier.size(28.dp))
            T(if (Player.playing) "Pause" else "Play", 10, C.muted)
        }
    }
}
