package com.somnaflow.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.somnaflow.app.MainActivity
import com.somnaflow.app.R

object Notif {
    const val CH_PLAY = "playback"; const val CH_TRACK = "tracking"; const val CH_ALARM = "alarm"; const val CH_ROUTINE = "routine"
    const val ID_PLAY = 1; const val ID_TRACK = 2; const val ID_ALARM = 3; const val ID_WIND = 4

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_PLAY, "Audio playback", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_TRACK, "Sleep tracking", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_ALARM, "Wake-up alarm", NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null); setBypassDnd(true) })
        nm.createNotificationChannel(NotificationChannel(CH_ROUTINE, "Wind-down reminders", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun open(ctx: Context, route: String = "", req: Int = 0): PendingIntent =
        PendingIntent.getActivity(ctx, 100 + req, Intent(ctx, MainActivity::class.java).putExtra("route", route)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun action(ctx: Context, action: String, req: Int): PendingIntent =
        PendingIntent.getBroadcast(ctx, req, Intent(ctx, SomnaReceiver::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun build(ctx: Context, channel: String, title: String, text: String, route: String = "", ongoing: Boolean = true,
              actionLabel: String? = null, actionIntent: PendingIntent? = null, fullScreen: Boolean = false): Notification {
        val b = Notification.Builder(ctx, channel).setSmallIcon(R.drawable.ic_stat).setContentTitle(title).setContentText(text)
            .setContentIntent(open(ctx, route, channel.hashCode() and 0xff)).setOngoing(ongoing).setOnlyAlertOnce(true)
            .setColor(0xFF8B6FE8.toInt())
        if (actionLabel != null && actionIntent != null) b.addAction(Notification.Action.Builder(null, actionLabel, actionIntent).build())
        if (fullScreen) b.setFullScreenIntent(open(ctx, "alarm", 9), true).setCategory(Notification.CATEGORY_ALARM)
        return b.build()
    }
}
