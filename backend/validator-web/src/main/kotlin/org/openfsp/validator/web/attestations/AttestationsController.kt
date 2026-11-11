package org.openfsp.validator.web.attestations

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import java.net.URI
import java.util.UUID

data class AttestationLinks(
    val page: String,
    val verify: String,
    val pdf: String,
    val badge: String,
    val report: String,
    val keys: String,
)

data class AttestationResource(
    val id: String,
    val attestation: JsonNode,
    val signature: AttestationSignature,
    val links: AttestationLinks,
)

data class AttestationSignature(
    val format: String,
    val alg: String,
    val kid: String,
    val jws: String,
)

data class ValidatorConfigurationResource(
    val issuer: String,
    val attestationKeys: List<AttestationJwk>,
)

@RestController
class AttestationsController(
    private val attestations: AttestationService,
    private val keys: AttestationKeys,
) {
    @PostMapping("/api/runs/{id}/attestation")
    fun issue(
        @PathVariable id: UUID,
    ): ResponseEntity<AttestationResource> {
        val issued = attestations.issue(id)
        val resource = resourceOf(issued)
        return ResponseEntity
            .status(if (issued.created) HttpStatus.CREATED else HttpStatus.OK)
            .location(URI.create("$BASE/${issued.stored.id}"))
            .body(resource)
    }

    @GetMapping("/api/runs/{id}/attestation")
    fun forRun(
        @PathVariable id: UUID,
    ): AttestationResource = resourceOf(attestations.forRun(id))

    @GetMapping("$BASE/{id}")
    fun show(
        @PathVariable id: UUID,
    ): AttestationResource = resourceOf(attestations.find(id))

    @GetMapping("$BASE/{id}/verify")
    fun verify(
        @PathVariable id: UUID,
    ): AttestationVerification = attestations.verify(id)

    @GetMapping(WELL_KNOWN)
    fun configuration(): ValidatorConfigurationResource = ValidatorConfigurationResource(attestations.publicUrl, keys.published().map(AttestationJwk::of))

    private fun resourceOf(issued: IssuedAttestation): AttestationResource {
        val id = issued.stored.id
        return AttestationResource(
            id = id.toString(),
            attestation = issued.payload,
            signature = AttestationSignature(FORMAT, ALG, issued.stored.kid, issued.stored.jws),
            links =
                AttestationLinks(
                    page = attestations.pageUrl(id),
                    verify = "$BASE/$id/verify",
                    pdf = "$BASE/$id.pdf",
                    badge = "$BASE/$id/badge.svg",
                    report = "/api/runs/${issued.stored.reportId}/report",
                    keys = WELL_KNOWN,
                ),
        )
    }

    companion object {
        const val BASE = "/api/attestations"
        const val WELL_KNOWN = "/.well-known/openfsp-validator"
        const val FORMAT = "jws-compact"
        const val ALG = "EdDSA"
    }
}
