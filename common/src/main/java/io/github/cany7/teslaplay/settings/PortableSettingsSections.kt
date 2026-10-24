// SPDX-License-Identifier: AGPL-3.0-only
package io.github.cany7.teslaplay.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.cany7.teslaplay.TeslaPlayPreferences
import io.github.cany7.teslaplay.PhoneBluetoothStart
import io.github.cany7.teslaplay.host.R

@Composable
internal fun SettingsBackupSection(actions: SettingsActions) {
    SettingsGroup(R.string.tesla_backup_title) {
        SettingsDescription(R.string.tesla_backup_help)
        SettingsButton(R.string.tesla_backup_export) { actions.command(SettingsCommand.EXPORT_SETTINGS) }
        SettingsButton(R.string.tesla_backup_import) { actions.command(SettingsCommand.IMPORT_SETTINGS) }
    }
}

@Composable
internal fun PhoneBluetoothStartSettings(state: SettingsState, actions: SettingsActions) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(PhoneBluetoothStart.enabled(context)) }
    var resumed by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { resumed++; enabled = PhoneBluetoothStart.enabled(context) }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val canEnable = remember(resumed, state.phoneName) {
        PhoneBluetoothStart.hasPermission(context) && TeslaPlayPreferences.phoneAddress(context) != null
    }
    SettingsGroup(R.string.tesla_phone_start_title) {
        Row(Modifier.fillMaxWidth().settingsSearchTarget("settings:phone-start")
            .toggleable(enabled, enabled = canEnable || enabled, role = Role.Switch) {
                PhoneBluetoothStart.save(context, it)
                enabled = it
            }.padding(vertical = 12.dp)) {
            Text(settingsString(R.string.tesla_phone_start_title), Modifier.weight(1f))
            Switch(enabled, onCheckedChange = null, enabled = canEnable || enabled)
        }
        Text(PhoneBluetoothStart.status(context), style = MaterialTheme.typography.bodyMedium)
        SettingsRow(settingsString(R.string.settings_iphone), state.phoneName, "settings:phone-start-device") {
            actions.command(SettingsCommand.SELECT_PHONE)
        }
        SettingsButton(R.string.settings_app_permissions) { actions.command(SettingsCommand.APP_PERMISSIONS) }
    }
}
