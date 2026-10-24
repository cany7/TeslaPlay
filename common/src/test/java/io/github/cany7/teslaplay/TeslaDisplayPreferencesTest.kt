package io.github.cany7.teslaplay

import io.github.cany7.teslaplay.web.TeslaDisplayPreferences as Display
import org.junit.Assert.*
import io.github.cany7.teslaplay.web.WebSession
import io.github.cany7.teslaplay.projection.*
import java.util.concurrent.CompletableFuture
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class TeslaDisplayPreferencesTest {
    @Test fun profilesSurviveReloadWithoutChangingCodecAndFrameRate() {
        val app = RuntimeEnvironment.getApplication()
        AirPlayPersistence.saveHevcEnabled(app, true)
        AirPlayPersistence.saveFps(app, 60)
        val config = Display.Config("large", Display.Profile(1600, 1000, "2k", .5), Display.Profile(1920, 1200, "fit", .75), true)
        assertTrue(Display.save(app, config))
        assertFalse(Display.save(app, config))
        assertEquals(config, Display.load(app))
        assertEquals(1152 to 720, config.right.output())
        assertEquals(1440 to 900, config.large.output())
        assertEquals(60, AirPlayPersistence.loadFps(app))
        assertTrue(AirPlayPersistence.loadHevcEnabled(app))
        assertFalse(Display.buttons(app))
        Display.saveButtons(app, true)
        assertTrue(Display.buttons(app))
    }
    @Test fun dimensionsFollowExistingProtocolAndRestoreRejectsInvalidTypes() {
        assertTrue(Display.validSize(8192, 4096))
        assertFalse(Display.validSize(1601, 1000))
        assertFalse(Display.validSize(1600, 320))
        for ((key, value) in listOf("width" to "1280", "height" to "720", "scale" to "1.0", "preset" to 720)) {
            val obj = Display.Config().json()
            obj.getJSONObject("right").put(key, value)
            assertFalse("Reject coerced profile field $key", Display.validateJson(obj.toString()))
        }
        val enabledString = Display.Config().json().put("enabled", "true")
        assertFalse("Reject coerced enabled flag", Display.validateJson(enabledString.toString()))
        assertEquals(640 to 320, Display.Profile(640, 320, "fit", .5).output())
    }
    @Test fun savedProfilesAndViewportChangesNeverInterruptAnActiveSession() {
        val app = RuntimeEnvironment.getApplication()
        val session = object : ProjectionSession {
            override val descriptor = SourceDescriptor("carplay", "CarPlay")
            override val capabilities = ProjectionCapabilities(emptySet())
            override val inputReady = true
            override fun touch(contacts: List<ProjectionContact>) = true
            override fun stop() = CompletableFuture.completedFuture(Unit)
        }
        assertTrue(projectionSessions.adopt(session, emptySet()))
        var applied: Pair<Int?, Int?>? = null
        try {
            WebSession.setBrowserViewportListener { w, h -> applied = w to h }
            assertNull(applied) // Recreating an Activity must not apply its pending profile.
            Display.save(app, Display.Config(right = Display.Profile(1600, 1000), enabled = true))
            assertNull(applied)
            assertTrue(WebSession.reportBrowserViewport(1920, 1080))
            assertNull(applied)
            WebSession.applySavedDisplay(app)
            assertEquals(1600 to 1000, applied)
        } finally {
            WebSession.setBrowserViewportListener(null)
            projectionSessions.ended(session)
        }
    }

    @Test fun ordinaryConnectionAndApplyUseSameProfileDespiteLegacyAdaptiveFlag() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = Display.preferences(app)
        prefs.edit().clear().putBoolean("adaptive_browser_size", false).commit()
        WebSession.reportBrowserViewport(1400, 900)
        WebSession.refreshConnectionPreferences(app)
        assertEquals(1400 to 900, WebSession.currentBrowserViewport)
        val config = Display.Config(right = Display.Profile(1600, 1000, "fit", .75), enabled = true)
        Display.save(app, config)
        WebSession.refreshConnectionPreferences(app)
        val expected = config.profile().output()
        assertEquals(expected, WebSession.currentBrowserViewport)
        var applied: Pair<Int?, Int?>? = null
        try {
            WebSession.setBrowserViewportListener { w, h -> applied = w to h }
            assertEquals(expected, applied)
            WebSession.applySavedDisplay(app)
            assertEquals(expected, applied)
        } finally { WebSession.setBrowserViewportListener(null) }
    }

}
