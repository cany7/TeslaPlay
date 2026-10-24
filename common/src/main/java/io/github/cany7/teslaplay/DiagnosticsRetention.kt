// SPDX-License-Identifier: GPL-3.0-only
package io.github.cany7.teslaplay

import android.content.Context
import java.io.File

internal object DiagnosticsRetention {
    const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    private val names get() = SessionLogFile.REPORT_NAMES + "last-crash.txt"

    fun prune(folder: File, now: Long = System.currentTimeMillis()) {
        for (name in names) {
            val file = File(folder, name)
            if (file.isFile && now - file.lastModified() > MAX_AGE_MS) file.delete()
        }
    }

    fun clear(context: Context) {
        for (name in names) File(context.filesDir, "logs/$name").delete()
        DisplayDiagnosticSnapshot.clear(context)
        RuntimeDiagnostics.clear()
    }
}
