// SPDX-License-Identifier: GPL-3.0-only
// Adapted from shihabal3amri/DiPlay 4487c689bfdd3baab12de67a993a671b67939eb7, CrashRecord.kt.
package io.github.cany7.teslaplay

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import java.io.File
import java.util.Collections
import java.util.IdentityHashMap

/** Bounded, redacted uncaught exception history for the next diagnostic export. */
internal object CrashRecord {
    internal const val FILE = "logs/last-crash.txt"
    internal const val MAX_BYTES = 64 * 1024
    private const val MAX_CAUSES = 4
    private const val MAX_FRAMES = 12
    private const val MAX_OWN_FRAMES = 6
    private const val MAX_MESSAGE = 120
    private var installed = false

    @Synchronized fun install(context: Context) {
        if (installed) return
        installed = true
        val file = File(context.applicationContext.filesDir, FILE)
        DiagnosticsRetention.prune(file.parentFile!!)
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "unknown"
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            recordAndDelegate(file, error, version) {
                if (previous != null) previous.uncaughtException(thread, error)
                else {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    kotlin.system.exitProcess(10)
                }
            }
        }
    }

    internal fun recordAndDelegate(file: File, error: Throwable, version: String, delegate: () -> Unit) {
        try {
            runCatching {
                file.parentFile?.mkdirs()
                file.writeText(format(error, System.currentTimeMillis(), version))
            }
        } finally { delegate() }
    }

    internal fun format(error: Throwable, atMillis: Long, version: String): String = buildString {
        appendLine("crash atEpochMs=$atMillis version=${DiagnosticRedactor.redact(version) ?: "redacted"}")
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        var current: Throwable? = error
        while (current != null && seen.size < MAX_CAUSES && seen.add(current)) {
            val message = current.message?.lineSequence()?.firstOrNull()?.let(DiagnosticRedactor::redact)?.take(MAX_MESSAGE)
            appendLine((if (seen.size == 1) "" else "caused by ") + current.javaClass.name + (message?.let { ": $it" } ?: ""))
            val frames = current.stackTrace
            frames.take(MAX_FRAMES).forEach { DiagnosticRedactor.redact("  at $it")?.let(::appendLine) }
            // A crash inside a framework call fills the first frames with Android's own; the DiYou card's
            // startActivity crash (#463) lost the DiPlay code that called it. Keep the first few of ours.
            val rest = frames.drop(MAX_FRAMES)
            val own = rest.filter(::ownFrame).take(MAX_OWN_FRAMES)
            if (own.isNotEmpty()) {
                appendLine("  ... TeslaPlay frames further down:")
                own.forEach { DiagnosticRedactor.redact("  at $it")?.let(::appendLine) }
            }
            if (rest.size > own.size) appendLine("  ... ${rest.size - own.size} more")
            current = current.cause
        }
    }.take(MAX_BYTES / 4)

    private fun ownFrame(frame: StackTraceElement): Boolean =
        frame.className.startsWith("io.github.cany7.teslaplay.")

    fun report(context: Context): String {
        val file = File(context.filesDir, FILE)
        DiagnosticsRetention.prune(file.parentFile!!)
        if (!file.isFile) return "No crash recorded by this version."
        return runCatching { file.readLines().mapNotNull(DiagnosticRedactor::redact).joinToString("\n") }
            .getOrElse { "Crash record unreadable: ${it.javaClass.simpleName}" }
    }
}

/** Installs [CrashRecord] when the process starts, before any activity, receiver or service runs. */
class CrashRecordInstaller : ContentProvider() {
    override fun onCreate(): Boolean {
        context?.let {
            CrashRecord.install(it)
            TeslaDefaults.migrate(it)
        }
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
