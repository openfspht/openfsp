package org.openfsp.validator.reports.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "reports")
class ReportEntity(
    @Id
    val id: UUID,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(nullable = false)
    val target: String,
    @Column(name = "base_url", nullable = false)
    val baseUrl: String,
    @Column(nullable = false)
    val core: String,
    @Column(name = "suite_version", nullable = false)
    val suiteVersion: String,
    @Column(nullable = false)
    val digest: String,
    @Column(nullable = false)
    val body: String,
)

interface ReportRepository : JpaRepository<ReportEntity, UUID> {
    @Modifying
    @Query("delete from ReportEntity report where report.createdAt < :before")
    fun deleteCreatedBefore(
        @Param("before") before: Instant,
    ): Int
}
