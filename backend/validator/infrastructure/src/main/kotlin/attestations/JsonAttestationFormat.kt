package org.openfsp.validator.attestations

import org.openfsp.validator.attestation.Attestation
import org.openfsp.validator.attestation.spi.AttestationFormat
import org.openfsp.validator.shared.json.CanonicalJson
import org.openfsp.validator.shared.json.ValidatorJson
import org.springframework.stereotype.Component
import tools.jackson.module.kotlin.readValue

@Component
class JsonAttestationFormat : AttestationFormat {
    override fun canonical(attestation: Attestation): ByteArray = CanonicalJson.of(ValidatorJson.documents.valueToTree(attestation)).toByteArray(Charsets.UTF_8)

    override fun read(payload: ByteArray): Attestation = ValidatorJson.documents.readValue(payload)
}
