package org.openfsp.validator.attestation.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.protocol.webhook.KeyStatus
import org.openfsp.validator.attestation.AttestationKey
import org.openfsp.validator.attestation.SigningKey
import org.openfsp.validator.attestation.StoredAttestation
import org.openfsp.validator.attestation.spi.AttestationSignatures
import org.openfsp.validator.attestation.spi.AttestationStore
import org.openfsp.validator.attestation.spi.SignedPayload
import org.openfsp.validator.attestation.spi.SigningKeys
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.Signature
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Stub
class InMemoryAttestationStore : AttestationStore {
    val attestations = ConcurrentHashMap<UUID, StoredAttestation>()

    override fun saveOrExisting(attestation: StoredAttestation): StoredAttestation = findByReport(attestation.reportId) ?: attestation.also { attestations[it.id] = it }

    override fun find(id: UUID): StoredAttestation? = attestations[id]

    override fun findByReport(reportId: UUID): StoredAttestation? = attestations.values.firstOrNull { it.reportId == reportId }
}

@Stub
class GeneratedSigningKeys(
    private val kid: String = "stub-key",
    enabled: Boolean = true,
) : SigningKeys {
    private val pair = KeyPairGenerator.getInstance(ED25519).generateKeyPair()
    private val signing = SigningKey(kid, pair.private).takeIf { enabled }
    private val published = AttestationKey(kid, pair.public, KeyStatus.ACTIVE, Instant.EPOCH)

    override fun signing(): SigningKey? = signing

    override fun find(kid: String): AttestationKey? = published.takeIf { it.kid == kid }

    override fun published(): List<AttestationKey> = listOf(published)

    private companion object {
        const val ED25519 = "Ed25519"
    }
}

@Stub
class DottedAttestationSignatures : AttestationSignatures {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    override fun sign(
        payload: ByteArray,
        key: SigningKey,
    ): String {
        val input = "${encoder.encodeToString(key.kid.toByteArray())}.${encoder.encodeToString(payload)}"
        val signature =
            Signature.getInstance(ED25519).run {
                initSign(key.privateKey)
                update(input.toByteArray())
                sign()
            }
        return "$input.${encoder.encodeToString(signature)}"
    }

    override fun parse(signed: String): SignedPayload? {
        val parts = signed.split('.').takeIf { it.size == PARTS } ?: return null
        return runCatching {
            val kid = String(decoder.decode(parts[0]))
            val payload = decoder.decode(parts[1])
            val signature = decoder.decode(parts[2])
            object : SignedPayload {
                override val kid: String = kid
                override val payload: ByteArray = payload

                override fun verifiedBy(publicKey: PublicKey): Boolean =
                    runCatching {
                        Signature.getInstance(ED25519).run {
                            initVerify(publicKey)
                            update("${parts[0]}.${parts[1]}".toByteArray())
                            verify(signature)
                        }
                    }.getOrDefault(false)
            }
        }.getOrNull()
    }

    private companion object {
        const val ED25519 = "Ed25519"
        const val PARTS = 3
    }
}
