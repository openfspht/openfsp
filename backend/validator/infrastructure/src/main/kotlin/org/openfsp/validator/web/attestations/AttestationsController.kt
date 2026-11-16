package org.openfsp.validator.web.attestations

import org.openfsp.validator.attestation.AttestationVerification
import org.openfsp.validator.attestation.IssuedAttestation
import org.openfsp.validator.attestation.api.IssueAttestations
import org.openfsp.validator.attestation.api.ReadAttestations
import org.openfsp.validator.attestation.api.VerifyAttestations
import org.springframework.http.CacheControl
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import java.net.URI
import java.time.Duration
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
    private val issuing: IssueAttestations,
    private val attestations: ReadAttestations,
    private val verifying: VerifyAttestations,
) {
    @PostMapping("/api/runs/{id}/attestation")
    fun issue(
        @PathVariable id: UUID,
    ): ResponseEntity<AttestationResource> {
        val issued = issuing.issue(id)
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
    ): AttestationVerification = verifying.verify(id)

    @GetMapping("$BASE/{id}.pdf", produces = [MediaType.APPLICATION_PDF_VALUE])
    fun pdf(
        @PathVariable id: UUID,
        @RequestParam(required = false) lang: String?,
        @RequestHeader(HttpHeaders.ACCEPT_LANGUAGE, required = false) acceptLanguage: String?,
    ): ResponseEntity<ByteArray> {
        val issued = attestations.find(id)
        val french = (lang ?: acceptLanguage.orEmpty()).trim().lowercase().startsWith(FRENCH)
        val body = AttestationPdf.render(issued.document, issued.stored.kid, attestations.pageUrl(id), if (french) PdfLocale.FR else PdfLocale.EN)
        return ResponseEntity
            .ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename("openfsp-attestation-$id.pdf").build().toString())
            .header(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE)
            .body(body)
    }

    @GetMapping("$BASE/{id}/badge.svg")
    fun badge(
        @PathVariable id: UUID,
    ): ResponseEntity<String> {
        val document = attestations.find(id).document
        return ResponseEntity
            .ok()
            .contentType(SVG)
            .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
            .body(AttestationBadge.svg(document.verdicts.core, document.claim.protocolVersion, attestations.pageUrl(id)))
    }

    @CrossOrigin(origins = ["*"], methods = [RequestMethod.GET])
    @GetMapping(WELL_KNOWN)
    fun configuration(): ValidatorConfigurationResource = ValidatorConfigurationResource(attestations.publicUrl, attestations.publishedKeys().map(AttestationJwk::of))

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
        private const val FRENCH = "fr"
        private val SVG = MediaType.parseMediaType("image/svg+xml;charset=UTF-8")
    }
}
