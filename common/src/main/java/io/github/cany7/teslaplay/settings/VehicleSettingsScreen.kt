// SPDX-License-Identifier: AGPL-3.0-only
package io.github.cany7.teslaplay.settings

import androidx.compose.runtime.Composable
import io.github.cany7.teslaplay.host.R

@Composable
internal fun VehicleSettingsScreen(state: SettingsState, actions: SettingsActions) {
    SettingsChoice(
        state, actions, ChoiceSetting.LOCATION, R.string.settings_location_source,
        listOf(settingsString(R.string.settings_location_iphone), settingsString(R.string.settings_location_android)),
    )
    SettingsDescription(R.string.settings_location_description)
}
