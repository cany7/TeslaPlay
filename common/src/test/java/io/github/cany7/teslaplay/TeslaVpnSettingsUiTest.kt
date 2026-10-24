package io.github.cany7.teslaplay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.compose.ui.test.*
import io.github.cany7.teslaplay.network.TeslaHttpMode
import io.github.cany7.teslaplay.web.RootAccess
import io.github.cany7.teslaplay.web.TeslaHttpCompatibility
import io.github.cany7.teslaplay.web.WebSession
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "zh-rCN-w360dp-h800dp",
    shadows = [TeslaVpnSettingsUiTest.ConsentShadow::class])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class TeslaVpnSettingsUiTest : ComposeSettingsTest() {
    @Implements(VpnService::class)
    class ConsentShadow : org.robolectric.shadows.ShadowService() {
        companion object {
            @JvmStatic @Implementation
            fun prepare(context: Context): Intent = Intent("teslaplay.test.VPN_CONSENT")
        }
    }

    private fun openSettings(activity: TeslaPlayActivity) =
        SettingsNavigation.open(activity, "Tesla 网络与 HTTPS", compose)

    private fun consent(activity: TeslaPlayActivity): Intent {
        var intent = shadowOf(activity).nextStartedActivity
        while (intent != null && intent.action != "teslaplay.test.VPN_CONSENT")
            intent = shadowOf(activity).nextStartedActivity
        assertNotNull("Expected Android VPN consent", intent)
        return intent
    }

    @Test fun defaultVpnRequestsConsentAndDenialKeepsConfiguredModeWithoutPromptLoop() {
        val original = RootAccess.authorize
        var rootRequests = 0
        RootAccess.startupChecked = false
        RootAccess.authorize = { rootRequests++; throw java.io.IOException("No root") }
        val controller = Robolectric.buildActivity(TeslaPlayActivity::class.java).setup()
        val activity = controller.get()
        try {
            val initialConsent = consent(activity)
            assertTrue(TeslaHttpCompatibility.config(activity).enabled)
            assertEquals(TeslaHttpMode.VPN, TeslaHttpCompatibility.config(activity).mode)
            shadowOf(activity).receiveResult(initialConsent, Activity.RESULT_CANCELED, null)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            openSettings(activity)
            compose.onNodeWithText("请求 Root 权限").assertDoesNotExist()
            compose.onNodeWithTag("toggle:TESLA").assertDoesNotExist()
            compose.onNodeWithTag("text:TESLA_ADDRESS").assertDoesNotExist()
            assertEquals("100.96.0.1", TeslaHttpCompatibility.config(activity).accessAddress)
            assertTrue(TeslaHttpCompatibility.config(activity).enabled)
            assertNull(shadowOf(activity).nextStartedActivity)
            assertEquals(0, rootRequests)
            compose.onNodeWithText("授权或重试 VPN").performScrollTo().performClick()
            assertEquals("teslaplay.test.VPN_CONSENT", consent(activity).action)
        } finally {
            RootAccess.authorize = original
            controller.pause().stop().destroy()
            WebSession.stop()
        }
    }

    @Test fun acceptedConsentAndModeChangesKeepRoutingAttachedToWebService() {
        val controller = Robolectric.buildActivity(TeslaPlayActivity::class.java).setup()
        val activity = controller.get()
        try {
            shadowOf(activity).receiveResult(consent(activity), Activity.RESULT_OK, null)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            openSettings(activity)
            assertTrue(TeslaHttpCompatibility.config(activity).enabled)
            clickTag("choice:TESLA_MODE")
            chooseOption(0)
            assertEquals(TeslaHttpMode.ROOT, TeslaHttpCompatibility.config(activity).mode)
            assertTrue(TeslaHttpCompatibility.config(activity).enabled)
            compose.onNodeWithText("请求 Root 权限").assertExists()
        } finally {
            controller.pause().stop().destroy()
            WebSession.stop()
        }
    }
}
