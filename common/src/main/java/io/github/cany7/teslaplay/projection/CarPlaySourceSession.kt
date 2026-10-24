package io.github.cany7.teslaplay.projection

import io.github.cany7.teslaplay.CarPlayBackgroundSession
import io.github.cany7.teslaplay.CarPlayAudioOutput
import java.util.concurrent.CompletableFuture

/** The only projection adapter that knows about the legacy CarPlay controller. */
internal class CarPlaySourceSession(
    private val status: () -> String?,
    /** Negotiated once; saved changes take effect only in the replacement session. */
    val audioOutput: CarPlayAudioOutput = CarPlayAudioOutput.ANDROID,
) : ProjectionSession {
    val carBluetoothAudio get() = audioOutput == CarPlayAudioOutput.IPHONE_BLUETOOTH
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
        nightMode = true, audioPlayback = !carBluetoothAudio, microphone = !carBluetoothAudio)
    override val inputReady get() = controller?.touchReady == true
    override val media get() = controller
    override val mediaOutput get() = sink
    override fun touch(contacts: List<ProjectionContact>): Boolean {
        if (contacts.any { it.id !in 0..1 }) return false
        // AirPlayTouchSlots retains the last coordinate for absent/released contacts.
        return controller?.sendTouch(contacts) ?: false
    }
    override fun setNightMode(night: Boolean) =
        if (controller?.setNightMode(night) == true) ProjectionCommandResult.SENT else ProjectionCommandResult.UNAVAILABLE
    override fun stop(): CompletableFuture<Unit> {
        CarPlayBackgroundSession.stop()
        return closed
    }
}
