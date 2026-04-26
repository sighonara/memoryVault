package org.sightech.memoryvault.backup.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "backup_providers")
class BackupProviderEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val type: BackupProviderType,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(name = "credentials_encrypted", nullable = false, columnDefinition = "TEXT")
    var credentialsEncrypted: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var config: String? = "{}",

    @Column(name = "is_primary", nullable = false)
    var isPrimary: Boolean = true,

    val createdAt: Instant = Instant.now(),

    var updatedAt: Instant = Instant.now(),

    var deletedAt: Instant? = null,

    @Version
    val version: Long = 0
)
