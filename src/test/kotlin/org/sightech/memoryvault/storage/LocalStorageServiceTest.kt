package org.sightech.memoryvault.storage

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalStorageServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var service: LocalStorageService

    @BeforeEach
    fun setUp() {
        service = LocalStorageService(tempDir.toString())
    }

    @Test
    fun `store saves file and returns path`() {
        val content = "test content"
        val result = service.store("videos/test.mp4", content.byteInputStream())

        assertTrue(result.endsWith("videos/test.mp4"))
        assertTrue(service.exists("videos/test.mp4"))
    }

    @Test
    fun `retrieve returns stored content`() {
        val content = "hello world"
        service.store("test.txt", content.byteInputStream())

        val retrieved = service.retrieve("test.txt").bufferedReader().readText()
        assertEquals(content, retrieved)
    }

    @Test
    fun `delete removes file`() {
        service.store("test.txt", "content".byteInputStream())
        assertTrue(service.exists("test.txt"))

        service.delete("test.txt")
        assertFalse(service.exists("test.txt"))
    }

    @Test
    fun `exists returns false for missing file`() {
        assertFalse(service.exists("nonexistent.txt"))
    }

    @Test
    fun `store creates parent directories`() {
        service.store("a/b/c/deep.txt", "deep".byteInputStream())
        assertTrue(service.exists("a/b/c/deep.txt"))
    }
}
