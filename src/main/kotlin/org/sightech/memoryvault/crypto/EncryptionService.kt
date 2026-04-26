package org.sightech.memoryvault.crypto

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.encrypt.Encryptors
import org.springframework.stereotype.Service

@Service
class EncryptionService(
    @Value("\${MEMORYVAULT_ENCRYPTION_KEY:default-dev-key-do-not-use}") private val encryptionKey: String
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val salt = "memoryvault"

    fun encrypt(plaintext: String): String {
        val encryptor = Encryptors.text(encryptionKey, bytesToHex(salt.toByteArray()))
        return encryptor.encrypt(plaintext)
    }

    fun decrypt(ciphertext: String): String {
        val encryptor = Encryptors.text(encryptionKey, bytesToHex(salt.toByteArray()))
        return encryptor.decrypt(ciphertext)
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }
}
