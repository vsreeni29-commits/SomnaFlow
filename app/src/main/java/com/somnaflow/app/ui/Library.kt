package com.somnaflow.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.somnaflow.app.audio.Beat
import com.somnaflow.app.audio.Fader
import com.somnaflow.app.audio.LayerFactory
import com.somnaflow.app.audio.MixConfig
import com.somnaflow.app.audio.SoundEngine
import com.somnaflow.app.data.Content
import com.somnaflow.app.data.Kind
import com.somnaflow.app.data.SleepMath
import com.somnaflow.app.data.Store
import com.somnaflow.app.data.Track
import com.somnaflow.app.service.Player
import kotlinx.coroutines.delay
import java.time.LocalTime

@Composable fun LibraryScreen() {
    val ctx = LocalContext.current
    var filter by remember { mutableStateOf<Kind?>(null) }
    val dayOk = Content.daytimeAllowed(LocalTime.now().toSecondOfDay() / 60, Store.wakeMin, Store.bedtimeMin)
    Screen("Library", "Everything is generated on your phone and works offline", back = false) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("All", filter == null) { filter = null }
            Kind.entries.forEach { k -> Pill(k.label, filter == k) { filter = k } }
        }
        Spacer(Modifier.height(12.dp))
        Panel(onClick = { Nav.go("mixer") }, color = C.raised) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Tune, null, tint = C.soft)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    T("Sound mixing console", 15, weight = FontWeight.SemiBold); T("10 faders · layers · binaural beats", 12, C.muted)
                }
                T("Open", 13, C.soft)
            }
        }
        // Stepped care: for disturbed sleepers, stories and sleep meditations are listed first.
        val order = if (Store.severity() >= 2) listOf(Kind.STORY, Kind.SLEEP_MED, Kind.ENTRAIN, Kind.SOUNDSCAPE, Kind.DAY_MED) else Kind.entries.toList()
        order.filter { filter == null || filter == it }.forEach { kind ->
            Section(kind.label)
            if (kind == Kind.DAY_MED) Panel(color = C.surface) {
                T(if (dayOk) "Available now. Upright, alert practice builds attention during the day."
                  else "Resting until morning. Alert, upright meditation raises pre-sleep attention and can delay sleep onset, so it is offered only in the daytime. Tonight, choose a Sleep Story or Sleep Meditation.", 12, C.muted)
            }
            Content.TRACKS.filter { it.kind == kind }.forEach { t ->
                val locked = kind == Kind.DAY_MED && !dayOk
                TrackRow(t, locked) { if (!locked) { Player.play(ctx, t); Nav.go("player") } }
            }
        }
    }
}

@Composable private fun TrackRow(t: Track, locked: Boolean, onClick: () -> Unit) =
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(t.hue, Modifier.size(58.dp), 14.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            T(t.title, 15, if (locked) C.muted else C.text, FontWeight.SemiBold, lines = 1)
            T(t.subtitle, 12, C.muted, lines = 2)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(if (locked) Icons.Rounded.Lock else if (Player.track?.id == t.id && Player.playing) Icons.Rounded.GraphicEq else Icons.Rounded.PlayCircle, null,
                tint = if (locked) C.muted else C.soft, modifier = Modifier.size(30.dp))
            T(if (locked) "Daytime" else if (t.minutes > 0) "${t.minutes} min" else "Play", 10, C.muted)
        }
    }

@Composable fun PlayerScreen() {
    val ctx = LocalContext.current
    val t = Player.track
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var ratio by remember { mutableFloatStateOf(Float.NaN) }; var beat by remember { mutableFloatStateOf(0f) }
    var nature by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); ratio = SoundEngine.mixer.measuredRatioDb; beat = SoundEngine.mixer.beatHz; nature = SoundEngine.mixer.activeNature; delay(500) } }
    Screen(t?.title ?: "My mix", t?.subtitle ?: "Custom console mix") {
        Box(Modifier.fillMaxWidth().height(230.dp)) {
            Artwork(t?.hue ?: 270f, Modifier.fillMaxSize(), 26.dp)
            Waveform(Player.playing, Modifier.fillMaxWidth().height(70.dp).align(Alignment.BottomCenter).padding(horizontal = 18.dp, vertical = 12.dp), bars = 36)
        }
        if (Player.caption.isNotEmpty() || Player.narrating) {
            Spacer(Modifier.height(12.dp))
            Panel { T(if (Player.caption.isEmpty()) "…" else Player.caption, 16, C.soft, align = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        }
        if (Player.ttsMissing && t != null && t.script.isNotEmpty()) Note("No text-to-speech voice is installed on this phone, so narration is silent. Install “Speech Services by Google” (free) to hear stories.")
        Spacer(Modifier.height(16.dp))
        BigButton(if (Player.playing) "Pause" else "Play", icon = if (Player.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow) { Player.toggle(ctx) }

        if (Player.mix.beat != Beat.OFF && Player.playing) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Binaural beat · L 250 Hz / R ${"%.1f".format(250 + beat)} Hz", "${"%.1f".format(beat)} Hz", Modifier.weight(1f))
                Stat(if (Player.mix.csLock) "Beat level under the sound bed (30:60 protocol)" else "Beat level vs bed (manual)", if (ratio.isNaN()) "—" else "${"%.0f".format(ratio)} dB", Modifier.weight(1f))
            }
            Note("Binaural beats need headphones or sleep earbuds: each ear must hear its own tone.")
        }
        if (Player.mix.randomize && nature.isNotEmpty()) Note("Now drifting through: ${nature.joinToString { LayerFactory.label(it) }} · changes every few minutes")

        Section("Sleep timer")
        val left = if (Player.timerEnd > 0) ((Player.timerEnd - now) / 60000).toInt() + 1 else 0
        Pills(listOf(15, 30, 45, 60, 90, 0), { m -> if (m == 0) Player.timerEnd == 0L else false }, { if (it == 0) "Until stopped" else "$it min" }) { Player.setTimer(it); Store.set("timerMin", it) }
        Note(if (!Player.playing) "Timer starts with playback." else if (left > 0) "Fades out gently in ${SleepMath.fmtDur(left)}." else "Playing until you stop it.")
        Spacer(Modifier.height(14.dp))
        BigButton("Open mixing console", secondary = true, icon = Icons.Rounded.Tune) { Nav.go("mixer") }
    }
}

