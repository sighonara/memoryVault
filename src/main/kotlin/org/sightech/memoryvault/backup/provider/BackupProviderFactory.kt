package org.sightech.memoryvault.backup.provider

import org.sightech.memoryvault.backup.entity.BackupProviderEntity
import org.sightech.memoryvault.backup.entity.BackupProviderType
import org.sightech.memoryvault.crypto.EncryptionService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class BackupProviderFactory(
    private val encryptionService: EncryptionService,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun create(entity: BackupProviderEntity): BackupProvider {
        val credentialsJson = encryptionService.decrypt(entity.credentialsEncrypted)
        val creds = objectMapper.readTree(credentialsJson)

        return when (entity.type) {
            BackupProviderType.INTERNET_ARCHIVE -> {
                val accessKey = creds.get("accessKey")?.stringValue()
                    ?: throw IllegalStateException("Missing accessKey in provider credentials")
                val secretKey = creds.get("secretKey")?.stringValue()
                    ?: throw IllegalStateException("Missing secretKey in provider credentials")
                InternetArchiveProvider(accessKey, secretKey)
            }
            BackupProviderType.CUSTOM -> {
                throw UnsupportedOperationException("Custom backup providers are not yet implemented")
            }
        }
    }
}
