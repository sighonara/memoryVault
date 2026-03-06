package org.sightech.memoryvault.scheduling.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

enum class JobType { RSS_FETCH, YT_SYNC, BOOKMARK_ARCHIVE }

enum class JobStatus { PENDING, RUNNING, SUCCESS, FAILED }

enum class TriggerSource { SCHEDULED, MANUAL }

@Entity
@Table(name = "sync_jobs")
class SyncJob(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val type: JobType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: JobStatus = JobStatus.PENDING,

    @Column(name = "started_at", nullable = false)
    val startedAt: Instant = Instant.now(),

    @Column(name = "completed_at")
    var completedAt: Instant? = null,

    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "triggered_by", nullable = false, length = 20)
    val triggeredBy: TriggerSource,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var metadata: String? = null
)
