package io.github.cany7.teslaplay.web

import android.os.Looper
import io.github.cany7.teslaplay.AirPlayPersistence
import io.github.cany7.teslaplay.media.VideoCodec
import io.github.cany7.teslaplay.projection.*
import java.util.concurrent.CompletableFuture
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class BrowserCodecFallbackTest {
    private fun source(codec: VideoCodec = VideoCodec.H265) = WebVideoSource(StreamTechnology.WEBRTC).apply {
        val hvcc = ByteArray(23).apply {
            this[0] = 1; this[1] = 1; this[12] = 120; this[21] = 3; this[22] = 3
        } + byteArrayOf(32, 0, 1, 0, 2, 64, 1, 33, 0, 1, 0, 2, 66, 1, 34, 0, 1, 0, 2, 68, 1)
        val avcc = byteArrayOf(1, 0x64, 0, 0x20, -1, -31, 0, 4, 0x67, 0x64, 0, 0x20, 1, 0, 2, 0x68, 1)
        configure(110, codec, if (codec == VideoCodec.H265) hvcc else avcc)
        assertNotNull(config)
    }

    @Test fun oneRetryPerConnectionRejectsStaleEpochAndPreservesUserPreference() {
        val app = RuntimeEnvironment.getApplication()
        AirPlayPersistence.saveHevcEnabled(app, true)
        val session = object : ProjectionSession {
            override val descriptor = SourceDescriptor("carplay", "CarPlay")
            override val capabilities = ProjectionCapabilities(emptySet())
            override val inputReady = true
            override fun touch(contacts: List<ProjectionContact>) = true
            override fun stop() = CompletableFuture.completedFuture(Unit)
        }
        assertTrue(projectionSessions.adopt(session, emptySet()))
        var retries = 0
        WebSession.setH264FallbackHandler { retries++; true }
        try {
            val first = source()
            val old = WebSession.beginVideo(1280, 720)
            WebSession.attachVideoSource(old, first); WebSession.setVideoActive(old, true)
            val stale = WebSession.mediaSnapshot()
            WebSession.requestH264Fallback(first, stale.sessionId, stale.videoEpoch)
            val next = WebSession.beginVideo(1280, 720)
            WebSession.attachVideoSource(next, first); WebSession.setVideoActive(next, true)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("Queued failure from the previous epoch is ignored", 0, retries)
            val live = WebSession.mediaSnapshot()
            repeat(2) { WebSession.requestH264Fallback(first, live.sessionId, live.videoEpoch) }
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, retries)
            assertTrue(AirPlayPersistence.loadHevcEnabled(app))
            val avc = source(VideoCodec.H264)
            WebSession.attachVideoSource(next, avc)
            WebSession.setH264FallbackHandler { retries++; true }
            WebSession.requestH264Fallback(avc, live.sessionId, live.videoEpoch)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("H.264 failures use existing image fallback", 1, retries)
            WebSession.requestH264Fallback(first, live.sessionId, live.videoEpoch)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("Replaced sources cannot restart the new source", 1, retries)
        } finally {
            WebSession.setH264FallbackHandler(null)
            WebSession.stop()
            projectionSessions.ended(session)
        }
    }
}
