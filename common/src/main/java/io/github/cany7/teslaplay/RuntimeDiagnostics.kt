// SPDX-License-Identifier: GPL-3.0-only
package io.github.cany7.teslaplay

import android.content.Context
import android.os.Build
import android.os.PowerManager
import io.github.cany7.teslaplay.web.WebListenSettings
import io.github.cany7.teslaplay.web.WebSession
import org.json.JSONObject

/** State only: never retain HTTP paths, query strings, addresses or browser credentials. */
internal object RuntimeDiagnostics {
    @Volatile private var lastRequestAt = 0L
    fun requestReceived() { lastRequestAt = System.currentTimeMillis() }
    fun clear() { lastRequestAt = 0L }
    fun sample(context: Context): JSONObject {
        val thermal = if (Build.VERSION.SDK_INT >= 29) runCatching {
            context.getSystemService(PowerManager::class.java).currentThermalStatus
        }.getOrNull() else null
        val tls = when {
            !WebListenSettings.httpsEnabled(context) -> "disabled"
            WebSession.tlsError != null -> "error"
            WebSession.httpsReady -> "ready"
            else -> "pending"
        }
        return JSONObject()
            .put("sampledAt", System.currentTimeMillis())
            .put("serviceStarted", WebSession.running || WebSession.httpsReady)
            .put("clientRequestedAt", lastRequestAt.takeIf { it > 0 } ?: JSONObject.NULL)
            .put("clientConnected", WebSession.hasViewer)
            .put("sourceState", WebSession.mediaSnapshot().sourceState)
            .put("thermalStatus", thermal ?: JSONObject.NULL)
            .put("socTemperatureC", JSONObject.NULL)
            .put("tlsState", tls)
    }
}
