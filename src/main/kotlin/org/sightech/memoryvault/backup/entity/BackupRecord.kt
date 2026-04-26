package org.sightech.memoryvault.backup.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "backup_records")
class BackupRecord(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "video_id", nullable = false)
    val videoId: UUID,

    @Column(name = "provider_id", nullable = false)
    val providerId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: BackupStatus = BackupStatus.PENDING,

    @Column(name = "external_url", length = 2048)
    var externalUrl: String? = null,

    @Column(name = "external_id", length = 255)
    var externalId: String? = null,

    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,

    @Column(name = "last_health_check_at")
    var lastHealthCheckAt: Instant? = null,

    @Column(name = "health_check_failures", nullable = false)
    var healthCheckFailures: Int = 0,

    val createdAt: Instant = Instant.now(),

    var updatedAt: Instant = Instant.now()
)
