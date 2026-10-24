// SPDX-License-Identifier: AGPL-3.0-only
package io.github.cany7.teslaplay.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import io.github.cany7.teslaplay.host.R
import io.github.cany7.teslaplay.web.TeslaHttpCompatibility
import io.github.cany7.teslaplay.web.ManagedHttps
import io.github.cany7.teslaplay.web.WebListenSettings
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun AcmeSettingsGroup(enabled: Boolean = true) {
    val context = LocalContext.current
    var saved by remember { mutableStateOf(ManagedHttps.config(context)) }
    var domain by remember { mutableStateOf(saved?.domain ?: WebListenSettings.hostname(context)) }
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(ManagedHttps.busy) }
    var status by remember { mutableStateOf(ManagedHttps.state(context)) }
    var next by remember { mutableLongStateOf(ManagedHttps.nextAttempt(context)) }
    var message by remember { mutableStateOf<String?>(null) }
    var accepted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            busy = ManagedHttps.busy
            status = ManagedHttps.state(context)
            saved = ManagedHttps.config(context)
            next = ManagedHttps.nextAttempt(context)
            delay(1_000)
        }
    }
    SettingsDescription(R.string.tesla_acme_description)
    OutlinedTextField(domain, { domain = it }, label = { Text(settingsString(R.string.tesla_acme_domain)) },
        supportingText = { Text(settingsString(R.string.tesla_acme_domain_hint)) },
        enabled = enabled && !busy, singleLine = true, modifier = Modifier.fillMaxWidth().settingsSearchTarget("acme"))
    OutlinedTextField(token, { token = it }, label = { Text(settingsString(R.string.tesla_acme_token)) },
        supportingText = { Text(settingsString(R.string.tesla_acme_token_hint)) },
        visualTransformation = PasswordVisualTransformation(), enabled = enabled && !busy,
        singleLine = true, modifier = Modifier.fillMaxWidth())
    SettingsDescription(R.string.tesla_acme_fixed_address, TeslaHttpCompatibility.config(context).accessAddress)
    Row(Modifier.fillMaxWidth().toggleable(value = accepted, enabled = enabled && !busy,
        role = Role.Checkbox, onValueChange = { accepted = it }), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(accepted, onCheckedChange = null, enabled = enabled && !busy)
        Text(settingsString(R.string.tesla_acme_terms), Modifier.weight(1f))
    }
    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://letsencrypt.org/repository/"))) }) {
        Text(settingsString(R.string.tesla_acme_terms_view))
    }
    Button(enabled = enabled && !busy && accepted, onClick = {
        val name = domain.trim().lowercase(Locale.ROOT)
        val secret = token.trim().ifBlank { saved?.token.orEmpty() }
        message = when {
            !Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.duckdns\\.org").matches(name) ->
                context.getString(R.string.tesla_acme_invalid_domain)
            !Regex("[A-Za-z0-9-]{16,64}").matches(secret) ->
                context.getString(R.string.tesla_acme_invalid_token)
            else -> runCatching {
                ManagedHttps.configure(context, name, token.trim(), TeslaHttpCompatibility.config(context).accessAddress)
                domain = name
                token = ""
                saved = ManagedHttps.config(context)
                check(ManagedHttps.request(context, force = true))
                busy = ManagedHttps.busy
                context.getString(R.string.tesla_acme_started)
            }.getOrElse { context.getString(R.string.tesla_acme_save_failed) }
        }
    }) { Text(settingsString(R.string.tesla_acme_request)) }
    if (saved?.enabled == true) TextButton(enabled = enabled && !busy, onClick = {
        message = runCatching {
            ManagedHttps.disable(context)
            saved = ManagedHttps.config(context)
            context.getString(R.string.tesla_acme_off)
        }.getOrElse { context.getString(R.string.tesla_acme_save_failed) }
    }) { Text(settingsString(R.string.tesla_acme_disable)) }
    Text(settingsString(if (saved?.enabled == true) R.string.tesla_acme_renewal_on else R.string.tesla_acme_off))
    Text(settingsString(when {
        busy -> R.string.tesla_acme_running
        status == "ready" -> R.string.tesla_acme_ready
        status == "OFFLINE" || status == "NETWORK" -> R.string.tesla_acme_network
        status == "DNS_UPDATE" -> R.string.tesla_acme_dns_failed
        status == "RATE_LIMIT" -> R.string.tesla_acme_rate_limit
        status == "CERTIFICATE" || status == "VALIDATION" -> R.string.tesla_acme_validation_failed
        status == "install_failed" -> R.string.tesla_acme_install_failed
        status == "SERVER" || status == "PROTOCOL" || status == "TIMEOUT" -> R.string.tesla_acme_issue_failed
        status == "idle" -> R.string.tesla_acme_idle
        else -> R.string.tesla_acme_failed
    }))
    if (next > 0 && saved?.enabled == true) Text(settingsString(R.string.tesla_acme_next, DateFormat.getDateTimeInstance().format(Date(next))))
    if (!WebListenSettings.httpsEnabled(context)) SettingsDescription(R.string.tesla_acme_enable_https)
    SettingsDescription(R.string.tesla_acme_reachability)
    message?.let { Text(it) }
}
