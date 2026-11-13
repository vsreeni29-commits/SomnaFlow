package com.somnaflow.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.somnaflow.app.audio.MixConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * Single on-device JSON store (settings + all logs). Nothing ever leaves the phone.
 * Composables read [rev] so they recompose whenever anything is saved.
 */
object Store {
    var rev by mutableIntStateOf(0); private set
    private var file: File? = null
    private var root = JSONObject()

    @Synchronized fun init(ctx: Context) {
        if (file != null) return
        file = File(ctx.filesDir, "somnaflow.json")
        root = try { JSONObject(file!!.readText()) } catch (_: Throwable) { JSONObject() }
    }

    @Synchronized fun save() {
        val f = file ?: return
        try {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(root.toString()); tmp.renameTo(f)
        } catch (_: Throwable) {}
        rev++
    }

    @Synchronized fun arr(name: String): JSONArray { rev; return root.optJSONArray(name) ?: JSONArray().also { root.put(name, it) } }
    /** Reading [rev] here subscribes every composable that reads the store, so screens refresh on save. */
    @Synchronized fun obj(name: String): JSONObject { rev; return root.optJSONObject(name) ?: JSONObject().also { root.put(name, it) } }
    private val settings get() = obj("settings")

    fun int(key: String, def: Int): Int = settings.optInt(key, def)
    fun bool(key: String, def: Boolean): Boolean = settings.optBoolean(key, def)
    fun str(key: String, def: String): String = settings.optString(key, def)
    fun dbl(key: String, def: Double): Double = settings.optDouble(key, def)
    fun set(key: String, v: Any) { synchronized(this) { settings.put(key, v) }; save() }

    // ---- Schedule & preferences ----
    val bedtimeMin get() = int("bedtimeMin", 22 * 60 + 30)
    val wakeMin get() = int("wakeMin", 6 * 60 + 30)
    val sleepNeedH get() = dbl("sleepNeedH", 8.0)
    val windDownLead get() = int("windDownLead", 45)
    val windDownEnabled get() = bool("wdEnabled", true)
    val wdDnd get() = bool("wdDnd", true)
    val wdDim get() = bool("wdDim", true)
    val wdAudio get() = bool("wdAudio", false)
    val alarmEnabled get() = bool("alarmEnabled", true)
    val smartWindow get() = int("smartWindow", 30)
    /** off | haptic | buzzer */
    val snoreMode get() = str("snoreMode", "haptic")
    val closedLoop get() = bool("closedLoop", true)
    val daylightPassive get() = bool("daylightPassive", true)
    val timerMin get() = int("timerMin", 45)

    // ---- Mix persistence ----
    fun saveMix(c: MixConfig) {
        val o = JSONObject().put("f", JSONArray(c.faders.map { it.toDouble() })).put("n", JSONArray(c.nature.toList()))
            .put("m", c.melody).put("b", c.beat).put("cs", c.csLock).put("r", c.randomize)
        set("mix", o)
    }
    fun loadMix(): MixConfig? = try {
        val o = settings.getJSONObject("mix"); val f = o.getJSONArray("f"); val n = o.getJSONArray("n")
        MixConfig(List(10) { f.getDouble(it).toFloat() }, (0 until n.length()).map { n.getString(it) }.toSet(),
            o.getString("m"), o.getInt("b"), o.getBoolean("cs"), o.getBoolean("r"))
    } catch (_: Throwable) { null }

    // ---- Logs ----
    fun add(name: String, o: JSONObject) { synchronized(this) { arr(name).put(o) }; save() }
    fun list(name: String): List<JSONObject> = synchronized(this) { val a = arr(name); List(a.length()) { a.getJSONObject(it) } }

    fun today(): String = LocalDate.now().toString()

    fun daylightMin(date: String = today()): Int = obj("daylight").optInt(date, 0)
    fun addDaylight(min: Int, date: String = today()) {
        synchronized(this) { obj("daylight").put(date, (daylightMin(date) + min).coerceIn(0, 600)) }; save()
    }

