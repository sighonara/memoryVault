package org.sightech.memoryvault.crypto

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class EncryptionServiceTest {

    private val service = EncryptionService("deadbeefdeadbeefdeadbeefdeadbeef")

    @Test
    fun `encrypt and decrypt round-trips`() {
        val plaintext = """{"accessKey": "abc123", "secretKey": "xyz789"}"""
        val encrypted = service.encrypt(plaintext)
        assertNotEquals(plaintext, encrypted)
        assertEquals(plaintext, service.decrypt(encrypted))
    }

    @Test
    fun `different plaintexts produce different ciphertexts`() {
        val a = service.encrypt("secret-a")
        val b = service.encrypt("secret-b")
        assertNotEquals(a, b)
    }

    @Test
    fun `decrypt with wrong key throws`() {
        val encrypted = service.encrypt("my-secret")
        val otherService = EncryptionService("aaaabbbbccccddddaaaabbbbccccdddd")
        assertThrows<Exception> {
            otherService.decrypt(encrypted)
        }
    }

    @Test
    fun `empty string round-trips`() {
        val encrypted = service.encrypt("")
        assertEquals("", service.decrypt(encrypted))
    }
}
