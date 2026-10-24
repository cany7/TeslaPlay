package io.github.cany7.teslaplay

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.cany7.teslaplay.settings.SettingsTextDialog
import io.github.cany7.teslaplay.settings.SettingsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "zh-rCN-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeSettingsSmokeTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun authenticationScreenUsesCompose() {
        val controller = Robolectric.buildActivity(TeslaPlayActivity::class.java).setup()
        try {
            compose.onNodeWithText("从 DiPlay APK 导入").assertExists()
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    @Config(sdk = [29, 35])
    fun textDialogWorksOnAndroid29And35() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            controller.get().setContent {
                SettingsTheme { SettingsTextDialog("Port", "8443", onDismiss = {}) { null } }
            }
            compose.onNodeWithText("8443").assertExists()
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
