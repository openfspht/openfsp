package org.openfsp.validator.attestation

import org.openfsp.validator.attestation.spi.AttestationSignatures
import org.openfsp.validator.json.JsonObject
import org.openfsp.validator.json.at
import org.openfsp.validator.json.spi.JsonCodec
import org.openfsp.validator.json.string
import org.openfsp.validator.report.spi.ReportFormat

data class AttestationVerification(
    val valid: Boolean,
    val signatureValid: Boolean,
    val keyKnown: Boolean,
    val keyId: String?,
    val keyStatus: String?,
    val digestMatches: Boolean,
    val reportDigest: String?,
    val problems: List<String>,
)

class AttestationVerifier(
    private val keys: (String) -> AttestationKey?,
    private val signatures: AttestationSignatures,
    private val json: JsonCodec,
    private val reports: ReportFormat,
) {
    fun verify(
        jws: String,
        reportBody: String?,
        expectedId: String? = null,
        expectedReportId: String? = null,
    ): AttestationVerification {
        val problems = mutableListOf<String>()
        val parsed = signatures.parse(jws)
        if (parsed == null) problems += "the signature is not a JWS in compact serialization"
        val kid = parsed?.kid
        val key = kid?.let(keys)
        if (parsed != null && key == null) problems += "the key ${kid ?: "(none)"} is not published by this validator"
        val signatureValid = parsed != null && key != null && parsed.verifiedBy(key.publicKey)
        if (key != null && !signatureValid) problems += "the signature does not verify with key $kid"

        val document = parsed?.let { json.parse(it.payload) } as? JsonObject
        if (parsed != null && document == null) problems += "the payload is not a JSON object"
        if (document != null && document.at("/type")?.string != Attestation.TYPE) problems += "the payload is not an OpenFSP conformance attestation"
        if (document != null && expectedId != null && document.at("/id")?.string != expectedId) problems += "the payload names another attestation"
        if (document != null && expectedReportId != null && document.at("/report/id")?.string != expectedReportId) problems += "the payload names another report"

        val reportDigest = reportBody?.let(reports::digest)
        val claimedDigest = document?.at("/report/digest")?.string
        val digestMatches = reportDigest != null && claimedDigest == reportDigest
        when {
            reportBody == null -> problems += "the attested report is no longer stored"
            !digestMatches -> problems += "the report digest does not match the stored report"
        }
        return AttestationVerification(
            valid = problems.isEmpty(),
            signatureValid = signatureValid,
            keyKnown = key != null,
            keyId = kid,
            keyStatus = key?.status?.wire,
            digestMatches = digestMatches,
            reportDigest = reportDigest,
            problems = problems,
        )
    }
}
