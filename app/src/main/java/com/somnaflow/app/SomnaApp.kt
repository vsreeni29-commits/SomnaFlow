package com.somnaflow.app

import android.app.Application
import com.somnaflow.app.bio.Bio
import com.somnaflow.app.data.Store
import com.somnaflow.app.service.Notif
import com.somnaflow.app.service.Player
import com.somnaflow.app.service.Scheduler
import com.somnaflow.app.service.WindDown

class SomnaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this); Notif.channels(this); Player.init(this); Bio.init(this)
        WindDown.refresh()
        try { Scheduler.all(this) } catch (_: Throwable) {}
    }
}
