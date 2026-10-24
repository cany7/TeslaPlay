// SPDX-License-Identifier: AGPL-3.0-only
package io.github.cany7.teslaplay

import android.content.Context

/** One-time upgrade of the former defaults; later user changes remain untouched. */
internal object TeslaDefaults {
    @Synchronized fun migrate(context: Context) {
        val prefs = context.getSharedPreferences("teslaplay_airplay", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("teslaplay_display_v2", false)) {
            prefs.edit().remove("display_scale_tenths").remove("adaptive_browser_size")
                .putBoolean("teslaplay_display_v2", true).commit()
        }
        if (prefs.getBoolean("teslaplay_defaults_v1", false)) return
        val edit = prefs.edit()
        if (!prefs.getBoolean("hevc_enabled", false)) edit.putBoolean("hevc_enabled", true)
        if (prefs.getInt("display_fps", 30) == 30) edit.putInt("display_fps", 60)
        if (prefs.getInt("web_image_quality", 75) == 75) edit.putInt("web_image_quality", 100)
        if (prefs.getString("wireless_hotspot_mode", "WIFI_P2P") == "WIFI_P2P") {
            edit.putString("wireless_hotspot_mode", "MANUAL")
        }
        edit.putBoolean("teslaplay_defaults_v1", true).commit()
    }
}
