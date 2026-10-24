package io.github.cany7.teslaplay

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PhoneBluetoothStartTest {
    private val app get() = RuntimeEnvironment.getApplication()
    @Before fun reset() {
        PhoneBluetoothStart.prefs(app).edit().clear().commit()
        app.getSharedPreferences("teslaplay", 0).edit().clear().commit()
    }
    @Test fun disabledByDefaultAndDebounceSurvivesReceiverInstances() {
        assertFalse(PhoneBluetoothStart.enabled(app))
        assertTrue(PhoneBluetoothStart.accept(app, 20_000))
        assertFalse(PhoneBluetoothStart.accept(app, 20_100))
        assertTrue(PhoneBluetoothStart.accept(app, 35_000))
        assertTrue(PhoneBluetoothStart.accept(app, 1_000)) // rebooted elapsed clock
    }
    @Test fun otherDevicesDoNotTriggerAndDisconnectAllowsReconnect() {
        val target = "00:11:22:33:44:55"
        TeslaPlayPreferences.savePhone(app, target, "iPhone")
        PhoneBluetoothStart.save(app, true)
        val other = BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:44:66")
        PhoneBluetoothReceiver().onReceive(app, Intent(BluetoothDevice.ACTION_ACL_CONNECTED).putExtra(BluetoothDevice.EXTRA_DEVICE, other))
        assertFalse(PhoneBluetoothStart.prefs(app).contains("last_launch_elapsed"))
        PhoneBluetoothStart.accept(app, 20_000)
        val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(target)
        PhoneBluetoothReceiver().onReceive(app, Intent(BluetoothDevice.ACTION_ACL_DISCONNECTED).putExtra(BluetoothDevice.EXTRA_DEVICE, device))
        assertFalse(PhoneBluetoothStart.prefs(app).contains("last_launch_elapsed"))
        PhoneBluetoothStart.save(app, false)
        PhoneBluetoothReceiver().onReceive(app, Intent(BluetoothDevice.ACTION_ACL_CONNECTED).putExtra(BluetoothDevice.EXTRA_DEVICE, device))
        assertFalse(PhoneBluetoothStart.prefs(app).contains("last_launch_elapsed"))
    }
}
