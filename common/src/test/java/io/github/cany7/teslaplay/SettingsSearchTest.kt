package io.github.cany7.teslaplay

import io.github.cany7.teslaplay.settings.SettingsSearchIndex
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SettingsSearchTest {
    private val app get() = RuntimeEnvironment.getApplication()
    @Test fun crossLocaleSynonymsAndNewFeaturesAreIndexedWithoutMutatingPreferences() {
        val prefs = app.getSharedPreferences("teslaplay_airplay", 0)
        prefs.edit().putBoolean("hevc_enabled", true).putInt("display_fps", 60).commit()
        val before = prefs.all
        for (query in listOf("HEVC", "h265", "帧率", "幀率", "frame rate", "bluetooth", "蓝牙", "藍牙", "720p", "backup", "恢复", "還原"))
            assertTrue(query, SettingsSearchIndex.search(app, query).isNotEmpty())
        assertEquals(before, prefs.all)
        assertTrue(SettingsSearchIndex.search(app, " ").isEmpty())
        assertTrue(SettingsSearchIndex.search(app, "not-a-setting-123456").isEmpty())
    }
    @Test fun conditionalFieldsRemainSearchableAndExplainPrerequisites() {
        val hidden = SettingsSearchIndex.entries.filter { it.tag in setOf("text:PASSWORD", "text:HTTPS_PORT", "text:HTTP_MAPPING") }
        assertEquals(3, hidden.size)
        assertTrue(hidden.all { it.condition != 0 })
        assertTrue(SettingsSearchIndex.entries.any { it.tag == "settings:tesla-window" })
        assertTrue(SettingsSearchIndex.entries.any {
            it.tag == "settings:virtual-buttons" && it.category == io.github.cany7.teslaplay.settings.SettingsCategory.BROWSER
        })
        assertTrue(SettingsSearchIndex.entries.none { it.tag in setOf("toggle:ADAPTIVE_SIZE", "choice:RESOLUTION", "toggle:NAVIGATION") })
        assertTrue(SettingsSearchIndex.search(app, "自适应浏览器尺寸").isEmpty())
        assertTrue(SettingsSearchIndex.search(app, "OEM").isEmpty())
    }
}
