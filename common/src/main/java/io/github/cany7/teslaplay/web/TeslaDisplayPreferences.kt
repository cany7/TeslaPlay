// SPDX-License-Identifier: GPL-3.0-only
package io.github.cany7.teslaplay.web

import android.content.Context
import org.json.JSONObject
import kotlin.math.floor

/** Two saved browser layouts. Pixel policy follows DiPlay-tesla display-presets.js;
 * transport, codec and frame-rate preferences remain owned by WheelPlay. */
internal object TeslaDisplayPreferences {
    const val KEY = "tesla_display_profiles_json"
    const val BUTTONS_KEY = "tesla_virtual_buttons"
    fun preferences(context: Context) = context.getSharedPreferences("teslaplay_airplay", Context.MODE_PRIVATE)
    data class Profile(val width: Int = 1280, val height: Int = 720, val preset: String = "fit", val scale: Double = 1.0) {
        fun json() = JSONObject().put("width", width).put("height", height).put("preset", preset).put("scale", scale)
        fun output(): Pair<Int, Int> {
            val cap = when (preset) { "720p" -> 1280 to 720; "1080p" -> 1920 to 1080; "2k" -> 2560 to 1440; else -> 3840 to 2160 }
            val fit = if (preset == "fit") minOf(1.0, cap.first.toDouble() / width, cap.second.toDouble() / height)
                else minOf(cap.first.toDouble() / width, cap.second.toDouble() / height)
            val factor = maxOf(fit * scale, 320.0 / width, 320.0 / height)
            fun even(value: Double) = (floor(value / 2).toInt() * 2).coerceAtLeast(320)
            return even(width * factor) to even(height * factor)
        }
    }
    data class Config(val active: String = "right", val right: Profile = Profile(), val large: Profile = Profile(), val enabled: Boolean = false) {
        fun profile() = if (active == "right") right else large
        fun json() = JSONObject().put("active", active).put("right", right.json()).put("large", large.json()).put("enabled", enabled)
    }
    fun validSize(width: Int, height: Int) = width in 320..8192 && height in 320..8192 &&
        width % 2 == 0 && height % 2 == 0 && width.toDouble() / height in 0.5..4.0
    private fun parseProfile(obj: JSONObject): Profile {
        require(obj.opt("width") is Int && obj.opt("height") is Int && obj.opt("preset") is String && obj.opt("scale") is Number)
        val p = Profile(obj.getInt("width"), obj.getInt("height"), obj.getString("preset"), obj.getDouble("scale"))
        require(validSize(p.width, p.height) && p.preset in listOf("fit", "720p", "1080p", "2k") && p.scale in listOf(.5, .75, 1.0))
        return p
    }
    fun parse(raw: String): Config {
        val obj = JSONObject(raw)
        require(obj.opt("active") is String && obj.opt("enabled") is Boolean)
        val active = obj.getString("active")
        require(active in listOf("right", "large"))
        return Config(active, parseProfile(obj.getJSONObject("right")), parseProfile(obj.getJSONObject("large")), obj.getBoolean("enabled"))
    }
    fun validateJson(raw: String) = runCatching { parse(raw) }.isSuccess
    fun load(context: Context) = runCatching { parse(preferences(context).getString(KEY, null)!!) }.getOrDefault(Config())
    @Synchronized fun save(context: Context, config: Config): Boolean {
        val old = load(context)
        if (old == config) return false
        parse(config.json().toString())
        preferences(context).edit().putString(KEY, config.json().toString()).apply()
        WebSession.stageSavedDisplay(config)
        return true
    }
    fun buttons(context: Context) = preferences(context).getBoolean(BUTTONS_KEY, false)
    fun saveButtons(context: Context, show: Boolean) = preferences(context).edit().putBoolean(BUTTONS_KEY, show).apply()
    fun pending(config: Config) = config.enabled && config.profile().output() != WebSession.appliedBrowserViewport
    fun status(context: Context): JSONObject {
        val saved = load(context)
        val output = saved.profile().output()
        return saved.json().put("buttons", buttons(context)).put("buttonsSet", preferences(context).contains(BUTTONS_KEY))
            .put("outputWidth", output.first).put("outputHeight", output.second)
            .put("pending", pending(saved))
    }
}
