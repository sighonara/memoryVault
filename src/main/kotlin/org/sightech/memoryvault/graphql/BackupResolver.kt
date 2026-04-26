package org.sightech.memoryvault.graphql

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.backup.entity.BackupProviderEntity
import org.sightech.memoryvault.backup.entity.BackupProviderType
import org.sightech.memoryvault.backup.service.BackupService
import org.sightech.memoryvault.youtube.entity.Video
import org.slf4j.LoggerFactory
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@Controller
class BackupResolver(
    private val backupService: BackupService,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @QueryMapping
    fun backupProviders(): List<BackupProviderEntity> {
        val userId = CurrentUser.userId()
        return backupService.getProviders(userId)
    }

    @QueryMapping
    fun backupStats(): Map<String, Any> {
        val userId = CurrentUser.userId()
        val stats = backupService.getStats(userId)
        return mapOf(
            "total" to stats.total,
            "backedUp" to stats.backedUp,
            "pending" to stats.pending,
            "lost" to stats.lost,
            "failed" to stats.failed
        )
    }

    @SchemaMapping(typeName = "Video", field = "backupStatus")
    fun backupStatus(video: Video): String? {
        return backupService.getBackupStatusForVideo(video.id)
    }

    @MutationMapping
    fun addBackupProvider(@Argument input: Map<String, Any>): BackupProviderEntity {
        val userId = CurrentUser.userId()
        val type = BackupProviderType.valueOf(input["type"] as String)
        val name = input["name"] as String
        val accessKey = input["accessKey"] as String
        val secretKey = input["secretKey"] as String
        val isPrimary = input["isPrimary"] as Boolean
        val credentialsJson = objectMapper.writeValueAsString(mapOf("accessKey" to accessKey, "secretKey" to secretKey))
        log.info("Adding backup provider name={} type={}", name, type)
        return backupService.addProvider(userId, type, name, credentialsJson, isPrimary)
    }

    @MutationMapping
    fun updateBackupProvider(@Argument id: UUID, @Argument input: Map<String, Any>): BackupProviderEntity? {
        val userId = CurrentUser.userId()
        val name = input["name"] as? String
        val accessKey = input["accessKey"] as? String
        val secretKey = input["secretKey"] as? String
        val credentialsJson = if (accessKey != null && secretKey != null) {
            objectMapper.writeValueAsString(mapOf("accessKey" to accessKey, "secretKey" to secretKey))
        } else null
        log.info("Updating backup provider id={}", id)
        return backupService.updateProvider(id, userId, name, credentialsJson)
    }

    @MutationMapping
    fun deleteBackupProvider(@Argument id: UUID): Boolean {
        val userId = CurrentUser.userId()
        log.info("Deleting backup provider id={}", id)
        return backupService.deleteProvider(id, userId)
    }

    @MutationMapping
    fun triggerBackfill(): Int {
        val userId = CurrentUser.userId()
        val count = backupService.triggerBackfill(userId)
        log.info("Backfill triggered: {} videos queued", count)
        return count
    }
}
