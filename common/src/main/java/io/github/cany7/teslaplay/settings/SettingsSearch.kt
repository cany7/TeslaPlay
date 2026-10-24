// SPDX-License-Identifier: AGPL-3.0-only
package io.github.cany7.teslaplay.settings

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.cany7.teslaplay.host.R
import java.util.Locale

internal val LocalSettingsSearchTarget = compositionLocalOf<String?> { null }

/** Attached to the actual control; requesting visibility never invokes its setting action. */
@Composable
internal fun Modifier.settingsSearchTarget(tag: String): Modifier {
    val selected = LocalSettingsSearchTarget.current == tag
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(selected) {
        if (selected) {
            withFrameNanos { }
            requester.bringIntoView()
        }
    }
    return this.bringIntoViewRequester(requester).then(
        if (selected) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)) else Modifier
    )
}

internal data class SettingsSearchEntry(
    val category: SettingsCategory,
    val tag: String,
    val title: Int,
    val description: Int = 0,
    val synonyms: String = "",
    val condition: Int = 0,
)

internal object SettingsSearchIndex {
    fun search(context: Context, query: String): List<SettingsSearchEntry> {
        val terms = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return emptyList()
        val contexts = listOf(context) + listOf("en", "zh-CN", "zh-TW").map { tag ->
            context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
            })
        }
        return entries.filter { item ->
            val haystack = contexts.joinToString(" ") {
                it.getString(item.title) + " " + it.getString(item.category.title) + " " +
                    (if (item.description != 0) it.getString(item.description) else "")
            }.lowercase(Locale.ROOT) + " " + item.synonyms.lowercase(Locale.ROOT)
            terms.all { it in haystack }
        }
    }

    val entries = listOf(
        SettingsSearchEntry(SettingsCategory.WEB, "acme", R.string.tesla_acme_title,
            R.string.tesla_acme_description, "duckdns acme https domain certificate 域名 網域 证书 憑證 自动续期 自動續期"),
        SettingsSearchEntry(SettingsCategory.CONNECTION, "toggle:AUTO_CONNECT", R.string.settings_auto_connect, R.string.settings_auto_connect_description, "", 0),
        SettingsSearchEntry(SettingsCategory.CONNECTION, "toggle:AUTO_START", R.string.settings_auto_start, R.string.settings_auto_start_description, "boot 开机 開機 自启动 自動啟動", 0),
        SettingsSearchEntry(SettingsCategory.CONNECTION, "choice:WIRELESS", R.string.settings_network, 0, "", 0),
        SettingsSearchEntry(SettingsCategory.CONNECTION, "text:SSID", R.string.settings_ssid, 0, "", R.string.tesla_search_hotspot_condition),
        SettingsSearchEntry(SettingsCategory.CONNECTION, "text:PASSWORD", R.string.settings_password, 0, "", R.string.tesla_search_hotspot_condition),
        SettingsSearchEntry(SettingsCategory.CONNECTION, "settings:phone", R.string.settings_iphone, 0, "", 0),
        SettingsSearchEntry(SettingsCategory.CONNECTION, "button:settings_open_hotspot", R.string.settings_open_hotspot, 0, "", 0),
        SettingsSearchEntry(SettingsCategory.DISPLAY, "choice:APPEARANCE", R.string.settings_appearance, R.string.settings_appearance_description, "", 0),
        SettingsSearchEntry(SettingsCategory.DISPLAY, "choice:DISPLAY_SIZE", R.string.settings_display_size, R.string.settings_display_size_description, "", 0),
        SettingsSearchEntry(SettingsCategory.DISPLAY, "choice:FPS", R.string.settings_fps, 0, "frame rate 帧率 幀率 60fps", 0),
        SettingsSearchEntry(SettingsCategory.DISPLAY, "choice:STREAM", R.string.settings_stream, R.string.settings_stream_description, "webrtc jpeg video 视频 視訊", 0),
        SettingsSearchEntry(SettingsCategory.DISPLAY, "choice:IMAGE_FORMAT", R.string.settings_image_format, R.string.settings_image_format_description, "", 0),
        SettingsSearchEntry(SettingsCategory.DISPLAY, "text:IMAGE_QUALITY", R.string.settings_image_quality, R.string.settings_image_quality_description, "", 0),
        SettingsSearchEntry(SettingsCategory.DISPLAY, "choice:RECOVERY", R.string.settings_recovery, R.string.settings_recovery_description, "", R.string.settings_recovery_condition),
        SettingsSearchEntry(SettingsCategory.DISPLAY, "toggle:HEVC", R.string.settings_hevc, R.string.settings_hevc_description, "h265 h.265 编码 編碼 codec", 0),
        SettingsSearchEntry(SettingsCategory.DISPLAY, "toggle:RIGHT_HAND", R.string.settings_right_hand, R.string.settings_right_hand_description, "", 0),
        SettingsSearchEntry(SettingsCategory.DISPLAY, "settings:safe-area", R.string.settings_safe_area, 0, "", 0),
        SettingsSearchEntry(SettingsCategory.AUDIO, "choice:AUDIO_BUFFER", R.string.settings_audio_buffer, 0, "latency delay 延迟 延遲", 0),
        SettingsSearchEntry(SettingsCategory.AUDIO, "choice:AUDIO_OUTPUT", R.string.settings_audio_output, R.string.settings_audio_output_description, "sound speaker bluetooth iphone 扬声器 喇叭 聲音 声音 蓝牙 藍牙", 0),
        SettingsSearchEntry(SettingsCategory.AUDIO, "choice:MICROPHONE", R.string.settings_microphone, R.string.settings_microphone_description, "mic siri 麦克风 麥克風 话筒", 0),
        SettingsSearchEntry(SettingsCategory.AUDIO, "button:settings_audio_apply_reconnect", R.string.settings_audio_apply_reconnect, 0, "", 0),
        SettingsSearchEntry(SettingsCategory.VEHICLE, "choice:LOCATION", R.string.settings_location_source, 0, "", 0),
        SettingsSearchEntry(SettingsCategory.BROWSER, "choice:OFFLINE", R.string.settings_offline, R.string.settings_offline_description, "", 0),
        SettingsSearchEntry(SettingsCategory.BROWSER, "settings:devices", R.string.settings_devices, 0, "", 0),
        SettingsSearchEntry(SettingsCategory.WEB, "text:HTTP_PORT", R.string.settings_http_port, 0, "", 0),
        SettingsSearchEntry(SettingsCategory.WEB, "text:HOSTNAME", R.string.settings_hostname, 0, "", 0),
        SettingsSearchEntry(SettingsCategory.WEB, "toggle:HTTPS", R.string.settings_https, R.string.settings_https_description, "", 0),
        SettingsSearchEntry(SettingsCategory.WEB, "text:HTTPS_PORT", R.string.settings_https_port, 0, "", R.string.settings_https_ports_search),
        SettingsSearchEntry(SettingsCategory.WEB, "button:settings_select_pem", R.string.settings_select_pem, 0, "", R.string.tesla_search_https_condition),
        SettingsSearchEntry(SettingsCategory.WEB, "button:settings_select_pkcs12", R.string.settings_select_pkcs12, 0, "", R.string.tesla_search_https_condition),
        SettingsSearchEntry(SettingsCategory.WEB, "choice:TESLA_MODE", R.string.settings_connection_mode, 0, "", 0),
        SettingsSearchEntry(SettingsCategory.WEB, "text:HTTP_MAPPING", R.string.settings_http_mapping, 0, "", R.string.tesla_search_root_condition),
        SettingsSearchEntry(SettingsCategory.WEB, "text:HTTPS_MAPPING", R.string.settings_https_mapping, 0, "", R.string.tesla_search_root_condition),
        SettingsSearchEntry(SettingsCategory.WEB, "button:settings_retry_vpn", R.string.settings_retry_vpn, 0, "", 0),
        SettingsSearchEntry(SettingsCategory.WEB, "button:settings_request_root", R.string.settings_request_root, 0, "", 0),
        SettingsSearchEntry(SettingsCategory.WEB, "button:settings_retry_root", R.string.settings_retry_root, 0, "", 0),
        SettingsSearchEntry(SettingsCategory.WEB, "button:settings_open_hotspot", R.string.settings_open_hotspot, 0, "", 0),
        SettingsSearchEntry(SettingsCategory.HELP, "settings:fingerprint", R.string.settings_fingerprint, 0, "", 0),
        SettingsSearchEntry(SettingsCategory.HELP, "settings:licenses", R.string.settings_license_title, 0, "", 0),
        SettingsSearchEntry(SettingsCategory.HELP, "button:settings_app_permissions", R.string.settings_app_permissions, 0, "", 0),
        SettingsSearchEntry(SettingsCategory.HELP, "button:settings_bluetooth_settings", R.string.settings_bluetooth_settings, 0, "", 0),
        SettingsSearchEntry(SettingsCategory.HELP, "button:settings_wireless_help", R.string.settings_wireless_help, 0, "", 0),
        SettingsSearchEntry(SettingsCategory.HELP, "button:settings_clear_diagnostics", R.string.settings_clear_diagnostics, 0, "", 0),
        SettingsSearchEntry(SettingsCategory.CONNECTION, "settings:phone-start", R.string.tesla_phone_start_title, R.string.tesla_phone_start_help, "bluetooth 蓝牙 藍牙 自动启动 自動啟動", 0),
        SettingsSearchEntry(SettingsCategory.HELP, "button:tesla_backup_export", R.string.tesla_backup_export, R.string.tesla_backup_help, "backup 备份 備份", 0),
        SettingsSearchEntry(SettingsCategory.HELP, "button:tesla_backup_import", R.string.tesla_backup_import, R.string.tesla_backup_help, "restore 恢复 還原", 0),
        SettingsSearchEntry(SettingsCategory.DISPLAY, "settings:tesla-window", io.github.cany7.teslaplay.shared.R.string.tesla_window_title, io.github.cany7.teslaplay.shared.R.string.tesla_window_help, "720p 1080p 2k scale resolution window 窗口 視窗 缩放 縮放 清晰度"),
        SettingsSearchEntry(SettingsCategory.BROWSER, "settings:virtual-buttons", io.github.cany7.teslaplay.shared.R.string.tesla_virtual_buttons, synonyms = "menu buttons 菜单 選單 虚拟按钮 虛擬按鈕"),
    )
}

@Composable
internal fun SettingsSearchResults(results: List<SettingsSearchEntry>, onSelect: (SettingsSearchEntry) -> Unit) {
    if (results.isEmpty()) Text(settingsString(R.string.tesla_search_empty), Modifier.testTag("settings:search-empty"))
    results.forEach { item ->
        SettingsRow(settingsString(item.title), settingsString(item.category.title), "search:${item.tag}") { onSelect(item) }
        if (item.condition != 0) Text(settingsString(item.condition), style = MaterialTheme.typography.bodySmall)
    }
}
