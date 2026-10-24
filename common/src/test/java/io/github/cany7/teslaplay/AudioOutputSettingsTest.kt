package io.github.cany7.teslaplay

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.lifecycle.SavedStateHandle
import io.github.cany7.teslaplay.settings.*
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
class AudioOutputSettingsTest : ComposeSettingsTest() {
    private val app get() = RuntimeEnvironment.getApplication()

    private fun actions(choice: (ChoiceSetting, Int) -> Unit) = SettingsActions(
        choice = choice,
        toggle = { _, _ -> },
        text = { _, _ -> null },
        credentials = { _, _ -> null },
        margins = {},
        resetBranding = {},
        command = {},
        renameBrowser = { _, _ -> },
        removeBrowser = {},
    )

    @Test
    fun bluetoothChoiceKeepsPreferencesHidesInappAudioControlsAndShowsPendingRoute() {
        AirPlayPersistence.saveAudioOutput(app, CarPlayAudioOutput.BROWSER)
        AirPlayPersistence.saveMicrophoneSource(app, CarPlayMicrophoneSource.BROWSER)
        AirPlayPersistence.saveMediaBufferMillis(app, 1000)
        val model = SettingsViewModel(app, SavedStateHandle())
        val runtime = SettingsRuntime(
            audioOutputActive = CarPlayAudioOutput.BROWSER,
            audioStatus = "当前输出：车机浏览器",
            microphoneStatus = "当前麦克风：浏览器",
        )
        val state = mutableStateOf(model.state.value.copy(runtime = runtime))
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            controller.get().setContent {
                SettingsTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        AudioSettingsScreen(state.value, actions { choice, index ->
                            model.choose(choice, index)
                            state.value = model.state.value.copy(runtime = runtime)
                        })
                    }
                }
            }
            clickTag("choice:AUDIO_OUTPUT")
            compose.onNodeWithText("iPhone 蓝牙输出").assertExists()
            chooseOption(CarPlayAudioOutput.IPHONE_BLUETOOTH.ordinal)
            assertEquals(CarPlayAudioOutput.IPHONE_BLUETOOTH, AirPlayPersistence.loadAudioOutput(app))
            assertTrue(AirPlayPersistence.loadBrowserAudioPlayback(app))
            assertEquals(CarPlayMicrophoneSource.BROWSER, AirPlayPersistence.loadMicrophoneSource(app))
            assertEquals(1000, AirPlayPersistence.loadMediaBufferMillis(app))
            compose.onNodeWithTag("choice:MICROPHONE").assertDoesNotExist()
            compose.onNodeWithTag("choice:AUDIO_BUFFER").assertDoesNotExist()
            compose.onNodeWithTag("toggle:CAR_BLUETOOTH_AUDIO").assertDoesNotExist()
            compose.onNodeWithText("应用并重新连接").assertExists()
            compose.onNodeWithText("当前输出：车机浏览器").assertExists()
            compose.onNodeWithText("iPhone 蓝牙模式的麦克风：由 iPhone 管理").assertDoesNotExist()

            clickTag("choice:AUDIO_OUTPUT")
            chooseOption(CarPlayAudioOutput.ANDROID.ordinal, save = false)
            assertEquals(CarPlayAudioOutput.IPHONE_BLUETOOTH, AirPlayPersistence.loadAudioOutput(app))
            clickTag("choice:AUDIO_OUTPUT")
            chooseOption(CarPlayAudioOutput.BROWSER.ordinal)
            compose.onNodeWithTag("choice:MICROPHONE").assertExists()
            compose.onNodeWithTag("choice:AUDIO_BUFFER").assertExists()
            compose.onNodeWithText("应用并重新连接").assertExists()
            assertEquals(CarPlayMicrophoneSource.BROWSER, AirPlayPersistence.loadMicrophoneSource(app))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun leavingBluetoothKeepsIphoneMicrophoneStatusUntilReconnect() {
        AirPlayPersistence.saveAudioOutput(app, CarPlayAudioOutput.ANDROID)
        val model = SettingsViewModel(app, SavedStateHandle())
        val state = model.state.value.copy(runtime = SettingsRuntime(
            audioOutputActive = CarPlayAudioOutput.IPHONE_BLUETOOTH,
            audioStatus = "当前输出：由 iPhone 管理（蓝牙模式）",
            microphoneStatus = "当前麦克风：由 iPhone 管理",
        ))
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            controller.get().setContent {
                SettingsTheme {
                    Column { AudioSettingsScreen(state, actions { _, _ -> }) }
                }
            }
            compose.onNodeWithTag("choice:AUDIO_OUTPUT").assertTextContains("手机（Android）")
            compose.onNodeWithText("当前麦克风：由 iPhone 管理").assertExists()
            compose.onNodeWithTag("choice:MICROPHONE").assertDoesNotExist()
            compose.onNodeWithTag("choice:AUDIO_BUFFER").assertDoesNotExist()
            compose.onNodeWithText("应用并重新连接").assertExists()
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
