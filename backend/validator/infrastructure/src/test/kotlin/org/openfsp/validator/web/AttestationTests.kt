package org.openfsp.validator.web

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.openfsp.protocol.webhook.Ed25519Keys
import org.openfsp.validator.attestations.persistence.AttestationEntity
import org.openfsp.validator.attestations.persistence.AttestationRepository
import org.openfsp.validator.report.api.PurgeReports
import org.openfsp.validator.report.spi.ReportStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.Signature
import java.time.Duration
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@ActiveProfiles("development")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AttestationTests {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var repository: AttestationRepository

    @Autowired
    private lateinit var reports: ReportStore

    @Autowired
    private lateinit var purging: PurgeReports

    private val json = JsonMapper.builder().build()
    private val api by lazy { Api(port) }

    @AfterAll
    fun stop() {
        target.close()
    }

    private fun finishedRun(): String {
        val id = json.readTree(api.post("/api/runs", """{"target":"server"}""").body()).path("id").asString()
        assertEquals(202, api.post("/api/runs/$id/start", """{"base_url":"${target.url}","api_key":"ofsp_test_AttestationKey","control_endpoint":"${target.url}/conformance/control"}""").statusCode())
        assertEquals("summary", api.events("/api/runs/$id/events").last().first)
        return id
    }

    private fun issue(run: String): JsonNode {
        val issued = api.post("/api/runs/$run/attestation", "")
        assertEquals(201, issued.statusCode(), issued.body())
        return json.readTree(issued.body())
    }

    @Test
    fun `a failed run gets an attestation that says core fail and verifies with the published key`() {
        val run = finishedRun()
        val issued = issue(run)
        val id = issued.path("id").asString()
        val document = issued.path("attestation")
        assertEquals("FAIL", document.at("/verdicts/core").asString())
        assertEquals("none", document.at("/claim/level").asString())
        assertEquals(run, document.at("/report/id").asString())
        assertEquals(target.url, document.at("/target/base_url").asString())
        assertEquals("[REDACTED]", document.at("/configuration/api_key").asString())
        assertTrue(document.path("limits").items().any { it.path("reference").asString() == "conformance#8.1" })
        assertTrue(document.at("/validity/statement").asString().contains("stale when the implementation or protocol version changes"))
        assertEquals("http://validator.example/attestations/$id", issued.at("/links/page").asString())

        val again = api.post("/api/runs/$run/attestation", "")
        assertEquals(200, again.statusCode())
        assertEquals(id, json.readTree(again.body()).path("id").asString())
        assertEquals(id, json.readTree(api.get("/api/runs/$run/attestation").body()).path("id").asString())
        assertEquals(issued, json.readTree(api.get("/api/attestations/$id").body()))

        val verification = json.readTree(api.get("/api/attestations/$id/verify").body())
        assertTrue(verification.path("valid").asBoolean(), verification.toString())
        assertTrue(verification.path("signature_valid").asBoolean())
        assertTrue(verification.path("digest_matches").asBoolean())
        assertEquals("active", verification.path("key_status").asString())

        val configuration = json.readTree(api.get("/.well-known/openfsp-validator").body())
        assertEquals("http://validator.example", configuration.path("issuer").asString())
        val jws = issued.at("/signature/jws").asString()
        val key = configuration.path("attestation_keys").items().single { it.path("kid").asString() == issued.at("/signature/kid").asString() }
        assertEquals(listOf("OKP", "Ed25519", "EdDSA", "sig", "active"), listOf("kty", "crv", "alg", "use", "status").map { key.path(it).asString() })
        val (header, payload, signature) = jws.split('.')
        val offline =
            Signature.getInstance("Ed25519").run {
                initVerify(Ed25519Keys.fromRaw(key.path("x").asString()))
                update("$header.$payload".toByteArray())
                verify(Base64.getUrlDecoder().decode(signature))
            }
        assertTrue(offline)
        assertEquals(document, json.readTree(Base64.getUrlDecoder().decode(payload)))
    }

    @Test
    fun `a tampered attestation or report no longer verifies`() {
        val run = finishedRun()
        val issued = issue(run)
        val id = UUID.fromString(issued.path("id").asString())
        val stored = repository.findById(id).orElseThrow()
        val (header, payload, signature) = stored.jws.split('.')
        val forged = String(Base64.getUrlDecoder().decode(payload)).replace("\"FAIL\"", "\"PASS\"")
        repository.save(AttestationEntity(stored.id, stored.reportId, stored.issuedAt, stored.kid, "$header.${Base64.getUrlEncoder().withoutPadding().encodeToString(forged.toByteArray())}.$signature"))
        val tampered = json.readTree(api.get("/api/attestations/$id/verify").body())
        assertFalse(tampered.path("valid").asBoolean())
        assertFalse(tampered.path("signature_valid").asBoolean())

        repository.save(stored)
        val report = reports.find(UUID.fromString(run))!!
        reports.save(report.copy(body = report.body.replace("\"FAIL\"", "\"PASS\"")))
        val changed = json.readTree(api.get("/api/attestations/$id/verify").body())
        assertFalse(changed.path("valid").asBoolean())
        assertTrue(changed.path("signature_valid").asBoolean())
        assertFalse(changed.path("digest_matches").asBoolean())
    }

    @Test
    fun `an attestation outlives the purge of its report`() {
        val run = finishedRun()
        val id = issue(run).path("id").asString()
        val report = reports.find(UUID.fromString(run))!!
        reports.save(report.copy(createdAt = report.createdAt.minus(Duration.ofDays(365))))
        purging.purgeExpired()

        assertEquals(404, api.get("/api/runs/$run/report").statusCode())
        assertEquals(200, api.get("/api/attestations/$id").statusCode())
        assertEquals(200, api.get("/api/attestations/$id/badge.svg", "image/svg+xml").statusCode())
        val verification = json.readTree(api.get("/api/attestations/$id/verify").body())
        assertTrue(verification.path("signature_valid").asBoolean())
        assertEquals(listOf("the attested report is no longer stored"), verification.path("problems").items().map { it.asString() })
    }

    @Test
    fun `only a finished run can be attested`() {
        val prepared = json.readTree(api.post("/api/runs", """{"target":"server"}""").body()).path("id").asString()
        assertEquals(409, api.post("/api/runs/$prepared/attestation", "").statusCode())
        assertEquals(404, api.post("/api/runs/${UUID.randomUUID()}/attestation", "").statusCode())
        assertEquals(404, api.get("/api/attestations/${UUID.randomUUID()}").statusCode())
        assertEquals(404, api.get("/api/runs/$prepared/attestation").statusCode())
    }

    @Test
    fun `any site can read the published keys`() {
        val request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/.well-known/openfsp-validator")).header("Origin", "https://openfsp.org").build()
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode())
        assertEquals("*", response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow())
    }

    @Test
    fun `the attestation is served as a PDF and as a badge`() {
        val issued = issue(finishedRun())
        val id = issued.path("id").asString()
        val client = HttpClient.newHttpClient()
        val pdf = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/api/attestations/$id.pdf?lang=fr")).build(), HttpResponse.BodyHandlers.ofByteArray())
        assertEquals(200, pdf.statusCode())
        assertEquals("application/pdf", pdf.headers().firstValue("Content-Type").orElseThrow())
        assertEquals("%PDF-", String(pdf.body().copyOfRange(0, 5)))
        val badge = api.get("/api/attestations/$id/badge.svg", "image/svg+xml")
        assertEquals(200, badge.statusCode())
        assertTrue(badge.headers().firstValue("Content-Type").orElseThrow().startsWith("image/svg+xml"))
        assertTrue(badge.body().contains("OpenFSP core"))
        assertTrue(badge.body().contains("fail"))
        assertTrue(badge.body().contains("http://validator.example/attestations/$id"))
    }

    companion object {
        private val target = FakeTarget()

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("openfsp.validator.conformance") { TestSuite.directory.toString() }
            registry.add("openfsp.validator.runs-per-address") { "100" }
            registry.add("openfsp.validator.attestation.public-url") { "http://validator.example/" }
        }
    }
}
