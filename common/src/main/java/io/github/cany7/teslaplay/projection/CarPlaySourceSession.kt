package io.github.cany7.teslaplay.projection

import io.github.cany7.teslaplay.CarPlayBackgroundSession
import java.util.concurrent.CompletableFuture

/** The only projection adapter that knows about the legacy CarPlay controller. */
internal class CarPlaySourceSession(private val status: () -> String?) : ProjectionSession {
    @Volatile private var controller: io.github.cany7.teslaplay.orchestration.CarPlayController? = null
    @Volatile private var sink: io.github.cany7.teslaplay.media.AndroidMediaSink? = null
    private val closed = CompletableFuture<Unit>()
    fun bind(controller: io.github.cany7.teslaplay.orchestration.CarPlayController?, sink: io.github.cany7.teslaplay.media.AndroidMediaSink?) {
        this.controller = controller
        this.sink = sink
    }
    fun completed(clean: Boolean) {
        if (clean) closed.complete(Unit)
        else closed.completeExceptionally(IllegalStateException("CarPlay transport did not stop"))
    }
    override val statusText get() = status()
    override val descriptor = SourceDescriptor("carplay", "CarPlay")
    override val capabilities = ProjectionCapabilities(setOf(VideoIngressKind.ENCODED), 2,
        nightMode = true, audioPlayback = true, microphone = true)
    override val inputReady get() = controller?.touchReady == true
    override val media get() = controller
    override val mediaOutput get() = sink
    override fun touch(contacts: List<ProjectionContact>): Boolean {
        if (contacts.any { it.id !in 0..1 }) return false
        // The CarPlay HID device has fixed slots, even when the first finger is released.
        return controller?.sendTouch((0..1).map { id ->
            contacts.find { it.id == id } ?: ProjectionContact(id, 0.0, 0.0, false)
        }) ?: false
    }
    override fun setNightMode(night: Boolean) =
        if (controller?.setNightMode(night) == true) ProjectionCommandResult.SENT else ProjectionCommandResult.UNAVAILABLE
    override fun stop(): CompletableFuture<Unit> {
        CarPlayBackgroundSession.stop()
        return closed
    }
}