@Composable private fun FaderColumn(label: String, value: Float, enabled: Boolean, modifier: Modifier, eq: Boolean, onChange: (Float) -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        T(if (eq) "%+d".format(((value - 0.5f) * 24).toInt()) else "${(value * 100).toInt()}", 10, C.muted)
        var h by remember { mutableFloatStateOf(1f) }
        Box(Modifier.padding(vertical = 4.dp).width(30.dp).height(190.dp)
            .pointerInput(enabled) { if (enabled) detectDragGestures { ch, _ -> ch.consume(); onChange(1f - ch.position.y / size.height) } }
            .pointerInput(enabled) { if (enabled) detectTapGestures { onChange(1f - it.y / size.height) } }, Alignment.BottomCenter) {
            Box(Modifier.width(8.dp).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(C.raised))
            Box(Modifier.width(8.dp).fillMaxHeight(value.coerceIn(0.001f, 1f)).clip(RoundedCornerShape(4.dp)).background(if (enabled) C.primary else C.deep))
            Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                Box(Modifier.size(width = 26.dp, height = 14.dp).clip(RoundedCornerShape(5.dp)).background(if (enabled) C.soft else C.muted))
                Spacer(Modifier.fillMaxHeight((value * 0.93f).coerceIn(0.0001f, 1f)))
            }
        }
        T(label, 10, if (enabled) C.text else C.muted, lines = 1, align = TextAlign.Center)
    }
}

@Composable fun MixerScreen() {
    val ctx = LocalContext.current
    val mix = Player.mix
    var ratio by remember { mutableFloatStateOf(Float.NaN) }
    LaunchedEffect(Unit) { while (true) { ratio = SoundEngine.mixer.measuredRatioDb; delay(500) } }
    val beatLocked = mix.csLock && mix.beat != Beat.OFF
    Screen("Mixing console", "Shape your own soundscape") {
        Panel {
            Row(Modifier.fillMaxWidth()) {
                for (i in 0 until 10) FaderColumn(Fader.LABELS[i], mix.faders[i], !(i == Fader.BINAURAL && beatLocked) && !(i == Fader.BINAURAL && mix.beat == Beat.OFF), Modifier.weight(1f), i >= Fader.BASS) {
                    Player.updateMix(mix.withFader(i, it))
                }
            }
            Note("Master · beat level · nature · melody · white, pink and brown noise · bass, mid and treble tone (±12 dB).")
        }
        Spacer(Modifier.height(12.dp))
        BigButton(if (Player.playing) "Pause" else "Play this mix", icon = if (Player.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow) { Player.toggle(ctx) }

        Section("Binaural beats")
        Pills(listOf(Beat.OFF, Beat.THETA, Beat.SWEEP), { mix.beat == it }, { when (it) { Beat.THETA -> "Theta 6 Hz"; Beat.SWEEP -> "Delta sweep 0.5–4 Hz"; else -> "Off" } }) { Player.updateMix(mix.copy(beat = it)) }
        Toggle("Combined Stimuli 30:60 lock", "Holds the beat 30 dB below the sound bed to avoid listening fatigue" + if (!ratio.isNaN() && mix.beat != Beat.OFF) " · now ${"%.0f".format(ratio)} dB" else "", mix.csLock) { Player.updateMix(mix.copy(csLock = it)) }

        Section("Nature layers")
        Toggle("Randomized nature triggers", "Drift between rain, ocean, waterfall, forest and river", mix.randomize) { Player.updateMix(mix.copy(randomize = it)) }
        if (!mix.randomize) Pills(LayerFactory.NATURE, { it in mix.nature }, { LayerFactory.label(it) }) { id -> Player.updateMix(mix.copy(nature = if (id in mix.nature) mix.nature - id else mix.nature + id)) }

        Section("Melodic base")
        Pills(listOf("") + LayerFactory.MELODY, { mix.melody == it }, { if (it.isEmpty()) "None" else LayerFactory.label(it) }) { Player.updateMix(mix.copy(melody = it)) }
        Spacer(Modifier.height(16.dp))
        BigButton("Reset console", secondary = true) { Player.updateMix(MixConfig(nature = setOf("rain"))) }
    }
}
