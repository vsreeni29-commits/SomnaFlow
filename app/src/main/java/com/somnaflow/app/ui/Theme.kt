package com.somnaflow.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.somnaflow.app.audio.SoundEngine
import com.somnaflow.app.data.SleepMath
import kotlin.math.sin

/**
 * Circadian-safe palette: near-black violet surfaces, soft purple accents and warm (low-blue) text.
 * Nothing in the app is pure white or saturated blue.
 */
object C {
    val bg = Color(0xFF0B0817); val surface = Color(0xFF16112C); val raised = Color(0xFF211A40); val line = Color(0xFF2E2556)
    val primary = Color(0xFF9B82F3); val soft = Color(0xFFC9B8FF); val deep = Color(0xFF5B45B0)
    val text = Color(0xFFEDE6F7); val muted = Color(0xFFA198BD); val warm = Color(0xFFE8B98A)
    val good = Color(0xFF8FD3B0); val alert = Color(0xFFE79A9A)
}

private val scheme = darkColorScheme(
    primary = C.primary, onPrimary = Color(0xFF160F30), secondary = C.soft, background = C.bg, onBackground = C.text,
    surface = C.surface, onSurface = C.text, surfaceVariant = C.raised, onSurfaceVariant = C.muted, outline = C.line, error = C.alert
)

@Composable fun SomnaTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, content = content)

/** Minimal in-memory navigation: three tabs plus a stack of detail screens. */
object Nav {
    var tab by mutableIntStateOf(0)
    val stack: SnapshotStateList<String> = mutableStateListOf()
    fun go(route: String) { if (stack.lastOrNull() != route) stack.add(route) }
    fun back() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    fun home(t: Int) { stack.clear(); tab = t }
}

@Composable fun T(text: String, size: Int = 15, color: Color = C.text, weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier,
                  align: TextAlign? = null, lines: Int = Int.MAX_VALUE) =
    Text(text, modifier, color = color, style = TextStyle(fontSize = size.sp, fontWeight = weight, lineHeight = (size * 1.4f).sp),
        textAlign = align, maxLines = lines, overflow = TextOverflow.Ellipsis)

@Composable fun Screen(title: String, subtitle: String = "", back: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    if (back) BackHandler { Nav.back() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (back) {
                Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable { Nav.back() }.padding(end = 12.dp, top = 4.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = C.soft)
                    T("Back", 11, C.muted)
                }
            }
            Column { T(title, 24, weight = FontWeight.SemiBold); if (subtitle.isNotEmpty()) T(subtitle, 13, C.muted) }
        }
        Spacer(Modifier.height(14.dp))
        content()
        Spacer(Modifier.height(28.dp))
    }
}

@Composable fun Panel(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, color: Color = C.surface, content: @Composable ColumnScope.() -> Unit) {
    val m = modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(color).border(1.dp, C.line.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
    Column((if (onClick != null) m.clickable(onClick = onClick) else m).padding(16.dp), content = content)
}

@Composable fun Section(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        T(title, 17, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) T(action, 13, C.soft, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(6.dp))
    }
}

@Composable fun BigButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, secondary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = if (!enabled) C.raised else if (secondary) C.raised else C.primary
    val fg = if (!enabled) C.muted else if (secondary) C.soft else Color(0xFF160F30)
    Row(modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).background(bg).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        T(text, 15, fg, FontWeight.SemiBold, align = TextAlign.Center)
    }
}

@Composable fun Pill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) =
    Box(modifier.clip(RoundedCornerShape(50)).background(if (selected) C.primary.copy(alpha = 0.28f) else C.raised)
        .border(1.dp, if (selected) C.primary else Color.Transparent, RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp)) {
        T(text, 13, if (selected) C.text else C.muted, if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable fun <X> Pills(items: List<X>, selected: (X) -> Boolean, label: (X) -> String, onClick: (X) -> Unit) =
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { Pill(label(it), selected(it)) { onClick(it) } }
    }

@Composable fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) { T(title, 15); if (subtitle.isNotEmpty()) T(subtitle, 12, C.muted) }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = C.primary, checkedThumbColor = C.text, uncheckedTrackColor = C.raised, uncheckedThumbColor = C.muted, uncheckedBorderColor = C.line))
    }

@Composable fun Stat(label: String, value: String, modifier: Modifier = Modifier, color: Color = C.text) =
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(C.raised).padding(12.dp)) {
        T(value, 19, color, FontWeight.SemiBold, lines = 1); T(label, 12, C.muted, lines = 2)
    }

