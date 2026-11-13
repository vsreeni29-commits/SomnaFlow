package com.somnaflow.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.somnaflow.app.data.Content
import com.somnaflow.app.data.Questionnaires
import com.somnaflow.app.data.SleepMath
import com.somnaflow.app.data.Store
import com.somnaflow.app.service.Player
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private fun valenceColor(v: Int): Color = when (v) { -3 -> Color(0xFF6A4BA8); -2 -> Color(0xFF7C5CC0); -1 -> Color(0xFF8F78C9); 0 -> Color(0xFF9E94C4)
    1 -> Color(0xFFB79FD0); 2 -> Color(0xFFD3A9C0); else -> Color(0xFFE8B98A) }

/** The visual for the affect scale: a jagged, tight shape for unpleasant states that opens into a soft bloom. */
@Composable private fun MoodOrb(v: Int, modifier: Modifier) = Canvas(modifier) {
    val c = Offset(size.width / 2, size.height / 2); val r = size.minDimension * 0.36f
    val petals = 6 + (v + 3); val spike = if (v < 0) 0.10f - v * 0.06f else 0.10f; val round = v >= 0
    val p = Path()
    val steps = 180
    for (i in 0..steps) {
        val a = 2 * PI * i / steps
        val wobble = if (round) 1f + spike * sin(petals * a).toFloat() * (0.6f + v * 0.25f)
        else 1f + spike * (if (((petals * a / PI).toInt()) % 2 == 0) 1f else -1f) * (1f - ((petals * a / PI) % 1.0).toFloat() * 0.5f)
        val x = c.x + r * wobble * cos(a).toFloat(); val y = c.y + r * wobble * sin(a).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    val col = valenceColor(v)
    drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.35f), Color.Transparent), c, r * 1.7f), r * 1.7f, c)
    drawPath(p, Brush.radialGradient(listOf(col, col.copy(alpha = 0.55f)), c, r * 1.2f))
    drawPath(p, col.copy(alpha = 0.9f), style = Stroke(3f))
}

@Composable fun CheckInScreen() {
    val ctx = LocalContext.current
    var v by remember { mutableIntStateOf(0) }
    var emotions by remember { mutableStateOf(setOf<String>()) }
    var impacts by remember { mutableStateOf(setOf<String>()) }
    var note by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    Screen("State of mind", "Naming a feeling slows the heart and softens its grip") {
        if (saved) {
            Panel(color = C.raised) {
                Icon(Icons.Rounded.CheckCircle, null, tint = C.good)
                T("Logged. Thank you for checking in.", 17, weight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                T(if (v < 0) "You named it — that alone lowers arousal. A sleep-specific practice can help you set it down for the night."
                  else "A settled mind is a good start. Keep the evening slow and let sleep come to you.", 13, C.muted)
            }
            Spacer(Modifier.height(12.dp))
            val pick = Content.byId(if (v <= -2) "sm_bodyscan" else if (v < 0) "sm_478" else "st_river")!!
            BigButton("Play “${pick.title}”", icon = Icons.Rounded.PlayArrow) { Player.play(ctx, pick); Nav.back(); Nav.go("player") }
            Spacer(Modifier.height(10.dp))
            BigButton("Done", secondary = true) { Nav.back() }
            return@Screen
        }
        T("How are you feeling right now?", 16, weight = FontWeight.SemiBold)
        MoodOrb(v, Modifier.fillMaxWidth().height(190.dp))
        T(Content.VALENCE[v + 3], 20, valenceColor(v), FontWeight.SemiBold, Modifier.fillMaxWidth(), TextAlign.Center)
        Slider(v.toFloat(), { n -> val nv = Math.round(n); if (nv != v) { v = nv; emotions = emptySet() } }, valueRange = -3f..3f, steps = 5,
            colors = SliderDefaults.colors(thumbColor = C.soft, activeTrackColor = C.primary, inactiveTrackColor = C.raised, activeTickColor = C.deep, inactiveTickColor = C.line))
        Row(Modifier.fillMaxWidth()) { T("Very Unpleasant", 11, C.muted, modifier = Modifier.weight(1f)); T("Very Pleasant", 11, C.muted) }

        Section("What best describes this feeling?")
        Pills(Content.EMOTIONS[v]!!.distinct(), { it in emotions }, { it }) { emotions = if (it in emotions) emotions - it else emotions + it }
        Section("What is having the biggest impact?")
        Pills(Content.IMPACTS, { it in impacts }, { it }) { impacts = if (it in impacts) impacts - it else impacts + it }
        Section("Anything to set down for the night? (optional)")
        OutlinedTextField(note, { note = it.take(400) }, Modifier.fillMaxWidth(), minLines = 2,
            placeholder = { T("Write it here so you don't have to carry it to bed", 13, C.muted) },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = C.text, unfocusedTextColor = C.text, focusedBorderColor = C.primary, unfocusedBorderColor = C.line, cursorColor = C.soft))
        Spacer(Modifier.height(16.dp))
        BigButton("Save check-in", icon = Icons.Rounded.Check) {
            Store.add("moods", JSONObject().put("t", System.currentTimeMillis()).put("v", v).put("e", JSONArray(emotions.toList())).put("i", JSONArray(impacts.toList())).put("note", note))
            saved = true
        }
    }
}

