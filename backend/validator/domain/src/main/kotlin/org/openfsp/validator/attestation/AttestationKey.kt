package org.openfsp.validator.attestation

import org.openfsp.protocol.webhook.KeyStatus
import java.security.PrivateKey
import java.security.PublicKey
import java.time.Instant

data class AttestationKey(
    val kid: String,
    val publicKey: PublicKey,
    val status: KeyStatus,
    val createdAt: Instant,
)

data class SigningKey(
    val kid: String,
    val privateKey: PrivateKey,
)
