// SPDX-License-Identifier: AGPL-3.0-only
package io.github.cany7.teslaplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Shares the idempotent, Activity-free service entry with the optional Bluetooth wake path. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && AirPlayPersistence.loadAutoStartOnBoot(context))
            BackgroundServiceStart.start(context)
    }
}
