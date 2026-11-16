package org.openfsp.validator.attestation.spi

import org.openfsp.validator.attestation.AttestationKey
import org.openfsp.validator.attestation.SigningKey

interface SigningKeys {
    fun signing(): SigningKey?

    fun find(kid: String): AttestationKey?

    fun published(): List<AttestationKey>
}
