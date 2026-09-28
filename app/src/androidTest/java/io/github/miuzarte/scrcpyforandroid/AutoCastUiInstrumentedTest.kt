package io.github.miuzarte.scrcpyforandroid

import android.content.ComponentName
import android.content.Intent
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.miuzarte.scrcpyforandroid.autocast.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoCastUiInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun coverWidgetInflatesAndLaunchAliasResolves() {
        compose.runOnIdle {
            val activity = compose.activity
            val views = RemoteViews(activity.packageName, R.layout.autocast_widget)
            val view = views.apply(activity, FrameLayout(activity))
            assertNotNull(view.findViewById<android.view.View>(R.id.autocast_widget_title))
            val alias = Intent().setComponent(ComponentName(activity.packageName, "${activity.packageName}.AutoCastActivity"))
            assertNotNull(alias.resolveActivity(activity.packageManager))
            assertTrue(AutoCastIntents.isAutoCast(alias))
            assertTrue(AutoCastIntents.isExternalInnerRequest(alias))
            assertTrue(AutoCastIntents.isAutoCast(AutoCastIntents.intent(activity)))
            assertFalse(AutoCastIntents.isExternalInnerRequest(AutoCastIntents.intent(activity)))
            assertEquals("com.example.target", AutoCastIntents.intent(activity, "com.example.target").getStringExtra(AutoCastIntents.EXTRA_START_APP))
            assertFalse(AutoCastIntents.isAutoCast(Intent(activity, MainActivity::class.java)))
        }
    }

    @Test fun controlCenterTileIsAnActionProtectedByTheSystemBindingPermission() {
        compose.runOnIdle {
            val context = compose.activity
            val tile = Intent(android.service.quicksettings.TileService.ACTION_QS_TILE)
                .setComponent(ComponentName(context, AutoCastTileService::class.java))
            val info = context.packageManager.resolveService(tile, android.content.pm.PackageManager.GET_META_DATA)!!.serviceInfo
            assertTrue(info.exported)
            assertEquals("android.permission.BIND_QUICK_SETTINGS_TILE", info.permission)
            assertEquals(context.getString(R.string.autocast_tile_name), info.loadLabel(context.packageManager))
            assertFalse(info.metaData.getBoolean("android.service.quicksettings.TOGGLEABLE_TILE"))
            assertTrue(AutoCastIntents.pendingIntent(context).isActivity)
        }
    }

    @Test fun oneTapEntryOnUnsupportedDeviceFailsBeforeConnecting() {
        org.junit.Assume.assumeFalse(AutoCastPolicy.supports(android.os.Build.MANUFACTURER, android.os.Build.MODEL))
        val activity = compose.activity
        val scenarioIntent = activity.intent
        try {
            compose.runOnIdle { activity.startActivity(AutoCastIntents.intent(activity)) }
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("autocast-status").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(activity.getString(R.string.autocast_unsupported)).assertIsDisplayed()
            assertNull(io.github.miuzarte.scrcpyforandroid.services.AppRuntime.scrcpy?.currentSessionState?.value)
            compose.onNodeWithText(activity.getString(R.string.autocast_close)).performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("autocast-status").fetchSemanticsNodes().isEmpty() }
            compose.onAllNodesWithText(activity.getString(R.string.device_title)).onFirst().assertIsDisplayed()
            compose.runOnIdle { assertFalse(activity.isFinishing) }
        } finally {
            // ActivityScenario matches lifecycle events against its original launch intent.
            compose.runOnIdle { activity.intent = scenarioIntent }
        }
    }
}
