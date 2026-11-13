package com.somnaflow.app.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

/** Keeps the process alive (and the CPU awake) while the soundscape plays with the screen off. */
class PlaybackService : Service() {
    private var wake: PowerManager.WakeLock? = null
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notif.channels(this)
        val n = Notif.build(this, Notif.CH_PLAY, Player.track?.title ?: "SomnaFlow mix", "Playing · tap to open",
            actionLabel = "Stop", actionIntent = Notif.action(this, SomnaReceiver.STOP_AUDIO, 11))
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(Notif.ID_PLAY, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            else startForeground(Notif.ID_PLAY, n)
        } catch (_: Throwable) { stopSelf(); return START_NOT_STICKY }
        if (wake == null) wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "somnaflow:play").also { it.acquire(12 * 3600_000L) }
        if (!Player.playing) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() { try { wake?.release() } catch (_: Throwable) {}; wake = null; super.onDestroy() }
}
