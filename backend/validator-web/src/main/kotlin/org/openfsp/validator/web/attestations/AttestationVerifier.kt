package org.openfsp.validator.web.attestations

import org.openfsp.validator.report.Reports
import org.openfsp.validator.web.reports.CanonicalJson
import tools.jackson.databind.JsonNode

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
) {
    fun verify(
        jws: String,
        reportBody: String?,
        expectedId: String? = null,
        expectedReportId: String? = null,
    ): AttestationVerification {
        val problems = mutableListOf<String>()
        val parsed = CompactJws.parse(jws)
        if (parsed == null) problems += "the signature is not a JWS in compact serialization"
        val kid = parsed?.kid
        val key = kid?.let(keys)
        if (parsed != null && key == null) problems += "the key ${kid ?: "(none)"} is not published by this validator"
        val signatureValid = parsed != null && key != null && parsed.verifiedBy(key.publicKey)
        if (key != null && !signatureValid) problems += "the signature does not verify with key $kid"

        val document = parsed?.let { runCatching { Reports.json.readTree(it.payload) }.getOrNull() }?.takeIf(JsonNode::isObject)
        if (parsed != null && document == null) problems += "the payload is not a JSON object"
        if (document != null && document.path("type").stringValue(null) != Attestation.TYPE) problems += "the payload is not an OpenFSP conformance attestation"
        if (document != null && expectedId != null && document.path("id").stringValue(null) != expectedId) problems += "the payload names another attestation"
        if (document != null && expectedReportId != null && document.at("/report/id").stringValue(null) != expectedReportId) problems += "the payload names another report"

        val reportDigest = reportBody?.let { runCatching { CanonicalJson.sha256(Reports.json.readTree(it)) }.getOrNull() }
        val claimedDigest = document?.at("/report/digest")?.stringValue(null)
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
