package org.openfsp.validator.attestations.persistence

import org.openfsp.protocol.webhook.Ed25519Keys
import org.openfsp.protocol.webhook.KeyStatus
import org.openfsp.validator.attestation.AttestationKey
import org.openfsp.validator.attestation.SigningKey
import org.openfsp.validator.attestation.StoredAttestation
import org.openfsp.validator.attestation.spi.AttestationStore
import org.openfsp.validator.attestations.signing.AttestationKeyStore
import org.openfsp.validator.attestations.signing.Ed25519KeyMaterial
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.security.PublicKey
import java.time.Clock
import java.util.UUID

@Component
class JpaAttestationKeyStore(
    private val repository: AttestationKeyRepository,
    private val clock: Clock,
) : AttestationKeyStore {
    @Transactional(readOnly = true)
    override fun all(): List<AttestationKey> = repository.findAll().map(::toDomain)

    @Transactional(readOnly = true)
    override fun find(kid: String): AttestationKey? = repository.findByIdOrNull(kid)?.let(::toDomain)

    @Transactional
    override fun activate(
        kid: String,
        publicKey: PublicKey,
        privateKey: ByteArray?,
    ) {
        val x = Ed25519Keys.toRaw(publicKey)
        val now = clock.instant()
        val existing = repository.findByIdOrNull(kid)
        check(existing == null || existing.publicKey == x) { "the attestation key id $kid already names another public key" }
        if (existing == null) repository.save(AttestationKeyEntity(kid, x, privateKey, now, null)) else existing.retiredAt = null
        repository.flush()
        repository.retireAllBut(kid, now)
    }

    @Transactional(readOnly = true)
    override fun developmentKey(): SigningKey? =
        repository.findFirstByRetiredAtIsNullAndPrivateKeyIsNotNullOrderByCreatedAtDesc()?.let { SigningKey(it.kid, Ed25519KeyMaterial.privateKey(it.privateKey!!)) }

    private fun toDomain(entity: AttestationKeyEntity) =
        AttestationKey(entity.kid, Ed25519Keys.fromRaw(entity.publicKey), if (entity.retiredAt == null) KeyStatus.ACTIVE else KeyStatus.RETIRED, entity.createdAt)
}

@Component
class JpaAttestationStore(
    private val repository: AttestationRepository,
) : AttestationStore {
    override fun saveOrExisting(attestation: StoredAttestation): StoredAttestation =
        try {
            toDomain(repository.saveAndFlush(toEntity(attestation)))
        } catch (_: DataIntegrityViolationException) {
            findByReport(attestation.reportId) ?: throw IllegalStateException("attestation ${attestation.id} could not be stored")
        }

    @Transactional(readOnly = true)
    override fun find(id: UUID): StoredAttestation? = repository.findByIdOrNull(id)?.let(::toDomain)

    @Transactional(readOnly = true)
    override fun findByReport(reportId: UUID): StoredAttestation? = repository.findByReportId(reportId)?.let(::toDomain)

    private fun toEntity(attestation: StoredAttestation) = AttestationEntity(attestation.id, attestation.reportId, attestation.issuedAt, attestation.kid, attestation.jws)

    private fun toDomain(entity: AttestationEntity) = StoredAttestation(entity.id, entity.reportId, entity.issuedAt, entity.kid, entity.jws)
}
