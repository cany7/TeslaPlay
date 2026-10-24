package io.github.cany7.teslaplay

import io.github.cany7.teslaplay.backup.SettingsBackupCodec
import io.github.cany7.teslaplay.orchestration.WirelessHotspotMode
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class TeslaDefaultsTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val prefs get() = app.getSharedPreferences("teslaplay_airplay", 0)

    @Test fun upgradeChangesOldDefaultsOnceAndKeepsLaterChoices() {
        prefs.edit().clear().putBoolean("hevc_enabled", false).putInt("display_fps", 30)
            .putInt("web_image_quality", 75).putString("wireless_hotspot_mode", "WIFI_P2P").commit()
        TeslaDefaults.migrate(app)
        assertTrue(AirPlayPersistence.loadHevcEnabled(app))
        assertEquals(60, AirPlayPersistence.loadFps(app))
        assertEquals(100, AirPlayPersistence.loadImageQuality(app))
        assertEquals(WirelessHotspotMode.MANUAL, AirPlayPersistence.loadWirelessHotspotMode(app))
        AirPlayPersistence.saveHevcEnabled(app, false)
        AirPlayPersistence.saveFps(app, 30)
        AirPlayPersistence.saveImageQuality(app, 75)
        AirPlayPersistence.saveWirelessHotspotMode(app, WirelessHotspotMode.WIFI_P2P)
        TeslaDefaults.migrate(app)
        assertFalse(AirPlayPersistence.loadHevcEnabled(app))
        assertEquals(30, AirPlayPersistence.loadFps(app))
        assertEquals(75, AirPlayPersistence.loadImageQuality(app))
        assertEquals(WirelessHotspotMode.WIFI_P2P, AirPlayPersistence.loadWirelessHotspotMode(app))
    }

    @Test fun newInstallAndBackupAgreeAndCustomQualityIsPreservedOnUpgrade() {
        prefs.edit().clear().commit()
        val backup = JSONObject(SettingsBackupCodec.export(app).toString(Charsets.UTF_8))
            .getJSONObject("settings").getJSONObject("teslaplay_airplay")
        assertEquals(AirPlayPersistence.loadHevcEnabled(app), backup.getBoolean("hevc_enabled"))
        assertEquals(AirPlayPersistence.loadFps(app), backup.getInt("display_fps"))
        assertEquals(AirPlayPersistence.loadImageQuality(app), backup.getInt("web_image_quality"))
        assertEquals(AirPlayPersistence.loadWirelessHotspotMode(app).name, backup.getString("wireless_hotspot_mode"))
        AirPlayPersistence.saveImageQuality(app, 90)
        AirPlayPersistence.saveFps(app, 45)
        TeslaDefaults.migrate(app)
        assertEquals(90, AirPlayPersistence.loadImageQuality(app))
        assertEquals(45, AirPlayPersistence.loadFps(app))
    }
    @Test fun retiredDisplayControlsAreRemovedWithoutChangingWindowProfilesOrCustomQuality() {
        val profile = io.github.cany7.teslaplay.web.TeslaDisplayPreferences.Config(
            right = io.github.cany7.teslaplay.web.TeslaDisplayPreferences.Profile(1600, 1000, "fit", .75), enabled = true)
        prefs.edit().clear().putBoolean("teslaplay_defaults_v1", true)
            .putInt("display_scale_tenths", 6).putBoolean("adaptive_browser_size", false)
            .putString("tesla_display_profiles_json", profile.json().toString())
            .putInt("web_image_quality", 90).commit()
        TeslaDefaults.migrate(app)
        assertFalse(prefs.contains("display_scale_tenths"))
        assertFalse(prefs.contains("adaptive_browser_size"))
        assertEquals(profile, io.github.cany7.teslaplay.web.TeslaDisplayPreferences.load(app))
        assertEquals(90, AirPlayPersistence.loadImageQuality(app))
        val after = prefs.all
        TeslaDefaults.migrate(app)
        assertEquals(after, prefs.all)
    }

}
