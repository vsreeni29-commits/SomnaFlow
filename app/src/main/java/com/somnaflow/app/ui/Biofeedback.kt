package com.somnaflow.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.somnaflow.app.bio.Bio
import com.somnaflow.app.bio.Ble
import com.somnaflow.app.bio.BrainState
import com.somnaflow.app.bio.CameraPpg
import com.somnaflow.app.bio.EegAnalyzer
import com.somnaflow.app.data.Store

@Composable fun LinePlot(values: List<Float>, modifier: Modifier, color: Color = C.soft, lo: Float? = null, hi: Float? = null, mark: Float? = null) = Canvas(modifier) {
    if (values.size < 2) return@Canvas
    val mn = lo ?: values.min(); val mx = (hi ?: values.max()).let { if (it - mn < 1e-6f) mn + 1f else it }
    val p = Path()
    values.forEachIndexed { i, v -> val x = size.width * i / (values.size - 1); val y = size.height * (1 - ((v - mn) / (mx - mn)).coerceIn(0f, 1f)); if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
    if (mark != null) { val y = size.height * (1 - (mark - mn) / (mx - mn)); drawLine(C.warm.copy(alpha = 0.6f), Offset(0f, y), Offset(size.width, y), 2f) }
    drawPath(p, color, style = Stroke(3.5f))
}

@Composable fun BiofeedbackScreen() {
    val ctx = LocalContext.current
    val blePerms = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    val bleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r -> if (r.values.all { it }) Ble.scan(ctx) }
    val camLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) CameraPpg.start(ctx) }
    DisposableEffect(Unit) { onDispose { CameraPpg.stop(); Ble.stopScan(ctx) } }

    Screen("Biofeedback", "Brain and heart signals, closing the loop") {
        // ---------- EEG ----------
        Section("Brainwaves (EEG)")
        Panel {
            if (Bio.eegSource.isEmpty()) {
                T("No EEG source connected", 15, weight = FontWeight.SemiBold)
                T("Connect a Bluetooth EEG headband below, or run the built-in demo signal to see the closed loop work: it begins in a racing-mind beta state and settles as the theta audio plays.", 13, C.muted)
                Spacer(Modifier.height(10.dp)); BigButton("Run demo signal", secondary = true, icon = Icons.Rounded.Science) { Bio.startDemo() }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { T(Bio.brain.label, 22, if (Bio.brain == BrainState.HYPERAROUSAL) C.warm else C.soft, FontWeight.SemiBold); T(Bio.brain.hint, 13, C.muted) }
                    T(Bio.eegSource, 11, C.muted)
                }
                Spacer(Modifier.height(12.dp))
                EegAnalyzer.BANDS.forEachIndexed { i, (name, range) ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.width(92.dp)) { T(name, 13); T("${range.first.toString().removeSuffix(".0")}–${range.second.toInt()} Hz", 10, C.muted) }
                        Bar(Bio.bands[i], Modifier.weight(1f), if (i >= 3) C.warm else if (i == 1) C.soft else C.primary)
                        T("${(Bio.bands[i] * 100).toInt()}%", 12, C.muted, modifier = Modifier.width(40.dp).padding(start = 6.dp))
                    }
                }
                Spacer(Modifier.height(8.dp)); T("Arousal trend (beta + gamma share, last 2 minutes)", 11, C.muted)
                LinePlot(Bio.trend, Modifier.fillMaxWidth().height(60.dp).padding(top = 4.dp), C.warm, 0f, 1f, 0.35f)
                if (Bio.demoRunning) { Spacer(Modifier.height(10.dp)); BigButton("Stop demo signal", secondary = true) { Bio.stopDemo() } }
            }
        }
        Spacer(Modifier.height(10.dp))
        Panel(color = if (Bio.loopActive) Color(0xFF2B1F4F) else C.surface) {
            Toggle("Closed-loop calming", "When sustained high beta is detected, 6 Hz theta beats start automatically and fade once alpha/theta take over", Store.closedLoop) { Store.set("closedLoop", it) }
            T(if (Bio.loopActive) "Intervening now · theta entrainment playing" else if (Bio.loopCount > 0) "Standing by · ${Bio.loopCount} automatic intervention${if (Bio.loopCount > 1) "s" else ""} this session" else "Standing by", 13, if (Bio.loopActive) C.warm else C.muted)
        }

        // ---------- Heart ----------
        Section("Heart rate variability")
        Panel {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Heart rate", if (Bio.heartRate > 0) "${Bio.heartRate.toInt()} bpm" else "—", Modifier.weight(1f))
                Stat("HRV (RMSSD)", if (Bio.rmssd > 0) "${Bio.rmssd.toInt()} ms" else "—", Modifier.weight(1f))
                Stat("vs baseline", if (Bio.baseline > 0) "%+.0f%%".format(Bio.changePct) else "—", Modifier.weight(1f), if (Bio.changePct >= 15f) C.good else C.text)
            }
            Spacer(Modifier.height(10.dp))
            T(if (Bio.baseline <= 0f) "The first minute sets your baseline. Then breathe slowly: a 15–20% rise in RMSSD marks the shift from fight-or-flight to rest-and-digest."
              else if (Bio.changePct >= 15f) "Parasympathetic shift reached — your body is in rest-and-digest mode." else "Relaxation target: +15–20% above your baseline of ${Bio.baseline.toInt()} ms.", 13, C.muted)
            if (Bio.baseline > 0f) { Spacer(Modifier.height(8.dp)); Bar((Bio.changePct / 30f).coerceIn(0f, 1f), mark = 0.5f, color = if (Bio.changePct >= 15f) C.good else C.primary) }
            Spacer(Modifier.height(12.dp))
            if (CameraPpg.running) {
                T(if (CameraPpg.fingerOn) "Signal quality ${(CameraPpg.quality * 100).toInt()}% · hold still" else "Cover the rear camera and flash completely with one fingertip", 13, if (CameraPpg.fingerOn) C.good else C.warm)
                LinePlot(CameraPpg.wave, Modifier.fillMaxWidth().height(56.dp).padding(vertical = 4.dp))
                BigButton("Finish measurement", secondary = true) { CameraPpg.stop() }
            } else BigButton("Measure with phone camera", secondary = true, icon = Icons.Rounded.Fingerprint) { camLauncher.launch(Manifest.permission.CAMERA) }
            if (CameraPpg.error.isNotEmpty()) Note(CameraPpg.error)
            if (Bio.hrSource.isNotEmpty()) Note("Source: ${Bio.hrSource}")
        }

        // ---------- Devices ----------
        Section("Bluetooth wearables")
        Panel {
            T("Works with heart-rate straps and watches that broadcast the standard Bluetooth Heart Rate profile, Muse-compatible EEG headbands, and EEG boards that stream samples over Nordic UART.", 13, C.muted)
            Spacer(Modifier.height(10.dp))
            BigButton(if (Ble.scanning) "Scanning…" else "Scan for devices", secondary = true, icon = Icons.Rounded.BluetoothSearching, enabled = !Ble.scanning) { bleLauncher.launch(blePerms) }
            if (Ble.status.isNotEmpty()) Note(Ble.status)
            Ble.found.take(12).forEach { d ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(14.dp)).clickable { Ble.connect(ctx, d) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (d.kind == "Heart rate") Icons.Rounded.MonitorHeart else if (d.kind == "Other") Icons.Rounded.Bluetooth else Icons.Rounded.Psychology, null, tint = if (d.kind == "Other") C.muted else C.soft)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) { T(d.name, 14, lines = 1); T(d.kind, 11, C.muted) }
                    T("Connect", 13, C.soft)
                }
            }
            if (Bio.hrSource.isNotEmpty() || (Bio.eegSource.isNotEmpty() && !Bio.demoRunning)) { Spacer(Modifier.height(10.dp)); BigButton("Disconnect all", secondary = true) { Bio.saveHeartSession(); Ble.disconnectAll() } }
        }
        Note("Wellness feedback only — not a medical device.")
    }
}
