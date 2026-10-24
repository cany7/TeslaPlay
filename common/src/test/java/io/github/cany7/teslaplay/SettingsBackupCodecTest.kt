package io.github.cany7.teslaplay

import io.github.cany7.teslaplay.backup.SettingsBackupCodec
import io.github.cany7.teslaplay.web.TeslaDisplayPreferences
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SettingsBackupCodecTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val prefs get() = app.getSharedPreferences("teslaplay_airplay", 0)
    @Before fun reset() {
        prefs.edit().clear().commit()
        app.getSharedPreferences("teslaplay", 0).edit().clear().commit()
    }
    private fun document(values: JSONObject) = JSONObject().put("format", SettingsBackupCodec.FORMAT)
        .put("version", 1).put("settings", JSONObject().put("teslaplay_airplay", values)).toString().toByteArray()

    @Test fun previousPackageBackupImportsIntoCurrentPreferencesWithoutRestoringRetiredScaling() {
        val old = JSONObject().put("format", SettingsBackupCodec.FORMAT).put("version", 1)
            .put("settings", JSONObject().put("xcertplay_airplay", JSONObject()
                .put("web_image_quality", 91).put("display_scale_tenths", 6)
                .put("adaptive_browser_size", false)))
        SettingsBackupCodec.apply(app, SettingsBackupCodec.preview(app, old.toString().toByteArray()))
        assertEquals(91, AirPlayPersistence.loadImageQuality(app))
        assertFalse(prefs.contains("display_scale_tenths"))
        assertFalse(prefs.contains("adaptive_browser_size"))
        assertFalse(app.getSharedPreferences("xcertplay_airplay", 0).contains("web_image_quality"))
    }

    @Test fun exportUsesActualDefaultsAndNeverExportsCredentials() {
        prefs.edit().putString("identity_private", "secret").putString("manual_hotspot_passphrase", "secret")
            .putString("pairing.phone", "secret").putString("duckdns_token", "secret").commit()
        val json = JSONObject(String(SettingsBackupCodec.export(app)))
        assertFalse(json.toString().contains("secret"))
        val values = json.getJSONObject("settings").getJSONObject("teslaplay_airplay")
        assertEquals(AirPlayPersistence.loadFps(app), values.getInt("display_fps"))
        assertEquals(AirPlayPersistence.loadImageFormat(app).name, values.getString("web_image_format"))
        assertEquals(AirPlayPersistence.loadWidthPhysicalMm(app), values.getInt("display_width_physical_mm"))
        assertEquals(AirPlayPersistence.loadImageQuality(app), values.getInt("web_image_quality"))
        assertEquals(AirPlayPersistence.loadHideTopBar(app), values.getBoolean("hide_top_bar"))
        assertEquals(0, SettingsBackupCodec.preview(app, SettingsBackupCodec.export(app)).count)
    }

    @Test fun legacyAutoConnectIsPreservedAndImportedInTheSameAtomicFile() {
        app.getSharedPreferences("teslaplay", 0).edit().putBoolean("auto_connect", true).commit()
        assertTrue(TeslaPlayPreferences.autoConnect(app))
        val json = JSONObject(String(SettingsBackupCodec.export(app))).getJSONObject("settings")
        assertEquals(1, json.length())
        assertTrue(json.getJSONObject("teslaplay_airplay").getBoolean("auto_connect"))
        SettingsBackupCodec.apply(app, SettingsBackupCodec.preview(app, document(JSONObject().put("auto_connect", false))))
        assertFalse(TeslaPlayPreferences.autoConnect(app))
    }

    @Test fun roundTripPreservesHevc60FpsTwoProfilesAndButtons() {
        AirPlayPersistence.saveFps(app, 60)
        AirPlayPersistence.saveHevcEnabled(app, true)
        TeslaDisplayPreferences.save(app, TeslaDisplayPreferences.Config(
            active = "large", right = TeslaDisplayPreferences.Profile(1280, 720, "720p", .75),
            large = TeslaDisplayPreferences.Profile(1920, 1080, "2k", 1.0), enabled = true))
        TeslaDisplayPreferences.saveButtons(app, true)
        val expected = TeslaDisplayPreferences.load(app)
        val bytes = SettingsBackupCodec.export(app)
        prefs.edit().clear().putBoolean("hevc_enabled", false).commit()
        val preview = SettingsBackupCodec.preview(app, bytes)
        assertTrue(preview.count > 0)
        assertFalse(AirPlayPersistence.loadHevcEnabled(app))
        SettingsBackupCodec.apply(app, preview)
        assertEquals(60, AirPlayPersistence.loadFps(app))
        assertTrue(AirPlayPersistence.loadHevcEnabled(app))
        assertEquals(expected, TeslaDisplayPreferences.load(app))
        assertTrue(TeslaDisplayPreferences.buttons(app))
    }

    @Test fun invalidLaterFieldRejectsWholeImportWithoutMutation() {
        val invalid = listOf(
            JSONObject().put("hevc_enabled", true).put("display_fps", 999),
            JSONObject().put("hevc_enabled", "true"),
            JSONObject().put("display_fps", 30.5),
            JSONObject().put("web_stream_technology", "INVALID"),
            JSONObject().put("carplay_safe_margins", "50,0,0,0"),
            JSONObject().put("tesla_display_profiles_json", "{}"),
        )
        invalid.forEach { assertTrue(runCatching { SettingsBackupCodec.preview(app, document(it)) }.isFailure) }
        assertTrue(prefs.all.isEmpty())
        assertTrue(runCatching { SettingsBackupCodec.preview(app, ByteArray(SettingsBackupCodec.MAX_BYTES + 1)) }.isFailure)
        assertTrue(runCatching { SettingsBackupCodec.preview(app, "{".toByteArray()) }.isFailure)
        val future = JSONObject(String(document(JSONObject()))).put("version", 2)
        assertTrue(runCatching { SettingsBackupCodec.preview(app, future.toString().toByteArray()) }.isFailure)
    }

    @Test fun unknownKeysAreReportedAndCannotImportPairingOrDeviceIdentity() {
        val preview = SettingsBackupCodec.preview(app, document(JSONObject().put("hevc_enabled", true)
            .put("pairing.phone", "attacker").put("future_feature", true)))
        assertEquals(2, preview.skipped)
        SettingsBackupCodec.apply(app, preview)
        assertTrue(AirPlayPersistence.loadHevcEnabled(app))
        assertFalse(prefs.contains("pairing.phone"))
        assertFalse(prefs.contains("future_feature"))
    }
    @Test fun oldBackupCannotRestoreRetiredScaleOrAdaptiveSwitch() {
        val preview = SettingsBackupCodec.preview(app, document(JSONObject()
            .put("display_scale_tenths", 6).put("adaptive_browser_size", false)
            .put("web_image_quality", 90)))
        assertEquals(2, preview.skipped)
        SettingsBackupCodec.apply(app, preview)
        assertFalse(prefs.contains("display_scale_tenths"))
        assertFalse(prefs.contains("adaptive_browser_size"))
        assertEquals(90, AirPlayPersistence.loadImageQuality(app))
        val exported = JSONObject(String(SettingsBackupCodec.export(app)))
            .getJSONObject("settings").getJSONObject("teslaplay_airplay")
        assertFalse(exported.has("display_scale_tenths"))
        assertFalse(exported.has("adaptive_browser_size"))
    }

}
