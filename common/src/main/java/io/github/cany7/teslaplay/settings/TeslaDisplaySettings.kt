// SPDX-License-Identifier: AGPL-3.0-only
package io.github.cany7.teslaplay.settings

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.delay
import io.github.cany7.teslaplay.web.TeslaDisplayPreferences
import io.github.cany7.teslaplay.web.WebSession
import io.github.cany7.teslaplay.shared.R as UiR

@Composable
internal fun TeslaDisplaySettings() {
    val context = LocalContext.current
    val prefs = remember(context) { TeslaDisplayPreferences.preferences(context) }
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == TeslaDisplayPreferences.KEY) revision++
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val saved = remember(revision) { TeslaDisplayPreferences.load(context) }
    var live by remember { mutableStateOf(WebSession.mediaSnapshot()) }
    var viewport by remember { mutableStateOf(WebSession.reportedBrowserViewport) }
    LaunchedEffect(Unit) { while (true) {
        live = WebSession.mediaSnapshot()
        viewport = WebSession.reportedBrowserViewport
        delay(1000)
    } }
    var mode by remember(saved) { mutableStateOf(saved.active) }
    var editing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().settingsSearchTarget("settings:tesla-window")) {
        SettingsRow(settingsString(UiR.string.tesla_window_title),
            if (saved.enabled) "${saved.profile().output().first} × ${saved.profile().output().second}" else settingsString(UiR.string.tesla_auto), "settings:tesla-window-row") { editing = true }
        SettingsDescription(UiR.string.tesla_window_help)
    }
    if (!editing) return
    var preset by remember(mode, saved) { mutableStateOf((if (mode == "right") saved.right else saved.large).preset) }
    var scale by remember(mode, saved) { mutableDoubleStateOf((if (mode == "right") saved.right else saved.large).scale) }
    var width by remember(mode, saved) { mutableStateOf((if (mode == "right") saved.right else saved.large).width.toString()) }
    var height by remember(mode, saved) { mutableStateOf((if (mode == "right") saved.right else saved.large).height.toString()) }
    val w = width.toIntOrNull(); val h = height.toIntOrNull()
    val valid = w != null && h != null && TeslaDisplayPreferences.validSize(w, h)
    val draft = if (valid) TeslaDisplayPreferences.Profile(w!!, h!!, preset, scale) else null
    val baseline = if (mode == "right") saved.right else saved.large
    val dirty = draft != baseline || mode != saved.active
    var custom by remember { mutableStateOf(false) }
    fun save(apply: Boolean) {
        val profile = draft ?: return
        val latest = TeslaDisplayPreferences.load(context)
        TeslaDisplayPreferences.save(context, if (mode == "right") latest.copy(active = mode, right = profile, enabled = true)
            else latest.copy(active = mode, large = profile, enabled = true))
        if (apply) WebSession.applySavedDisplay(context)
        editing = false
    }
    AlertDialog(onDismissRequest = { editing = false; mode = saved.active }, title = { Text(settingsString(UiR.string.tesla_window_title)) }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            DisplayChoice(settingsString(UiR.string.tesla_window_mode), mode,
                listOf("right" to settingsString(UiR.string.tesla_right_window), "large" to settingsString(UiR.string.tesla_large_window))) { mode = it }
            DisplayChoice(settingsString(UiR.string.tesla_preset), preset,
                listOf("fit" to settingsString(UiR.string.tesla_fit), "720p" to "720p", "1080p" to "1080p", "2k" to "2K (2560 × 1440)")) { preset = it }
            TextButton(onClick = { custom = !custom }) { Text(settingsString(UiR.string.tesla_custom_size)) }
            if (custom) {
            Text(settingsString(UiR.string.tesla_size_help))
            DisplayChoice(settingsString(UiR.string.tesla_scale), scale.toString(), listOf("0.5" to "1/2", "0.75" to "3/4", "1.0" to "1×")) { scale = it.toDouble() }
            OutlinedTextField(width, { width = it }, label = { Text(settingsString(UiR.string.tesla_width)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(height, { height = it }, label = { Text(settingsString(UiR.string.tesla_height)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            TextButton(enabled = viewport != null, onClick = {
                viewport?.let { width = it.first.toString(); height = it.second.toString() }
            }) { Text(settingsString(UiR.string.tesla_use_viewport)) }
            }
            Text(draft?.output()?.let { "${it.first} × ${it.second}" } ?: settingsString(UiR.string.tesla_invalid_size))
            Text(settingsString(when {
                dirty -> UiR.string.tesla_unsaved
                !saved.enabled -> UiR.string.tesla_auto
                live.sourceState != "ready" || TeslaDisplayPreferences.pending(saved) -> UiR.string.tesla_pending
                else -> UiR.string.tesla_in_use
            }))
            Text("${settingsString(UiR.string.tesla_current)}: " +
                if (live.sourceState == "ready") "${live.width} × ${live.height}" else settingsString(UiR.string.tesla_disconnected))
            TextButton(enabled = valid, onClick = { save(true) }) { Text(settingsString(UiR.string.tesla_apply)) }
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = { save(false) }) { Text(settingsString(UiR.string.tesla_save)) } },
        dismissButton = { TextButton(onClick = { editing = false; mode = saved.active }) { Text(settingsString(UiR.string.tesla_cancel)) } })
}

@Composable
private fun DisplayChoice(title: String, value: String, options: List<Pair<String, String>>, change: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text("$title · ${options.first { it.first == value }.second}") }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { (key, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { expanded = false; change(key) }) }
        }
    }
}
