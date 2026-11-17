package org.openfsp.validator.attestations.signing

import org.openfsp.validator.attestation.Attestation
import org.openfsp.validator.attestation.spi.AttestationFormat
import org.openfsp.validator.shared.json.CanonicalJson
import org.openfsp.validator.shared.json.ValidatorJson
import tools.jackson.module.kotlin.readValue

object JsonAttestationFormat : AttestationFormat {
    override fun canonical(attestation: Attestation): ByteArray = CanonicalJson.of(ValidatorJson.documents.valueToTree(attestation)).toByteArray(Charsets.UTF_8)

    override fun read(payload: ByteArray): Attestation = ValidatorJson.documents.readValue(payload)
}
