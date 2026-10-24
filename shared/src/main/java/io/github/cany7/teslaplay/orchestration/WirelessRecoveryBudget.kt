package io.github.cany7.teslaplay.orchestration

import io.github.cany7.teslaplay.network.WirelessStartupPolicy

/** A stable session or explicit reconnect resets the bounded startup retry allowance. */
internal class WirelessRecoveryBudget(private val maximum: Int = WirelessStartupPolicy.MAX_STARTUP_RETRIES) {
    private var attempts = 0
    @Synchronized fun retry(): Boolean = if (attempts < maximum) { attempts++; true } else false
    @Synchronized fun reset() { attempts = 0 }
}
