package org.openfsp.validator.web.attestations.persistence

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
@Table(name = "attestation_keys")
class AttestationKeyEntity(
    @Id
    val kid: String,
    @Column(name = "public_key", nullable = false)
    val publicKey: String,
    @Column(name = "private_key")
    val privateKey: ByteArray?,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "retired_at")
    var retiredAt: Instant?,
)

interface AttestationKeyRepository : JpaRepository<AttestationKeyEntity, String> {
    fun findFirstByRetiredAtIsNullAndPrivateKeyIsNotNullOrderByCreatedAtDesc(): AttestationKeyEntity?

    @Modifying
    @Query("update AttestationKeyEntity stored set stored.retiredAt = :at where stored.retiredAt is null and stored.kid <> :kid")
    fun retireAllBut(
        @Param("kid") kid: String,
        @Param("at") at: Instant,
    ): Int
}

@Entity
@Table(name = "attestations")
class AttestationEntity(
    @Id
    val id: UUID,
    @Column(name = "report_id", nullable = false)
    val reportId: UUID,
    @Column(name = "issued_at", nullable = false)
    val issuedAt: Instant,
    @Column(nullable = false)
    val kid: String,
    @Column(nullable = false)
    val jws: String,
)

interface AttestationRepository : JpaRepository<AttestationEntity, UUID> {
    fun findByReportId(reportId: UUID): AttestationEntity?
}
