// SPDX-License-Identifier: AGPL-3.0-only
package io.github.cany7.teslaplay

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import io.github.cany7.teslaplay.host.R
import io.github.cany7.teslaplay.web.WebSession

/** DiPlay fe1a59db/4487c689 ACL matching and debounce, adapted to a service-only launch. */
class PhoneBluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!PhoneBluetoothStart.enabled(context)) return
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED &&
            intent.action != BluetoothDevice.ACTION_ACL_DISCONNECTED) return
        if (!PhoneBluetoothStart.hasPermission(context)) {
            PhoneBluetoothStart.record(context, "permission")
            return
        }
        val device = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        } ?: return
        val address = runCatching { device.address }.getOrNull() ?: return
        if (!address.equals(TeslaPlayPreferences.phoneAddress(context), true)) return
        if (intent.action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
            PhoneBluetoothStart.prefs(context).edit().remove("last_launch_elapsed").apply()
            return
        }
        if (PhoneBluetoothStart.accept(context, SystemClock.elapsedRealtime())) BackgroundServiceStart.start(context)
    }
}

internal object PhoneBluetoothStart {
    fun prefs(context: Context) = context.getSharedPreferences("tesla_phone_start", Context.MODE_PRIVATE)
    fun enabled(context: Context) = prefs(context).getBoolean("enabled", false)
    fun save(context: Context, enabled: Boolean) { prefs(context).edit().putBoolean("enabled", enabled).remove("last_launch_elapsed").apply() }
    fun hasPermission(context: Context) = Build.VERSION.SDK_INT < 31 ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    @Synchronized fun accept(context: Context, elapsed: Long): Boolean {
        val prefs = prefs(context)
        val previous = prefs.getLong("last_launch_elapsed", -1)
        if (previous >= 0 && elapsed >= previous && elapsed - previous < 15_000) return false
        return prefs.edit().putLong("last_launch_elapsed", elapsed).commit()
    }
    fun record(context: Context, outcome: String) { prefs(context).edit().putString("result", outcome).apply() }
    fun status(context: Context): String = context.getString(when {
        !hasPermission(context) -> R.string.tesla_phone_start_permission
        TeslaPlayPreferences.phoneAddress(context) == null -> R.string.tesla_phone_start_select
        prefs(context).getString("result", "") == "authentication" -> R.string.tesla_phone_start_auth
        prefs(context).getString("result", "") == "blocked" -> R.string.tesla_phone_start_blocked
        else -> R.string.tesla_phone_start_help
    })
}

/** Shared by boot and Bluetooth. Never replaces a session or brings an Activity forward. */
internal object BackgroundServiceStart {
    fun start(context: Context) {
        if (WebSession.running || CarPlayBackgroundSession.hasSession()) {
            PhoneBluetoothStart.record(context, "running")
            return
        }
        if (!PhoneBluetoothStart.hasPermission(context)) {
            PhoneBluetoothStart.record(context, "permission")
            return
        }
        if (runCatching { TeslaPlayBootstrap.ensure(context) }.isFailure) {
            PhoneBluetoothStart.record(context, "authentication")
            return
        }
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, TeslaPlaySessionService::class.java))
        }.onSuccess { PhoneBluetoothStart.record(context, "started") }
            .onFailure { PhoneBluetoothStart.record(context, "blocked") }
    }
}
