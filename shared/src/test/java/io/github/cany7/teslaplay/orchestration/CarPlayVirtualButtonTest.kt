package io.github.cany7.teslaplay.orchestration

import android.os.Looper
import android.os.NetworkOnMainThreadException
import io.github.cany7.teslaplay.airplay.*
import io.github.cany7.teslaplay.transport.Iap2IdentificationConfig
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class CarPlayVirtualButtonTest {
    private lateinit var controller: CarPlayController
    private lateinit var session: AirPlaySession
    private lateinit var worker: ExecutorService
    private val wire = ByteArrayOutputStream()
    private val key = ByteArray(32) { it.toByte() }
    private var ended = false

    @Before fun setup() {
        val config = AirPlayConfig("test", "00:00:00:00:00:01", "00:00:00:00:00:02",
            "1", AirPlayDisplayConfig(800, 480))
        val identity = AirPlayIdentity(ByteArray(32), ByteArray(32), "test")
        controller = CarPlayController(RuntimeEnvironment.getApplication(),
            CarPlayRuntimeConfig(mfiTarget = MfiTarget.LOCAL,
                identification = Iap2IdentificationConfig("test", "test", "test", "1", "1", "1", 3)),
            config, identity, PairingStore(), object : AirPlaySessionListener {}, object : AirPlayMediaHandler {}, {})
        session = AirPlaySession(Socket(), config, identity, PairingStore(), null,
            object : AirPlaySessionListener { override fun onSessionEnded(session: AirPlaySession) { ended = true } },
            object : AirPlayMediaHandler {})
        val socket = object : Socket() {
            override fun getOutputStream() = object : OutputStream() {
                override fun write(value: Int) = write(byteArrayOf(value.toByte()))
                override fun write(bytes: ByteArray, offset: Int, length: Int) {
                    // Reproduce Android's actual device failure instead of accepting main-thread writes.
                    if (Looper.myLooper() == Looper.getMainLooper()) throw NetworkOnMainThreadException()
                    wire.write(bytes, offset, length)
                }
            }
        }
        ReflectionHelpers.setField(session, "eventSocket", socket)
        ReflectionHelpers.setField(session, "eventCipher", ControlCipher(key, key))
        ReflectionHelpers.setField(controller, "activeSession", session)
        worker = ReflectionHelpers.getField(controller, "touchExecutor")
    }

    @After fun teardown() {
        controller.close()
        assertTrue(controller.awaitClosed(3000))
        session.close()
    }

    private fun drain() { worker.submit {}.get(3, TimeUnit.SECONDS) }
    private fun commands(): List<Map<*, *>> {
        val requests = mutableListOf<RtspMessage.Request>()
        RtspMessage.Decoder().feed(ControlCipher(key, key).decrypt(wire.toByteArray()).data, requests::add)
        return requests.map { BplistCodec.decode(it.body) as Map<*, *> }
    }

    @Test fun mainThreadShortcutsWriteEncryptedCommandsOnTouchWorkerAfterReleaseWithPairedEdges() {
        assertEquals(Looper.getMainLooper(), Looper.myLooper())
        assertTrue(controller.sendTouch(emptyList()))
        for (action in listOf("home", "back", "siri", "previous", "play", "next"))
            assertTrue(controller.sendVirtualButton(action))
        drain()
        assertFalse("No shortcut may close the session through NetworkOnMainThreadException", ended)
        assertTrue(session.touchReady)
        val messages = commands()
        assertEquals(13, messages.size)
        assertEquals(AirPlayHid.TOUCH_HID_UID.toString(16), messages.first()["uuid"])
        val knobs = messages.filter { it["uuid"] == AirPlayHid.KNOB_HID_UID.toString(16) }
        assertEquals(listOf(2, 0, 4, 0), knobs.map { (it["hidReport"] as ByteArray)[0].toInt() })
        val siri = messages.filter { it["type"] == "requestSiri" }
        assertEquals(listOf(2L, 3L), siri.map { (it["params"] as Map<*, *>)["siriAction"] })
        val media = messages.filter { it["uuid"] == AirPlayHid.MEDIA_HID_UID.toString(16) }
        assertEquals(listOf(5, 0, 3, 0, 4, 0), media.map { (it["hidReport"] as ByteArray)[0].toInt() })
    }

    @Test fun queuedShortcutCannotWriteIntoAnEndedOrReplacedSession() {
        val gate = CountDownLatch(1)
        worker.execute { gate.await(3, TimeUnit.SECONDS) }
        try {
            assertTrue(controller.sendVirtualButton("home"))
            ReflectionHelpers.setField(controller, "activeSession", null)
        } finally { gate.countDown() }
        drain()
        assertEquals(0, wire.size())
    }

    @Test fun invalidNotReadyAndClosedShortcutsAreRejected() {
        assertFalse(controller.sendVirtualButton("unknown"))
        ReflectionHelpers.setField(session, "eventCipher", null)
        assertFalse(controller.sendVirtualButton("home"))
        controller.close()
        assertFalse(controller.sendVirtualButton("play"))
        assertEquals(0, wire.size())
    }
}
