package io.github.cany7.teslaplay

import androidx.compose.ui.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import io.github.cany7.teslaplay.web.WebListenSettings
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "zh-rCN-w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsSearchUiTest : ComposeSettingsTest() {
    @Test fun choosingSearchResultNavigatesWithoutChangingTheSetting() {
        val controller = Robolectric.buildActivity(TeslaPlayActivity::class.java).setup()
        val activity = controller.get()
        try {
            SettingsNavigation.open(activity, "画面与串流", compose)
            val before = AirPlayPersistence.loadHevcEnabled(activity)
            compose.onNodeWithTag("settings:search").performTextInput("h265")
            compose.onNodeWithTag("search:toggle:HEVC").performClick()
            compose.onNodeWithTag("toggle:HEVC").assertIsDisplayed()
            assertEquals(before, AirPlayPersistence.loadHevcEnabled(activity))
            compose.onNodeWithTag("settings:search").performTextInput("nothing-matches-98765")
            compose.onNodeWithTag("settings:search-empty").assertExists()
            compose.onNodeWithTag("settings:search").performTextClearance()
            compose.onNodeWithTag("toggle:HEVC").assertExists()
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun conditionalSearchExplainsHttpsWithoutEnablingIt() {
        WebListenSettings.saveHttpsEnabled(RuntimeEnvironment.getApplication(), false)
        val controller = Robolectric.buildActivity(TeslaPlayActivity::class.java).setup()
        val activity = controller.get()
        try {
            SettingsNavigation.open(activity, "画面与串流", compose)
            compose.onNodeWithTag("settings:search").performTextInput("HTTPS")
            compose.onNodeWithTag("search:text:HTTPS_PORT").performScrollTo().performClick()
            compose.onNodeWithTag("toggle:HTTPS").assertExists()
            compose.onNodeWithTag("text:HTTPS_PORT").assertExists()
            compose.onNodeWithTag("toggle:HTTPS").assertIsOff()
            assertFalse(WebListenSettings.httpsEnabled(activity))
            compose.onNodeWithText(activity.getString(io.github.cany7.teslaplay.host.R.string.settings_https_ports_search)).assertExists()
        } finally { controller.pause().stop().destroy() }
    }
}
