package org.sightech.memoryvault.bookmark.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "ingest_previews")
class IngestPreviewEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preview_data", nullable = false, columnDefinition = "jsonb")
    var previewData: String,

    @Column(nullable = false)
    var committed: Boolean = false,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant = Instant.now().plusSeconds(3600)
)
