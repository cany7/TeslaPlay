package io.github.cany7.teslaplay

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class CrashRecordTest {
    @Test fun savedCrashIsBoundedAndRedactedBeforeStorage() {
        val folder = Files.createTempDirectory("crash-record").toFile()
        try {
            val error = IllegalStateException("token=never-save-this", IllegalArgumentException("peer=192.168.1.3"))
            error.stackTrace = Array(300) {
                StackTraceElement(if (it == 299) "io.github.cany7.teslaplay.Test" else "android.Framework", "method", "Test.kt", it)
            }
            val file = folder.resolve("crash.txt")
            var delegated = false
            CrashRecord.recordAndDelegate(file, error, "1.2.3") { delegated = true }
            val report = file.readText()
            assertTrue(delegated)
            assertTrue(report.contains("version=1.2.3"))
            assertTrue(report.contains("IllegalStateException"))
            assertTrue(report.contains("io.github.cany7.teslaplay.Test"))
            assertTrue(report.contains("caused by java.lang.IllegalArgumentException"))
            assertFalse(report.contains("never-save-this"))
            assertFalse(report.contains("192.168.1.3"))
            assertTrue(file.length() <= CrashRecord.MAX_BYTES)
            assertTrue(report.lines().count { it.contains("  at ") } <= 36)
        } finally { folder.deleteRecursively() }
    }

    @Test fun failedWriteStillDelegatesToOriginalHandler() {
        val folder = Files.createTempDirectory("crash-write-failure").toFile()
        try {
            var delegated = false
            CrashRecord.recordAndDelegate(folder, RuntimeException("failure"), "test") { delegated = true }
            assertTrue(delegated)
        } finally { folder.deleteRecursively() }
    }

    @Test fun oldPrivateLogsExpireWithoutDeletingUnrelatedFiles() {
        val folder = Files.createTempDirectory("diagnostics-retention").toFile()
        try {
            val old = folder.resolve("last-crash.txt").apply { writeText("old"); setLastModified(1000) }
            val recent = folder.resolve("teslaplay.log").apply { writeText("current") }
            val unrelated = folder.resolve("other.txt").apply { writeText("keep"); setLastModified(1000) }
            DiagnosticsRetention.prune(folder)
            assertFalse(old.exists())
            assertTrue(recent.exists())
            assertTrue(unrelated.exists())
        } finally { folder.deleteRecursively() }
    }
}
