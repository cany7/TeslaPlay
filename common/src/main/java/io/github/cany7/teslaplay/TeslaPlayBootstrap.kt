package io.github.cany7.teslaplay

import android.content.Context
import io.github.cany7.teslaplay.airplay.AirPlayIdentity
import io.github.cany7.teslaplay.orchestration.MfiTarget
import java.security.MessageDigest

/** Uses user-imported resources, with optional privately provisioned APK assets as a fallback. */
internal object TeslaPlayBootstrap {
    private val importInProgress = java.util.concurrent.atomic.AtomicBoolean(false)
    val importing: Boolean get() = importInProgress.get()
    @Synchronized fun beginImport(): Boolean = importInProgress.compareAndSet(false, true)
    fun finishImport() { importInProgress.set(false) }

    @Synchronized fun ensure(context: Context) {
        check(!importing) { "Authentication import in progress" }
        val store = MfiAssetStore(context.noBackupFilesDir)
        if (!store.exists()) {
            store.installFiles(
                { context.assets.open("offline-mfi/identity.pk8") },
                { context.assets.open("offline-mfi/certificate.p7b") },
            )
        }
        store.load()
        AirPlayPersistence.saveMfiTarget(context, MfiTarget.LOCAL)
        AirPlayPersistence.saveDebugLogsEnabled(context, false)
    }

    fun deviceId(identity: AirPlayIdentity): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(identity.publicKey).take(6).toByteArray()
        bytes[0] = ((bytes[0].toInt() and 0xfc) or 0x02).toByte()
        return bytes.joinToString(":") { "%02X".format(it.toInt() and 0xff) }
    }
}

internal object TeslaPlayPreferences {
    private fun prefs(context: Context) = context.getSharedPreferences("teslaplay", Context.MODE_PRIVATE)
    fun phoneAddress(context: Context): String? = prefs(context).getString("phone_address", null)
    fun phoneName(context: Context): String = prefs(context).getString("phone_name", null) ?: context.getString(io.github.cany7.teslaplay.shared.R.string.ui_your_iphone)
    fun savePhone(context: Context, address: String, name: String) {
        prefs(context).edit().putString("phone_address", address).putString("phone_name", name).apply()
    }
    // Portable settings share one atomic preferences commit; retain the original key as a migration fallback.
    fun autoConnect(context: Context) = context.getSharedPreferences("teslaplay_airplay", Context.MODE_PRIVATE)
        .getBoolean("auto_connect", prefs(context).getBoolean("auto_connect", false))
    fun saveAutoConnect(context: Context, value: Boolean) {
        context.getSharedPreferences("teslaplay_airplay", Context.MODE_PRIVATE).edit().putBoolean("auto_connect", value).apply()
    }
}