@Composable fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) =
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        T(label, 15, modifier = Modifier.weight(1f))
        Box(Modifier.size(44.dp).clip(CircleShape).background(C.raised).clickable(onClick = onMinus), Alignment.Center) { T("−", 22, C.soft) }
        T(value, 16, weight = FontWeight.SemiBold, modifier = Modifier.width(96.dp), align = TextAlign.Center)
        Box(Modifier.size(44.dp).clip(CircleShape).background(C.raised).clickable(onClick = onPlus), Alignment.Center) { T("+", 22, C.soft) }
    }

@Composable fun TimeStepper(label: String, minutes: Int, step: Int = 15, onChange: (Int) -> Unit) =
    Stepper(label, SleepMath.fmtClock(minutes), { onChange((minutes - step + 1440) % 1440) }, { onChange((minutes + step) % 1440) })

@Composable fun Note(text: String) = T(text, 12, C.muted, modifier = Modifier.padding(top = 8.dp))

/** Generated cover art: a soft gradient with drifting orbs, tinted per track. */
@Composable fun Artwork(hue: Float, modifier: Modifier = Modifier, corner: Dp = 16.dp) {
    val a = Color.hsv(hue, 0.55f, 0.42f); val b = Color.hsv((hue + 40f) % 360f, 0.6f, 0.16f); val orb = Color.hsv((hue + 320f) % 360f, 0.35f, 0.8f)
    Canvas(modifier.clip(RoundedCornerShape(corner))) {
        drawRect(Brush.linearGradient(listOf(a, b), Offset.Zero, Offset(size.width, size.height)))
        val s = size.minDimension
        drawCircle(orb.copy(alpha = 0.22f), s * 0.42f, Offset(size.width * (0.25f + (hue % 50f) / 100f), size.height * 0.3f))
        drawCircle(orb.copy(alpha = 0.14f), s * 0.3f, Offset(size.width * 0.78f, size.height * 0.75f))
        drawCircle(Color(0xFFF1E4C8).copy(alpha = 0.75f), s * 0.07f, Offset(size.width * 0.72f, size.height * 0.26f))
    }
}

/** Live level of the audio engine, sampled every frame while playing. */
@Composable fun rememberLevel(playing: Boolean): State<Float> {
    val level = remember { mutableFloatStateOf(0f) }
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        if (!playing) { level.floatValue = 0f; return@LaunchedEffect }
        while (true) { withFrameNanos { phase.floatValue = it / 1e9f }; level.floatValue = (SoundEngine.mixer.outLevel * 5f).coerceIn(0.15f, 1f) }
    }
    return level
}

@Composable fun Waveform(playing: Boolean, modifier: Modifier = Modifier, bars: Int = 28, color: Color = C.soft) {
    val level by rememberLevel(playing)
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) { if (playing) while (true) withFrameNanos { time = it / 1e9f } }
    Canvas(modifier) {
        val w = size.width / (bars * 1.6f)
        for (i in 0 until bars) {
            val wob = 0.5f + 0.5f * sin(time * (1.1f + (i % 5) * 0.23f) + i * 0.9f)
            val h = (if (playing) (0.18f + 0.82f * wob * level) else 0.12f) * size.height
            drawRoundRect(color.copy(alpha = if (playing) 0.9f else 0.35f), Offset(i * w * 1.6f + w * 0.3f, (size.height - h) / 2), Size(w, h), CornerRadius(w / 2))
        }
    }
}

@Composable fun Gauge(value: Int, label: String, modifier: Modifier = Modifier) {
    Box(modifier, Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            val st = Stroke(10.dp.toPx(), cap = StrokeCap.Round)
            drawArc(C.line, 135f, 270f, false, style = st)
            drawArc(Brush.sweepGradient(listOf(C.deep, C.primary, C.soft, C.deep)), 135f, 270f * value / 100f, false, style = st)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { T("$value", 30, weight = FontWeight.Bold); T(label, 11, C.muted, align = TextAlign.Center) }
    }
}

@Composable fun Bar(fraction: Float, modifier: Modifier = Modifier, color: Color = C.primary, mark: Float = -1f) =
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(C.raised, cornerRadius = r)
        drawRoundRect(color, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height), cornerRadius = r)
        if (mark >= 0f) drawLine(C.warm, Offset(size.width * mark, -4f), Offset(size.width * mark, size.height + 4f), 3f)
    }
