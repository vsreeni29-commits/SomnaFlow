package com.somnaflow.app.bio

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.somnaflow.app.data.Store
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Health Connect is Android's equivalent of Apple HealthKit: watches and fitness apps (Fitbit, Samsung Health,
 * Google Fit, Garmin, Oura…) write into it, and SomnaFlow reads steps, resting heart rate, sleep stages,
 * blood oxygen and respiratory rate from it, read-only.
 */
object Health {
    val PERMISSIONS = setOf(
        HealthPermission.getReadPermission(StepsRecord::class), HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class), HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(RespiratoryRateRecord::class)
    )
    const val PROVIDER = "com.google.android.apps.healthdata"

    /** 0 = ready, 1 = needs install/update, 2 = not supported on this phone */
    fun status(ctx: Context): Int = try {
        when (HealthConnectClient.getSdkStatus(ctx, PROVIDER)) {
            HealthConnectClient.SDK_AVAILABLE -> 0
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> 1
            else -> 2
        }
    } catch (_: Throwable) { 2 }

    suspend fun granted(ctx: Context): Boolean = try {
        HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions().any { it in PERMISSIONS }
    } catch (_: Throwable) { false }

    /** Pulls the latest data into the local store. Each read is independent so partial permission still works. */
    suspend fun sync(ctx: Context): String {
        val client = try { HealthConnectClient.getOrCreate(ctx) } catch (_: Throwable) { return "Health Connect is not available" }
        val zone = ZoneId.systemDefault(); val now = Instant.now()
        val dayStart = LocalDate.now().atStartOfDay(zone).toInstant()
        val out = JSONObject().put("syncedAt", System.currentTimeMillis())
        var got = 0
        try {
            val steps = client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(dayStart, now)))[StepsRecord.COUNT_TOTAL]
            if (steps != null) { out.put("steps", steps); got++ }
        } catch (_: Throwable) {}
        val week = TimeRangeFilter.between(now.minus(Duration.ofDays(7)), now)
        try { client.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, week)).records.lastOrNull()?.let { out.put("restingHr", it.beatsPerMinute); got++ } } catch (_: Throwable) {}
        try { client.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, week)).records.lastOrNull()?.let { out.put("spo2", it.percentage.value); got++ } } catch (_: Throwable) {}
        try { client.readRecords(ReadRecordsRequest(RespiratoryRateRecord::class, week)).records.lastOrNull()?.let { out.put("resp", it.rate); got++ } } catch (_: Throwable) {}
        try {
            val sessions = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(now.minus(Duration.ofDays(15)), now))).records
            val perDay = HashMap<String, Double>()
            for (s in sessions) {
                var asleep = 0L; var deep = 0L; var rem = 0L; var core = 0L
                for (st in s.stages) {
                    val m = Duration.between(st.startTime, st.endTime).toMinutes()
                    when (st.stage) {
                        SleepSessionRecord.STAGE_TYPE_DEEP -> { deep += m; asleep += m }
                        SleepSessionRecord.STAGE_TYPE_REM -> { rem += m; asleep += m }
                        SleepSessionRecord.STAGE_TYPE_LIGHT, SleepSessionRecord.STAGE_TYPE_SLEEPING -> { core += m; asleep += m }
                    }
                }
                val inBed = Duration.between(s.startTime, s.endTime).toMinutes()
                if (s.stages.isEmpty()) asleep = inBed
                val date = s.endTime.atZone(zone).toLocalDate().toString()
                perDay[date] = (perDay[date] ?: 0.0) + asleep / 60.0
                if (s === sessions.last()) out.put("sleep", JSONObject().put("core", core).put("deep", deep).put("rem", rem).put("inBed", inBed).put("asleep", asleep).put("date", date))
            }
            val o = Store.obj("hcSleep"); perDay.forEach { (d, h) -> o.put(d, h) }
            if (sessions.isNotEmpty()) got++
        } catch (_: Throwable) {}
        synchronized(Store) { val o = Store.obj("hc"); o.keys().asSequence().toList().forEach { o.remove(it) }; out.keys().forEach { o.put(it, out.get(it)) } }
        Store.save()
        return if (got == 0) "No data found yet — check that a watch or health app is writing to Health Connect" else "Synced $got data types"
    }
}
