package org.openfsp.validator.attestation.spi

import org.openfsp.validator.attestation.Attestation

interface AttestationFormat {
    fun canonical(attestation: Attestation): ByteArray

    fun read(payload: ByteArray): Attestation
}
