package io.github.cany7.teslaplay.projection

import android.view.Surface
import io.github.cany7.teslaplay.media.CompressedVideoFrame
import io.github.cany7.teslaplay.media.VideoCodec

/** Android host port. A source receives this port from the composition layer, never a WebSession.
 * Only one ingress kind is enabled per output. Stop the producer before closing the output.
 */
internal interface ProjectionVideoOutput : AutoCloseable {
    val surface: Surface
    /** ISO-BMFF avcC/hvcC configuration; returns a revision required by every subsequent frame. */
    fun configure(codec: VideoCodec, codecData: ByteArray, requestKeyframe: () -> Unit): Long
    /** Complete Annex-B access unit; borrowed for this call, retained by asynchronous consumers. PTS is microseconds. */
    fun frame(formatRevision: Long, frame: CompressedVideoFrame, ptsUs: Long): Boolean
    /** Complete composed ARGB snapshot. The output copies it before returning and coalesces pending snapshots. */
    fun framebuffer(snapshot: ProjectionFramebuffer.Snapshot): Boolean
}
