package org.openfsp.validator.attestation.spi

import org.openfsp.validator.attestation.SigningKey
import java.security.PublicKey

interface SignedPayload {
    val kid: String?
    val payload: ByteArray

    fun verifiedBy(publicKey: PublicKey): Boolean
}

interface AttestationSignatures {
    fun sign(
        payload: ByteArray,
        key: SigningKey,
    ): String

    fun parse(signed: String): SignedPayload?
}
