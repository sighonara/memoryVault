package org.sightech.memoryvault.logging

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalLogServiceTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `getLogs parses JSON log lines`() {
        val logFile = tempDir.resolve("memoryvault.log")
        Files.writeString(logFile, buildString {
            appendLine("""{"timestamp":"2026-03-05T10:00:00.000Z","level":"INFO","logger":"org.sightech.memoryvault.feed.FeedService","message":"Feed sync started","thread":"main"}""")
            appendLine("""{"timestamp":"2026-03-05T10:00:01.000Z","level":"ERROR","logger":"org.sightech.memoryvault.youtube.YtDlpService","message":"Download failed","thread":"scheduler-1"}""")
        })

        val service = LocalLogService(logFile.toString())
        val logs = service.getLogs(null, null, 50)

        assertEquals(2, logs.size)
        assertEquals("ERROR", logs[0].level)  // Most recent first
        assertEquals("INFO", logs[1].level)
    }

    @Test
    fun `getLogs filters by level`() {
        val logFile = tempDir.resolve("memoryvault.log")
        Files.writeString(logFile, buildString {
            appendLine("""{"timestamp":"2026-03-05T10:00:00.000Z","level":"INFO","logger":"test","message":"info msg","thread":"main"}""")
            appendLine("""{"timestamp":"2026-03-05T10:00:01.000Z","level":"ERROR","logger":"test","message":"error msg","thread":"main"}""")
        })

        val service = LocalLogService(logFile.toString())
        val logs = service.getLogs("ERROR", null, 50)

        assertEquals(1, logs.size)
        assertEquals("error msg", logs[0].message)
    }

    @Test
    fun `getLogs filters by logger`() {
        val logFile = tempDir.resolve("memoryvault.log")
        Files.writeString(logFile, buildString {
            appendLine("""{"timestamp":"2026-03-05T10:00:00.000Z","level":"INFO","logger":"org.sightech.memoryvault.feed.FeedService","message":"feed msg","thread":"main"}""")
            appendLine("""{"timestamp":"2026-03-05T10:00:01.000Z","level":"INFO","logger":"org.sightech.memoryvault.youtube.YtDlpService","message":"youtube msg","thread":"main"}""")
        })

        val service = LocalLogService(logFile.toString())
        val logs = service.getLogs(null, "feed", 50)

        assertEquals(1, logs.size)
        assertEquals("feed msg", logs[0].message)
    }

    @Test
    fun `getLogs respects limit`() {
        val logFile = tempDir.resolve("memoryvault.log")
        Files.writeString(logFile, buildString {
            repeat(10) { i ->
                appendLine("""{"timestamp":"2026-03-05T10:00:0$i.000Z","level":"INFO","logger":"test","message":"msg $i","thread":"main"}""")
            }
        })

        val service = LocalLogService(logFile.toString())
        val logs = service.getLogs(null, null, 3)

        assertEquals(3, logs.size)
    }

    @Test
    fun `getLogs returns empty when file does not exist`() {
        val service = LocalLogService(tempDir.resolve("nonexistent.log").toString())
        val logs = service.getLogs(null, null, 50)

        assertTrue(logs.isEmpty())
    }
}