    fun tasksDone(date: String = today()): Set<String> = synchronized(this) {
        val a = obj("tasks").optJSONArray(date) ?: return emptySet(); (0 until a.length()).map { a.getString(it) }.toSet()
    }
    fun setTask(id: String, done: Boolean, date: String = today()) {
        val s = tasksDone(date).toMutableSet(); if (done) s.add(id) else s.remove(id)
        synchronized(this) { obj("tasks").put(date, JSONArray(s.toList())) }; save()
    }

    fun manualSleep(date: String): Double? = obj("manualSleep").let { if (it.has(date)) it.getDouble(date) else null }
    fun setManualSleep(date: String, h: Double) { synchronized(this) { obj("manualSleep").put(date, h) }; save() }
    fun healthSleep(date: String): Double? = obj("hcSleep").let { if (it.has(date)) it.getDouble(date) else null }

    fun nights(): List<Night> = list("nights").map { Night.from(it) }
    /** Hours slept for the night that ended on [date]: tracked night, else manual entry, else Health Connect. */
    fun sleepHours(date: String): Double? {
        val tracked = nights().filter { it.wakeDate == date }.sumOf { it.asleepMin } / 60.0
        return if (tracked > 0.5) tracked else manualSleep(date) ?: healthSleep(date)
    }
    fun last14(): List<Double?> { val t = LocalDate.now(); return (13 downTo 0).map { sleepHours(t.minusDays(it.toLong()).toString()) } }
    fun debtHours(): Double = SleepMath.debt(last14(), sleepNeedH)

    fun latestAssessment(type: String): JSONObject? = list("assess").lastOrNull { it.optString("type") == type }
    /** 0 unknown, 1 good sleeper, 2 moderate disturbance, 3 severe disturbance (from the PSQI global score). */
    fun severity(): Int { val s = latestAssessment("psqi")?.optInt("score", -1) ?: return 0; return if (s <= 5) 1 else if (s <= 10) 2 else 3 }

    /** Consecutive days (ending today or yesterday) with any SomnaFlow activity. */
    fun streak(): Int {
        val days = HashSet<String>()
        fun day(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
        list("moods").forEach { days.add(day(it.optLong("t"))) }
        nights().forEach { days.add(it.wakeDate) }
        list("sessions").forEach { days.add(day(it.optLong("t"))) }
        synchronized(this) { obj("tasks").keys().forEach { k -> if ((obj("tasks").optJSONArray(k)?.length() ?: 0) > 0) days.add(k) } }
        var d = LocalDate.now(); if (d.toString() !in days) d = d.minusDays(1)
        var n = 0; while (d.toString() in days) { n++; d = d.minusDays(1) }
        return n
    }
}

/** One tracked night. [epochs] has one char per minute: W awake, L light, D deep. */
data class Night(
    val start: Long, val end: Long, val epochs: String, val solMin: Int, val snoreMin: Int, val snoreEpisodes: Int,
    val pauses: Int, val interventions: Int, val breathRate: Float, val wokeBy: String
) {
    val asleepMin get() = epochs.count { it != 'W' }
    val deepMin get() = epochs.count { it == 'D' }
    val awakenings get() = Regex("[LD]W").findAll(epochs.drop(solMin.coerceAtMost(epochs.length))).count()
    val efficiency get() = if (epochs.isEmpty()) 0 else asleepMin * 100 / epochs.length
    val wakeDate: String get() = java.time.Instant.ofEpochMilli(end).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
    fun json(): JSONObject = JSONObject().put("start", start).put("end", end).put("epochs", epochs).put("sol", solMin)
        .put("snoreMin", snoreMin).put("snoreEp", snoreEpisodes).put("pauses", pauses).put("iv", interventions)
        .put("br", breathRate.toDouble()).put("wokeBy", wokeBy)
    companion object {
        fun from(o: JSONObject) = Night(o.optLong("start"), o.optLong("end"), o.optString("epochs"), o.optInt("sol"), o.optInt("snoreMin"),
            o.optInt("snoreEp"), o.optInt("pauses"), o.optInt("iv"), o.optDouble("br", 0.0).toFloat(), o.optString("wokeBy"))
    }
}
