package com.somnaflow.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.somnaflow.app.data.Night
import com.somnaflow.app.data.Store
import com.somnaflow.app.ui.Nav
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Launches the real activity and renders every screen: catches runtime crashes and produces layout screenshots. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-hdpi")
class ScreensTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) { rule.waitForIdle(); rule.onRoot().captureRoboImage("build/shots/$name.png") }

    @Test fun everyScreenRenders() {
        // Seed a little history so charts and reports have something to draw.
        val now = System.currentTimeMillis()
        val epochs = "W".repeat(14) + "L".repeat(40) + "D".repeat(60) + "L".repeat(50) + "W".repeat(4) + "L".repeat(70) + "D".repeat(45) + "L".repeat(120) + "W".repeat(6)
        Store.add("nights", Night(now - epochs.length * 60000L, now, epochs, 14, 22, 3, 1, 2, 14.5f, "smart").json())
        for (i in 1..9) Store.setManualSleep(LocalDate.now().minusDays(i.toLong()).toString(), 6.0 + (i % 4) * 0.7)
        Store.addDaylight(55)
        Store.add("assess", JSONObject().put("t", now - 86400000L * 20).put("type", "psqi").put("score", 13))
        Store.add("assess", JSONObject().put("t", now).put("type", "psqi").put("score", 9))
        shot("home")
        rule.runOnUiThread { Nav.home(1) }; shot("library")
        rule.runOnUiThread { Nav.home(2) }; shot("profile")
        for (r in listOf("player", "mixer", "checkin", "assessments", "assess/psqi", "assess/phq9", "assess/gad7", "tonight", "night/0", "bio",
            "winddown", "stretch", "daylight", "schedule", "health", "about")) {
            rule.runOnUiThread { Nav.stack.clear(); Nav.go(r) }
            shot(r.replace('/', '_'))
        }
    }
}
