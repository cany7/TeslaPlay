package io.github.cany7.teslaplay.web

import android.content.Context
import io.github.cany7.teslaplay.network.TeslaHttpConfig
import io.github.cany7.teslaplay.network.TeslaHttpMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class TeslaHttpPreferencesTest {
    @Test fun vpnModePreservesRootSettingsButAlwaysUsesTheFixedEntry() {
        val context = RuntimeEnvironment.getApplication()
        val root = TeslaHttpConfig(true, "100.96.0.2", 81, 444, TeslaHttpMode.ROOT)
        TeslaHttpCompatibility.save(context, root)
        TeslaHttpCompatibility.save(context, root.copy(mode = TeslaHttpMode.VPN, vpnAddress = "6.6.6.7"))
        val vpn = TeslaHttpCompatibility.config(context)
        assertEquals(root.copy(mode = TeslaHttpMode.VPN), vpn)
        assertEquals("100.96.0.1", vpn.accessAddress)
        assertEquals("100.96.0.2", vpn.copy(mode = TeslaHttpMode.ROOT).accessAddress)
        assertNull(TeslaHttpCompatibility.plan(vpn, listOf(HttpDownstream("ap0", 7, "192.168.43.1/24")), 8080, 8443, true))
    }

    @Test fun oldDisabledVpnAndAddressMigrateWithoutRewritingTheSelectedMode() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("tesla_http", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("enabled", false).putString("mode", "VPN")
            .putString("vpn_address", "6.6.6.6").apply()
        val migrated = TeslaHttpCompatibility.config(context)
        assertTrue(migrated.enabled)
        assertEquals(TeslaHttpMode.VPN, migrated.mode)
        assertEquals("100.96.0.1", migrated.vpnAddress)
        assertEquals("100.96.0.1", prefs.getString("vpn_address", null))
        TeslaHttpCompatibility.save(context, migrated.copy(mode = TeslaHttpMode.ROOT))
        assertEquals(TeslaHttpMode.ROOT, TeslaHttpCompatibility.config(context).mode)
    }

    @Test fun routingFollowsWebServiceAndCannotBeDisabledByAnOldToggle() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals(TeslaHttpConfig(), TeslaHttpCompatibility.config(context))
        assertEquals(TeslaHttpMode.VPN, TeslaHttpCompatibility.config(context).mode)
        val value = TeslaHttpConfig(true, "100.96.0.2", 81, 444)
        TeslaHttpCompatibility.save(context, value.copy(enabled = false))
        assertEquals(value, TeslaHttpCompatibility.config(context))
    }

    @Test fun invalidModeFailsClosedAndOldPlaceholderIsReplacedByFixedEntry() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("tesla_http", Context.MODE_PRIVATE)
        prefs.edit().putString("mode", "UNKNOWN").apply()
        assertFalse(TeslaHttpCompatibility.config(context).enabled)
        prefs.edit().putString("mode", "VPN").putString("vpn_address", TeslaHttpConfig.VPN_PLACEHOLDER_ADDRESS).apply()
        assertEquals(TeslaHttpConfig(), TeslaHttpCompatibility.config(context))
    }

    @Test fun olderPreferencesKeepDefaultPortAndInvalidPortsFailClosed() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("tesla_http", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("enabled", true).putString("address", "100.96.0.2").apply()
        assertEquals(TeslaHttpConfig(true, "100.96.0.2"), TeslaHttpCompatibility.config(context))
        prefs.edit().putInt("http_mapping_port", 0).apply()
        assertEquals(TeslaHttpConfig(enabled = false), TeslaHttpCompatibility.config(context))
    }

    @Test fun invalidStoredConfigCannotStartOrAdvertiseAnArbitraryAddress() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("tesla_http", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("enabled", true).putString("address", "0.0.0.0").apply()
        assertEquals(TeslaHttpConfig(enabled = false), TeslaHttpCompatibility.config(context))
        prefs.edit().putString("address", TeslaHttpConfig.DEFAULT_ADDRESS)
            .putInt("https_mapping_port", 0).apply()
        assertEquals(TeslaHttpConfig(enabled = false), TeslaHttpCompatibility.config(context))
    }
}
