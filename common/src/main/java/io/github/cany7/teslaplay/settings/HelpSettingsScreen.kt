// SPDX-License-Identifier: AGPL-3.0-only
package io.github.cany7.teslaplay.settings

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import io.github.cany7.teslaplay.host.R

@Composable
internal fun HelpSettingsScreen(state: SettingsState, actions: SettingsActions) {
    var fingerprint by remember { mutableStateOf(false) }
    var licenses by remember { mutableStateOf(false) }
    SettingsGroup(R.string.settings_permissions_help) {
        SettingsDescription(R.string.settings_permissions_description)
        SettingsRow(
            settingsString(R.string.settings_fingerprint),
            settingsString(R.string.settings_fingerprint_description),
            "settings:fingerprint",
        ) {
            fingerprint = true
        }
        SettingsButton(R.string.settings_app_permissions) {
            actions.command(SettingsCommand.APP_PERMISSIONS)
        }
        SettingsButton(R.string.settings_bluetooth_settings) {
            actions.command(SettingsCommand.BLUETOOTH_SETTINGS)
        }
        SettingsButton(R.string.settings_wireless_help) {
            actions.command(SettingsCommand.WIRELESS_HELP)
        }
    }
    SettingsGroup(R.string.settings_about_diagnostics) {
        Text(
            "${settingsString(R.string.app_name)} · ${state.runtime.version}",
            style = MaterialTheme.typography.titleMedium,
        )
        SettingsButton(
            if (state.runtime.exporting) R.string.settings_exporting else R.string.settings_export,
            !state.runtime.exporting,
        ) {
            actions.command(SettingsCommand.EXPORT_REPORT)
        }
        SettingsDescription(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) R.string.settings_export_downloads
            else R.string.settings_export_document
        )
        SettingsButton(R.string.settings_clear_diagnostics, !state.runtime.exporting) {
            actions.command(SettingsCommand.CLEAR_DIAGNOSTICS)
        }
        SettingsDescription(R.string.settings_diagnostics_retention)
        SettingsRow(
            settingsString(R.string.settings_license_title),
            settingsString(R.string.settings_license_summary),
            "settings:licenses",
        ) { licenses = true }
    }
    if (licenses) LicenseDialog { licenses = false }
    SettingsBackupSection(actions)
    if (fingerprint)
        AlertDialog(
            onDismissRequest = { fingerprint = false },
            title = { Text(settingsString(R.string.settings_fingerprint)) },
            text = {
                Text(
                    if (!state.checked(ToggleSetting.HTTPS))
                        settingsString(R.string.settings_fingerprint_disabled)
                    else
                        state.runtime.fingerprint?.let {
                            settingsString(R.string.settings_fingerprint_value, it)
                        } ?: settingsString(R.string.settings_fingerprint_pending)
                )
            },
            confirmButton = {
                TextButton(onClick = { fingerprint = false }) {
                    Text(settingsString(R.string.settings_close))
                }
            },
        )
}

@Composable
private fun LicenseDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val links = LocalUriHandler.current
    var document by remember { mutableStateOf<Pair<String, String>?>(null) }
    val text = remember(document) { document?.let { context.assets.open(it.second).bufferedReader().use { reader -> reader.readText() } } }
    val projects = listOf(
        "TeslaPlay" to "https://github.com/cany7/TeslaPlay",
        "WheelPlay" to "https://github.com/fython/wheelplay",
        "DiPlay" to "https://github.com/shihabal3amri/DiPlay",
        "DiPlay-tesla" to "https://github.com/WangZhuo2015/DiPlay-tesla",
        "DashPlay" to "https://github.com/yizhixiaoheigou/DashPlay",
        "teslaplay" to "https://github.io/github/cany7/teslaplay",
        "DiAuto" to "https://github.com/shihabal3amri/DiAuto",
    )
    val documents = listOf(
        "TeslaPlay notices" to "licenses/TeslaPlay-notices.txt",
        "GNU GPL 3.0" to "licenses/GPL-3.0.txt",
        "GNU AGPL 3.0 (DiAuto / inherited UI)" to "licenses/AGPL-3.0.txt",
        "Upstream notices" to "licenses/Upstream-notices.txt",
        "Apache 2.0 (AndroidX / Kotlin)" to "licenses/Apache-2.0.txt",
        "Bouncy Castle" to "licenses/Bouncy-Castle.txt",
        "SLF4J" to "licenses/SLF4J.txt",
        "NanoHTTPD" to "licenses/NanoHTTPD.txt",
        "Quickie" to "licenses/Quickie.txt",
        "Material Icons" to "licenses/Material-Icons.txt",
        "WebRTC" to "webrtc-licenses.txt",
        "VPN Hotspot" to "vpnhotspot-license.txt",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(document?.first ?: settingsString(R.string.settings_license_title)) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                if (text != null) Text(text, style = MaterialTheme.typography.bodySmall)
                else {
                    Text("TeslaPlay · GPL-3.0; inherited AGPL-3.0 components retain their notices.")
                    projects.forEach { (name, url) ->
                        TextButton(onClick = { links.openUri(url) }) { Text(name) }
                    }
                    HorizontalDivider()
                    documents.forEach { item ->
                        TextButton(onClick = { document = item }) { Text(item.first) }
                    }
                }
            }
        },
        dismissButton = { if (document != null) TextButton(onClick = { document = null }) { Text(settingsString(R.string.settings_back)) } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(settingsString(R.string.settings_close)) } },
    )
}
