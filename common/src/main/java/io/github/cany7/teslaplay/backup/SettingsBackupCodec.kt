// SPDX-License-Identifier: AGPL-3.0-only
package io.github.cany7.teslaplay.backup

import android.content.Context
import android.content.SharedPreferences
import io.github.cany7.teslaplay.CarPlaySafeMargins
import io.github.cany7.teslaplay.TeslaPlayPreferences
import io.github.cany7.teslaplay.web.TeslaDisplayPreferences
import org.json.JSONObject

/** Adapted from DiPlay SettingsBackupCodec (4487c689); explicit typed allowlist, no credentials. */
object SettingsBackupCodec {
    const val FORMAT = "teslaplay-settings"
    const val VERSION = 1
    const val MAX_BYTES = 128 * 1024
    private data class Rule(val default: Any, val valid: (Any) -> Boolean)
    private fun bool(value: Boolean = false) = Rule(value) { it is Boolean }
    private fun int(value: Int, range: IntRange) = Rule(value) { it is Int && it in range }
    private fun choice(value: String, vararg values: String) = Rule(value) { it is String && it in values }
    private val policies = mapOf(
        "teslaplay_airplay" to linkedMapOf(
            "ui_scale_percent" to Rule(100) { it in listOf(75, 85, 100, 115) },
            "hevc_enabled" to bool(true),
            "hevc_software_decoder" to bool(),
            "advanced_audio_channel_mapping" to bool(),
            "wireless_enabled" to bool(true),
            "wireless_hotspot_mode" to choice("MANUAL", "WIFI_P2P", "MANUAL"),
            "display_fps" to Rule(60) { it is Int && it in 30..60 && it % 5 == 0 },
            "web_stream_technology" to choice("WEBRTC", "WEBRTC", "JPEG"),
            "web_rtc_recovery" to choice("BALANCED", "QUICK", "BALANCED", "PATIENT"),
            "web_image_format" to choice("WEBP", "JPEG", "WEBP"),
            "web_image_quality" to int(100, 1..100),
            "media_buffer_ms" to Rule(300) { it in listOf(300, 500, 1000) },
            "display_width_physical_mm" to Rule(300) { it is Int && it in 100..400 && it % 50 == 0 },
            "display_physical_size_basis" to choice("WIDTH", "WIDTH", "HEIGHT"),
            "right_hand_drive" to bool(),
            "hide_top_bar" to bool(true),
            "hide_bottom_bar" to bool(true),
            "browser_audio_playback" to bool(),
            "car_bluetooth_audio" to bool(),
            "microphone_source" to choice("ANDROID", "ANDROID", "BROWSER", "DISABLED"),
            "carplay_appearance" to choice("SYSTEM", "SYSTEM", "DAY", "NIGHT"),
            "browser_offline_policy" to choice("KEEP", "KEEP", "ONE_MINUTE", "FIVE_MINUTES"),
            "carplay_safe_margins" to Rule("0,0,0,0") { it is String && CarPlaySafeMargins.decode(it) != null },
            "auto_start_on_boot" to bool(),
            "auto_connect" to bool(),
            "location_reporting_enabled" to bool(false),
            "tesla_virtual_buttons" to bool(),
            "tesla_display_profiles_json" to Rule(TeslaDisplayPreferences.Config().json().toString()) { it is String && TeslaDisplayPreferences.validateJson(it) },
        ),
    )

    data class Change(val file: String, val key: String, val before: Any?, val after: Any)
    class Preview internal constructor(internal val changes: List<Change>, val skipped: Int) {
        val count get() = changes.size
        fun summary() = changes.joinToString("\n") { "${it.key}: ${it.before ?: "—"} → ${it.after}" }
    }

    fun export(context: Context): ByteArray {
        val files = JSONObject()
        policies.forEach { (file, rules) ->
            val current = context.getSharedPreferences(file, 0).all
            files.put(file, JSONObject().also { json ->
                rules.forEach { (key, rule) ->
                    val value = current[key] ?: effectiveDefault(context, key, rule)
                    check(rule.valid(value)) { "Invalid stored setting: $key" }
                    json.put(key, value)
                }
            })
        }
        return JSONObject().put("format", FORMAT).put("version", VERSION)
            .put("settings", files).toString(2).toByteArray(Charsets.UTF_8)
    }

    /** Entire input is validated before any preference is changed. Unknown keys are skipped. */
    fun preview(context: Context, document: ByteArray): Preview {
        require(document.size <= MAX_BYTES) { "Backup exceeds 128 KiB" }
        val root = JSONObject(document.toString(Charsets.UTF_8))
        require(root.opt("format") == FORMAT) { "Unsupported backup format" }
        require(root.opt("version") is Int && root.getInt("version") == VERSION) { "Unsupported backup version" }
        val files = root.getJSONObject("settings")
        val changes = mutableListOf<Change>()
        var skipped = root.keys().asSequence().count { it !in setOf("format", "version", "settings") }
        files.keys().forEach { sourceFile ->
            // Backups from the previous application ID used this preference filename.
            val file = if (sourceFile == "xcertplay_airplay") "teslaplay_airplay" else sourceFile
            if (sourceFile != file && files.has(file)) return@forEach
            val rules = policies[file]
            if (rules == null) { skipped++; return@forEach }
            val values = files.getJSONObject(sourceFile)
            val current = context.getSharedPreferences(file, 0).all
            values.keys().forEach keys@{ key ->
                val rule = rules[key] ?: run { skipped++; return@keys }
                val value = values.get(key)
                require(rule.valid(value)) { "Invalid setting: $key" }
                val before = current[key] ?: effectiveDefault(context, key, rule)
                if (before != value) changes += Change(file, key, before, value)
            }
        }
        return Preview(changes, skipped)
    }

    private fun effectiveDefault(context: Context, key: String, rule: Rule): Any = when (key) {
        "microphone_source" -> io.github.cany7.teslaplay.AirPlayPersistence.loadMicrophoneSource(context).name
        "auto_connect" -> TeslaPlayPreferences.autoConnect(context)
        else -> rule.default
    }

    /** All portable settings live in one file, committed atomically after complete validation. */
    @Synchronized fun apply(context: Context, preview: Preview) {
        val groups = preview.changes.groupBy { it.file }
        val originals = groups.mapValues { (file, changes) ->
            val current = context.getSharedPreferences(file, 0).all
            changes.associate { it.key to current[it.key] }
        }
        try {
            groups.forEach { (file, changes) ->
                val editor = context.getSharedPreferences(file, 0).edit()
                changes.forEach { put(editor, it.key, it.after) }
                check(editor.commit()) { "Could not save settings" }
            }
        } catch (error: Exception) {
            originals.forEach { (file, values) ->
                val editor = context.getSharedPreferences(file, 0).edit()
                values.forEach { (key, value) -> if (value == null) editor.remove(key) else put(editor, key, value) }
                editor.commit()
            }
            throw error
        }
    }

    private fun put(editor: SharedPreferences.Editor, key: String, value: Any) {
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is String -> editor.putString(key, value)
            else -> error("Unsupported preference type")
        }
    }
}
