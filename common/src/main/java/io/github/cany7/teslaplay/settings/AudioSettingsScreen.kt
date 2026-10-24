// SPDX-License-Identifier: AGPL-3.0-only
package io.github.cany7.teslaplay.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.cany7.teslaplay.*
import io.github.cany7.teslaplay.host.R

@Composable
internal fun AudioSettingsScreen(state: SettingsState, actions: SettingsActions) {
    val selectedOutput = CarPlayAudioOutput.entries[state.selected(ChoiceSetting.AUDIO_OUTPUT)]
    val activeOutput = state.runtime.audioOutputActive
    val iphoneAudio = selectedOutput == CarPlayAudioOutput.IPHONE_BLUETOOTH
    SettingsChoice(
        state,
        actions,
        ChoiceSetting.AUDIO_OUTPUT,
        R.string.settings_audio_output,
        CarPlayAudioOutput.entries.map { settingsString(it.labelRes) },
    )
    Text(state.runtime.audioStatus, style = MaterialTheme.typography.bodyMedium)
    SettingsDescription(R.string.settings_audio_output_description)
    if (activeOutput != null && activeOutput != selectedOutput) {
        SettingsDescription(R.string.settings_audio_mode_pending)
    }
    if (iphoneAudio) {
        SettingsDescription(R.string.settings_car_bluetooth_audio_description)
        Text(state.runtime.microphoneStatus, style = MaterialTheme.typography.bodyMedium)
    } else if (activeOutput == CarPlayAudioOutput.IPHONE_BLUETOOTH) {
        // The saved microphone and buffer preferences resume after the audio-mode reconnect.
        Text(state.runtime.microphoneStatus, style = MaterialTheme.typography.bodyMedium)
    } else {
        SettingsChoice(
            state,
            actions,
            ChoiceSetting.AUDIO_BUFFER,
            R.string.settings_audio_buffer,
            listOf(
                settingsString(R.string.settings_buffer_default),
                "500 ms",
                settingsString(R.string.settings_buffer_stable),
            ),
        )
        SettingsDescription(R.string.settings_audio_buffer_description)
        SettingsChoice(
            state,
            actions,
            ChoiceSetting.MICROPHONE,
            R.string.settings_microphone,
            CarPlayMicrophoneSource.entries.map { settingsString(it.labelRes) },
        )
        Text(state.runtime.microphoneStatus, style = MaterialTheme.typography.bodyMedium)
        SettingsDescription(R.string.settings_microphone_description)
    }
    SettingsDescription(R.string.settings_saved_apply_help)
    SettingsButton(R.string.settings_audio_apply_reconnect, state.runtime.audioOutputActive != null) { actions.command(SettingsCommand.APPLY_SAVED_SETTINGS) }
}
