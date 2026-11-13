package com.somnaflow.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import com.somnaflow.app.data.Store
import com.somnaflow.app.service.WindDown
import com.somnaflow.app.ui.Nav
import com.somnaflow.app.ui.SomnaRoot

class MainActivity : ComponentActivity() {
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        route(intent)
        setContent {
            // Lower the panel brightness itself while wind-down is active.
            LaunchedEffect(WindDown.active, Store.rev) {
                window.attributes = window.attributes.apply {
                    screenBrightness = if (WindDown.active && Store.wdDim) 0.06f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
            SomnaRoot()
        }
        if (Build.VERSION.SDK_INT >= 33 && savedInstanceState == null && !Store.bool("askedNotif", false)) {
            Store.set("askedNotif", true)
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); route(intent) }
    override fun onResume() { super.onResume(); WindDown.refresh() }

    private fun route(i: Intent?) {
        val r = i?.getStringExtra("route") ?: return
        i.removeExtra("route")
        if (r.isNotEmpty() && r != "alarm") { Nav.stack.clear(); Nav.go(r) }
    }
}
