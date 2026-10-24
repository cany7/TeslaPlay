package io.github.cany7.teslaplay

import androidx.compose.ui.test.*
import io.github.cany7.teslaplay.web.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "zh-rCN-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageStreamingSettingsUiTest : ComposeSettingsTest() {
    @Test fun userCanChooseFormatAndSaveValidatedQualityFromDisplaySettings() {
        val controller = Robolectric.buildActivity(TeslaPlayActivity::class.java).setup()
        val app = RuntimeEnvironment.getApplication()
        try {
            SettingsNavigation.open(controller.get(), "画面与串流", compose)
            clickTag("choice:IMAGE_FORMAT"); chooseOption(0)
            assertEquals(ImageFormat.JPEG, AirPlayPersistence.loadImageFormat(app))
            clickTag("text:IMAGE_QUALITY"); input("101"); saveInput()
            compose.onNodeWithText("请输入 1 到 100 之间的整数。").assertExists()
            assertEquals(100, AirPlayPersistence.loadImageQuality(app))
            input("32"); saveInput()
            assertEquals(ImageEncodingOptions(ImageFormat.JPEG, 32), AirPlayPersistence.imageEncoding(app, ImageFormat.WEBP))
            clickTag("text:IMAGE_QUALITY"); input("90"); cancelInput()
            assertEquals(32, AirPlayPersistence.loadImageQuality(app))
            SettingsNavigation.open(controller.get(), "画面与串流", compose)
            compose.onNodeWithTag("text:IMAGE_QUALITY").performScrollTo().assertTextContains("32")
            clickTag("choice:IMAGE_FORMAT"); chooseOption(1)
            assertEquals(ImageEncodingOptions(ImageFormat.WEBP, 32), AirPlayPersistence.imageEncoding(app, ImageFormat.WEBP))
            captureSettings(controller.get(), "image-streaming-settings")
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun displayHidesRetiredControlsAndShowsRecoveryOnlyInWebRtcAdvancedSettings() {
        val controller = Robolectric.buildActivity(TeslaPlayActivity::class.java).setup()
        try {
            SettingsNavigation.open(controller.get(), "画面与串流", compose)
            compose.onNodeWithTag("toggle:ADAPTIVE_SIZE").assertDoesNotExist()
            compose.onNodeWithTag("choice:RESOLUTION").assertDoesNotExist()
            compose.onNodeWithTag("choice:RECOVERY").assertDoesNotExist()
            clickTag("settings:display-advanced")
            compose.onNodeWithTag("choice:RECOVERY").assertExists()
            clickTag("choice:STREAM"); chooseOption(1)
            compose.onNodeWithTag("choice:RECOVERY").assertDoesNotExist()
            compose.onNodeWithTag("settings:display-advanced").assertDoesNotExist()
            compose.onNodeWithText("图片串流").assertExists()
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun windowEditorDistinguishesAutomaticSizeDisconnectedOutputAndUnsavedDraft() {
        val app = RuntimeEnvironment.getApplication()
        TeslaDisplayPreferences.preferences(app).edit().remove(TeslaDisplayPreferences.KEY).commit()
        WebSession.stop()
        val controller = Robolectric.buildActivity(TeslaPlayActivity::class.java).setup()
        try {
            SettingsNavigation.open(controller.get(), "画面与串流", compose)
            compose.onNodeWithTag("settings:tesla-window-row").assertTextContains("连接时采用浏览器区域")
            clickTag("settings:tesla-window-row")
            compose.onNodeWithText("当前输出: 未连接").assertExists()
            compose.onNodeWithText("自定义尺寸与比例").performClick()
            compose.onNodeWithText("窗口宽度（像素）").performTextReplacement("1600")
            compose.onNodeWithText("未保存的修改").assertExists()
            compose.onNodeWithText("取消").performClick()
            assertFalse(TeslaDisplayPreferences.load(app).enabled)
            assertEquals(1280, TeslaDisplayPreferences.load(app).right.width)
        } finally { controller.pause().stop().destroy() }
    }

}
