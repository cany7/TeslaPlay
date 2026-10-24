// SPDX-License-Identifier: AGPL-3.0-only
package io.github.cany7.teslaplay.settings

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.cany7.teslaplay.web.TeslaDisplayPreferences
import io.github.cany7.teslaplay.shared.R as UiR
import androidx.compose.ui.unit.dp
import io.github.cany7.teslaplay.BrowserOfflinePolicy
import io.github.cany7.teslaplay.host.R
import java.text.DateFormat
import java.util.Date

@Composable
internal fun BrowserSettingsScreen(state: SettingsState, actions: SettingsActions) {
    BrowserVirtualButtonsSetting()
    SettingsDescription(UiR.string.tesla_virtual_buttons_description)
    var manage by remember { mutableStateOf(false) }
    SettingsDescription(R.string.settings_browser_description)
    SettingsRow(
        settingsString(R.string.settings_devices),
        settingsString(R.string.settings_devices_description),
        "settings:devices",
    ) {
        manage = true
    }
    SettingsChoice(
        state,
        actions,
        ChoiceSetting.OFFLINE,
        R.string.settings_offline,
        BrowserOfflinePolicy.entries.map { settingsString(it.labelRes) },
    )
    SettingsDescription(R.string.settings_offline_description)
    if (manage) BrowserDevicesDialog(state, actions) { manage = false }
}

@Composable
private fun BrowserDevicesDialog(
    state: SettingsState,
    actions: SettingsActions,
    onDismiss: () -> Unit,
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var rename by remember { mutableStateOf(false) }
    var removeAll by remember { mutableStateOf(false) }
    val selected = state.browsers.firstOrNull { it.id == selectedId }
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    if (selected == null && !removeAll)
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(settingsString(R.string.settings_browser_devices)) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    if (state.browsers.isEmpty())
                        SettingsDescription(R.string.settings_devices_empty)
                    state.browsers.forEach { device ->
                        SettingsRow(
                            device.name,
                            dateFormat.format(Date(device.lastUsedAt)),
                            "browser:${device.id}",
                        ) {
                            selectedId = device.id
                        }
                    }
                }
            },
            confirmButton = {
                if (state.browsers.isNotEmpty())
                    TextButton(onClick = { removeAll = true }) {
                        Text(settingsString(R.string.settings_remove_all))
                    }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(settingsString(R.string.settings_close)) }
            },
        )
    if (selected != null) {
        if (rename)
            SettingsTextDialog(
                settingsString(R.string.settings_browser_name),
                selected.name,
                onDismiss = { rename = false },
            ) {
                actions.renameBrowser(selected.id, it)
                selectedId = null
                null
            }
        else
            AlertDialog(
                onDismissRequest = { selectedId = null },
                title = { Text(selected.name) },
                text = {
                    Text(
                        settingsString(
                            R.string.settings_device_details,
                            dateFormat.format(Date(selected.createdAt)),
                            dateFormat.format(Date(selected.lastUsedAt)),
                            selected.peer,
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            actions.removeBrowser(selected.id)
                            selectedId = null
                        }
                    ) {
                        Text(settingsString(R.string.settings_remove_device))
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { rename = true }) {
                            Text(settingsString(R.string.settings_rename))
                        }
                        TextButton(onClick = { selectedId = null }) {
                            Text(settingsString(R.string.settings_cancel))
                        }
                    }
                },
            )
    }
    if (removeAll)
        SettingsConfirmation(
            R.string.settings_remove_all_title,
            R.string.settings_remove_all_message,
            { removeAll = false },
        ) {
            actions.removeBrowser(null)
        }
}

@Composable
private fun BrowserVirtualButtonsSetting() {
    val context = LocalContext.current
    val prefs = remember(context) { TeslaDisplayPreferences.preferences(context) }
    var show by remember { mutableStateOf(TeslaDisplayPreferences.buttons(context)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == TeslaDisplayPreferences.BUTTONS_KEY) show = TeslaDisplayPreferences.buttons(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    Row(Modifier.fillMaxWidth().settingsSearchTarget("settings:virtual-buttons")) {
        Text(settingsString(UiR.string.tesla_virtual_buttons), Modifier.weight(1f))
        Switch(checked = show, onCheckedChange = { TeslaDisplayPreferences.saveButtons(context, it) })
    }
}
