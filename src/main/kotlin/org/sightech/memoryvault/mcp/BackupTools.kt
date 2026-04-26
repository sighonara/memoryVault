package org.sightech.memoryvault.mcp

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.backup.service.BackupService
import org.slf4j.LoggerFactory
import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class BackupTools(private val backupService: BackupService) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Tool(description = "Get backup status for a specific video across all configured providers. Shows whether the video is backed up, pending, lost, or failed on each provider. Use when checking if a video has been backed up.")
    fun getBackupStatus(videoId: String): String {
        val records = backupService.getBackupRecords(UUID.fromString(videoId))
        if (records.isEmpty()) return "No backup records for this video."

        val lines = records.map { r ->
            "- ${r.status}: ${r.externalUrl ?: "(no URL)"}" +
                (if (r.errorMessage != null) " — error: ${r.errorMessage}" else "") +
                (if (r.healthCheckFailures > 0) " — health check failures: ${r.healthCheckFailures}" else "")
        }
        return "${records.size} backup record(s):\n${lines.joinToString("\n")}"
    }

    @Tool(description = "List all configured backup providers (without credentials). Shows provider type, name, and whether it's the primary. Use when checking what backup destinations are configured.")
    fun listBackupProviders(): String {
        val userId = CurrentUser.userId()
        val providers = backupService.getProviders(userId)
        if (providers.isEmpty()) return "No backup providers configured."

        val lines = providers.map { p ->
            "- ${p.name} (${p.type}${if (p.isPrimary) ", primary" else ""}) — id: ${p.id}"
        }
        return "${providers.size} provider(s):\n${lines.joinToString("\n")}"
    }

    @Tool(description = "Queue all downloaded videos that haven't been backed up yet for backup to the primary provider. Returns the count of videos queued. Use when the user wants to backfill existing videos.")
    fun triggerBackfill(): String {
        val userId = CurrentUser.userId()
        val count = backupService.triggerBackfill(userId)
        return if (count > 0) "Queued $count video(s) for backup." else "All downloaded videos are already backed up or no primary provider configured."
    }
}
