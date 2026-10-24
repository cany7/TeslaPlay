// SPDX-License-Identifier: AGPL-3.0-only
package io.github.cany7.teslaplay.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.cany7.teslaplay.host.R
import io.github.cany7.teslaplay.web.ManagedHttps
import io.github.cany7.teslaplay.web.WebListenSettings
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Composable
internal fun WebSettingsScreen(state: SettingsState, actions: SettingsActions) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var status by remember { mutableStateOf(ManagedHttps.status(context)) }
    var busy by remember { mutableStateOf(ManagedHttps.busy) }
    // A radio button chooses which setup form to show. Only a completed apply/import changes
    // the active certificate; dismissing a picker or revisiting this screen is harmless.
    var selected by remember(status.mode) { mutableStateOf(status.mode) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    val searchTarget = LocalSettingsSearchTarget.current
    LaunchedEffect(searchTarget) {
        if (searchTarget in listOf("text:HTTP_PORT", "text:HTTPS_PORT")) advanced = true
        if (searchTarget == "button:settings_select_pem") selected = WebListenSettings.CertificateType.PEM
        if (searchTarget == "button:settings_select_pkcs12") selected = WebListenSettings.CertificateType.PKCS12
        if (searchTarget == "acme") selected = WebListenSettings.CertificateType.DUCKDNS
    }
    LaunchedEffect(Unit) {
        while (true) {
            status = ManagedHttps.status(context)
            busy = ManagedHttps.busy
            delay(1_000)
        }
    }
    val enabled = !state.runtime.webBusy && !busy
    TeslaSettingsScreen(state, actions)
    SettingsGroup(R.string.settings_https_group) {
        SettingsToggle(state, actions, ToggleSetting.HTTPS, R.string.settings_https,
            R.string.settings_https_description, enabled)
        Text(settingsString(R.string.settings_certificate_type), style = MaterialTheme.typography.titleMedium)
        WebListenSettings.CertificateType.entries.forEach { type ->
            Row(
                Modifier.fillMaxWidth().testTag("certificate:${type.name}")
                    .selectable(selected == type, enabled = enabled, role = Role.RadioButton) {
                        selected = type
                    }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected == type, onClick = null, enabled = enabled)
                Text(settingsString(type.labelRes), Modifier.padding(start = 12.dp))
            }
        }
        SettingsDescription(R.string.settings_certificate_active_mode, settingsString(status.mode.labelRes))
        if (selected != status.mode) SettingsDescription(R.string.settings_certificate_draft)
        if (selected == WebListenSettings.CertificateType.PEM || selected == WebListenSettings.CertificateType.PKCS12)
            SettingsDescription(R.string.settings_manual_certificate_stops_renewal)
        when (selected) {
            WebListenSettings.CertificateType.DEFAULT -> {
                SettingsDescription(R.string.settings_certificate_default)
                SettingsDescription(R.string.settings_apply_local_certificate_description)
                SettingsButton(R.string.settings_apply_local_certificate, enabled) {
                    actions.choice(ChoiceSetting.CERTIFICATE, WebListenSettings.CertificateType.DEFAULT.ordinal)
                }
            }
            WebListenSettings.CertificateType.PEM -> SettingsButton(R.string.settings_select_pem, enabled) {
                actions.command(SettingsCommand.SELECT_PEM)
            }
            WebListenSettings.CertificateType.PKCS12 -> SettingsButton(R.string.settings_select_pkcs12, enabled) {
                actions.command(SettingsCommand.SELECT_PKCS12)
            }
            WebListenSettings.CertificateType.DUCKDNS -> AcmeSettingsGroup(enabled)
        }
        if (selected == status.mode && selected != WebListenSettings.CertificateType.DUCKDNS) {
            SettingsTextPreference(actions, TextSetting.HOSTNAME, R.string.settings_hostname,
                state.hostname, enabled,
                displayValue = state.hostname.ifEmpty { settingsString(R.string.settings_use_ip) })
            SettingsDescription(R.string.settings_manual_domain_description)
        }
        status.installed?.let { certificate ->
            SettingsDescription(R.string.settings_certificate_installed, certificate.subject,
                certificate.issuer, DateFormat.getDateTimeInstance().format(Date(certificate.expiresAt)))
        } ?: SettingsDescription(R.string.settings_certificate_not_installed)
        status.serving?.let { certificate ->
            SettingsDescription(if (certificate.custom) R.string.settings_certificate_serving_custom
                else R.string.settings_certificate_serving_local, certificate.issuer)
            SelectionContainer { Text(settingsString(R.string.settings_certificate_fingerprint, certificate.fingerprint)) }
        } ?: SettingsDescription(R.string.settings_certificate_listener_off)
        val host = WebListenSettings.hostname(context)
        if (host.isNotBlank()) {
            val url = "https://$host:${WebListenSettings.httpsPort(context)}/"
            SelectionContainer { Text(url) }
            SettingsButton(R.string.settings_copy_https_url) { clipboard.setText(AnnotatedString(url)) }
        }
        SettingsDescription(R.string.tesla_acme_origin)
    }
    SettingsButton(if (advanced) R.string.settings_hide_network_advanced else R.string.settings_network_advanced) {
        advanced = !advanced
    }
    if (advanced) SettingsGroup(R.string.settings_web_listeners) {
        SettingsTextPreference(actions, TextSetting.HTTP_PORT, R.string.settings_http_port,
            state.httpPort.toString(), enabled, numeric = true)
        SettingsTextPreference(actions, TextSetting.HTTPS_PORT, R.string.settings_https_port,
            state.httpsPort.toString(), enabled, numeric = true)
        SettingsDescription(R.string.settings_vpn_ports_description)
    }
}