@Composable fun AssessmentsScreen() = Screen("Assessments", "Clinical baselines and progress reports") {
    Questionnaires.ALL.forEach { q ->
        val hist = Store.list("assess").filter { it.optString("type") == q.id }
        val last = hist.lastOrNull()
        Panel(Modifier.padding(bottom = 12.dp)) {
            T(q.title, 17, weight = FontWeight.SemiBold); T(q.about, 12, C.muted)
            if (last != null) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    T("${last.optInt("score")}", 30, C.soft, FontWeight.Bold); T(" / ${q.max}", 14, C.muted, modifier = Modifier.padding(bottom = 5.dp))
                    Spacer(Modifier.width(12.dp)); T(q.band(last.optInt("score")), 14, modifier = Modifier.padding(bottom = 5.dp))
                }
                if (hist.size > 1) {
                    val first = hist.first().optInt("score"); val d = last.optInt("score") - first
                    T("Progress: ${if (d < 0) "improved by ${-d}" else if (d > 0) "up by $d" else "unchanged"} since your first of ${hist.size} reports (lower is better)", 12, if (d < 0) C.good else C.muted)
                    Spark(hist.map { it.optInt("score").toFloat() }, q.max.toFloat(), Modifier.fillMaxWidth().height(48.dp).padding(top = 8.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            BigButton(if (last == null) "Take now" else "Retake", secondary = last != null) { Nav.go("assess/${q.id}") }
        }
    }
    Note("These are screening questionnaires for self-tracking, not a diagnosis. The PSQI structure and scoring follow Buysse et al. (1989); PHQ-9 and GAD-7 are public-domain instruments. Share results with a clinician if you are concerned.")
}

@Composable fun Spark(values: List<Float>, max: Float, modifier: Modifier) = Canvas(modifier) {
    if (values.size < 2) return@Canvas
    val p = Path()
    values.forEachIndexed { i, v -> val x = size.width * i / (values.size - 1); val y = size.height * (1 - v / max); if (i == 0) p.moveTo(x, y) else p.lineTo(x, y); drawCircle(C.soft, 5f, Offset(x, y)) }
    drawPath(p, C.primary, style = Stroke(4f))
}

@Composable fun AssessScreen(id: String) {
    val q = remember(id) { Questionnaires.byId(id) }
    val answers = remember(id) { mutableStateListOf<Int>().apply { q.questions.forEach { add(if (it.kind == 0) -1 else it.default) } } }
    var i by remember(id) { mutableIntStateOf(0) }
    var score by remember(id) { mutableIntStateOf(-1) }
    Screen(q.title, if (score < 0) "Question ${i + 1} of ${q.questions.size}" else "Your result") {
        if (score >= 0) {
            Panel(color = C.raised) {
                Row(verticalAlignment = Alignment.Bottom) { T("$score", 44, C.soft, FontWeight.Bold); T(" / ${q.max}", 16, C.muted, modifier = Modifier.padding(bottom = 9.dp)) }
                T(q.band(score), 18, weight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp)); Bar(score / q.max.toFloat())
            }
            if (id == "psqi") {
                Section("Components (0 best – 3 worst)")
                val comps = Questionnaires.psqiComponents(answers.toIntArray())
                Panel { Questionnaires.PSQI_COMPONENTS.forEachIndexed { k, name -> Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(name, 13, modifier = Modifier.weight(1f)); Bar(comps[k] / 3f, Modifier.width(90.dp), if (comps[k] >= 2) C.warm else C.primary); T("  ${comps[k]}", 13, C.muted) } } }
                Note(when { score <= 5 -> "Your sleep is in the healthy range. SomnaFlow will keep things light: entrainment and soundscapes first."
                    score <= 10 -> "Moderate disturbance. Your library now leads with Sleep Stories and sleep-specific meditations alongside theta entrainment."
                    else -> "Severe disturbance. Sleep Stories and sleep meditations are now prioritised, as they show the strongest gains for profiles like yours. Please also consider speaking with a doctor about your sleep." })
            }
            if (id == "phq9" && answers[8] > 0) {
                Spacer(Modifier.height(12.dp))
                Panel(color = Color(0xFF2A1A2E)) {
                    T("You are not alone", 16, weight = FontWeight.SemiBold)
                    T("You marked having thoughts of being better off dead or of hurting yourself. Please talk to someone you trust or a health professional today. If you might act on these thoughts, contact your local emergency number or a crisis line right now.", 13, C.text)
                }
            } else if ((id == "phq9" || id == "gad7") && score >= 10) Note("Scores of 10 or more are worth discussing with a doctor or therapist. Low mood and anxiety are closely tied to sleep, and both are treatable.")
            Spacer(Modifier.height(16.dp))
            BigButton("Done") { Nav.back() }
            return@Screen
        }
        Bar((i + 1f) / q.questions.size); Spacer(Modifier.height(14.dp))
        if (i == 0 || (id == "psqi" && i in 4..13)) T(if (id == "psqi" && i in 4..13) "During the past month, how often have you had trouble sleeping…" else q.intro, 13, C.muted, modifier = Modifier.padding(bottom = 10.dp))
        val item = q.questions[i]
        T(item.text, 19, weight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        when (item.kind) {
            0 -> item.options.forEachIndexed { k, opt ->
                val on = answers[i] == k
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(16.dp)).background(if (on) C.primary.copy(alpha = 0.28f) else C.surface)
                    .clickable { answers[i] = k }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (on) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked, null, tint = if (on) C.soft else C.muted)
                    T(opt, 15, modifier = Modifier.padding(start = 12.dp))
                }
            }
            1 -> Panel { TimeStepper("Time", answers[i]) { answers[i] = it } }
            2 -> Panel { Stepper("Minutes", "${answers[i]} min", { answers[i] = (answers[i] - 5).coerceAtLeast(0) }, { answers[i] = (answers[i] + 5).coerceAtMost(240) }) }
            else -> Panel { Stepper("Hours of sleep", "%.1f h".format(answers[i] / 10f), { answers[i] = (answers[i] - 5).coerceAtLeast(0) }, { answers[i] = (answers[i] + 5).coerceAtMost(140) }) }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (i > 0) BigButton("Previous", Modifier.weight(1f), secondary = true) { i-- }
            BigButton(if (i == q.questions.size - 1) "See result" else "Next", Modifier.weight(1f), enabled = answers[i] >= 0) {
                if (i < q.questions.size - 1) i++ else {
                    val s = q.score(answers.toIntArray()); score = s
                    Store.add("assess", JSONObject().put("t", System.currentTimeMillis()).put("type", id).put("score", s).put("a", JSONArray(answers.toList())))
                }
            }
        }
    }
}
