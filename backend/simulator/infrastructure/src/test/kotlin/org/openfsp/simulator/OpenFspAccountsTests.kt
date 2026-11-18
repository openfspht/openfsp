package org.openfsp.simulator

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openfsp.simulator.control.web.ControlController
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "openfsp.simulator.response-delay=0ms",
        "openfsp.simulator.settle-after=0ms",
        "server.address=127.0.0.1",
    ],
)
@ActiveProfiles("conformance")
class OpenFspAccountsTests {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var control: ControlController

    private val client by lazy { localClient(port) }

    @BeforeEach
    fun reset() = control.reset()

    @Test
    fun `the published key and a run never see each other's payments`() {
        val run = prepare()
        val ofRun = create(run.apiKey, "acct-run")
        val published = create(API_KEY, "acct-published")

        assertEquals(200, read(run.apiKey, ofRun).statusCode.value())
        assertEquals(404, read(API_KEY, ofRun).statusCode.value())
        assertEquals(404, read(run.apiKey, published).statusCode.value())
        assertEquals(0, found(API_KEY, "acct-run"))
        assertEquals(0, found(run.apiKey, "acct-published"))
        assertEquals(404, controlState(API_KEY, "acct-run"))
        assertEquals(404, controlState(run.apiKey, "acct-published"))
        assertEquals(404, resolve(API_KEY, ofRun))
    }

    @Test
    fun `two runs never see each other's payments, statements or control`() {
        val first = prepare()
        val second = prepare()
        val payment = create(first.apiKey, "acct-first")

        assertEquals(404, read(second.apiKey, payment).statusCode.value())
        assertEquals(0, found(second.apiKey, "acct-first"))
        assertEquals(404, controlState(second.apiKey, "acct-first"))
        assertEquals(404, resolve(second.apiKey, payment))
        assertEquals(1, statement(first.apiKey))
        assertEquals(0, statement(second.apiKey))
        assertEquals(0, statement(API_KEY))
    }

    @Test
    fun `a scenario declared in one run never applies to the same reference in another run`() {
        val first = prepare()
        val second = prepare()

        assertEquals(204, declare(first.apiKey, "acct-shared", "PENDING_FOREVER"))
        val settled = create(second.apiKey, "acct-shared")
        val pending = create(first.apiKey, "acct-shared")

        assertEquals("succeeded", read(second.apiKey, settled).body!!["status"])
        assertEquals("pending", read(first.apiKey, pending).body!!["status"])
    }

    @Test
    fun `the restricted key of a run reads the run payments but keeps its own idempotency records and no statements`() {
        val run = prepare()
        val payment = create(run.apiKey, "acct-restricted")

        assertEquals(200, read(run.restrictedKey, payment).statusCode.value())
        assertEquals(1, found(run.restrictedKey, "acct-restricted"))
        val again = createResponse(run.restrictedKey, "acct-restricted")
        assertEquals(409, again.statusCode.value())
        assertEquals(null, again.headers.getFirst("Idempotent-Replay"))
        assertEquals(403, client.get().uri(statementUri()).header(AUTHORIZATION, "Bearer ${run.restrictedKey}").retrieve().toBodilessEntity().statusCode.value())
        assertEquals(401, controlState(run.restrictedKey, "acct-restricted"))
    }

    private data class Run(
        val apiKey: String,
        val restrictedKey: String,
    )

    private fun prepare(): Run {
        val fixtures = client.post().uri("/mock_openfsp/conformance").retrieve().body(Map::class.java)!!
        return Run(fixtures["api_key"] as String, (fixtures["inputs"] as Map<*, *>)["restricted-key"] as String)
    }

    private fun createResponse(
        key: String,
        reference: String,
    ): ResponseEntity<Map<*, *>> =
        client
            .post()
            .uri("/mock_openfsp/v1/payments")
            .header(AUTHORIZATION, "Bearer $key")
            .header("Idempotency-Key", "key-$reference")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("reference" to reference, "amount" to mapOf("amount" to 125000, "currency" to "HTG"), "provider" to "mock_openfsp"))
            .retrieve()
            .toEntity(Map::class.java)

    private fun create(
        key: String,
        reference: String,
    ): String = createResponse(key, reference).body!!["id"] as String

    private fun read(
        key: String,
        paymentId: String,
    ): ResponseEntity<Map<*, *>> = client.get().uri("/mock_openfsp/v1/payments/{id}", paymentId).header(AUTHORIZATION, "Bearer $key").retrieve().toEntity(Map::class.java)

    private fun found(
        key: String,
        reference: String,
    ): Int = (client.get().uri("/mock_openfsp/v1/payments?reference={reference}", reference).header(AUTHORIZATION, "Bearer $key").retrieve().body(Map::class.java)!!["data"] as List<*>).size

    private fun declare(
        key: String,
        reference: String,
        scenario: String,
    ): Int =
        client
            .put()
            .uri("$CONTROL/references/{reference}/scenario", reference)
            .header(AUTHORIZATION, "Bearer $key")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("scenario" to scenario))
            .retrieve()
            .toBodilessEntity()
            .statusCode
            .value()

    private fun controlState(
        key: String,
        reference: String,
    ): Int = client.get().uri("$CONTROL/references/{reference}", reference).header(AUTHORIZATION, "Bearer $key").retrieve().toBodilessEntity().statusCode.value()

    private fun resolve(
        key: String,
        paymentId: String,
    ): Int =
        client
            .post()
            .uri("$CONTROL/payments/{id}/resolve", paymentId)
            .header(AUTHORIZATION, "Bearer $key")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("outcome" to "succeeded"))
            .retrieve()
            .toBodilessEntity()
            .statusCode
            .value()

    private fun statementUri() = "/mock_openfsp/v1/statements?provider=mock_openfsp&business_date=${LocalDate.now(ZoneId.of("America/Port-au-Prince"))}"

    private fun statement(key: String): Int {
        repeat(ATTEMPTS) {
            val entries = client.get().uri(statementUri()).header(AUTHORIZATION, "Bearer $key").retrieve().body(Map::class.java)!!["data"] as List<*>
            if (entries.isNotEmpty()) return entries.size
            Thread.sleep(PAUSE_MILLIS)
        }
        return 0
    }

    private companion object {
        const val CONTROL = "/mock_openfsp/conformance/control"
        const val AUTHORIZATION = "Authorization"
        const val API_KEY = "ofsp_test_MockOpenFspKeyPublishedForTheSimulator"
        const val ATTEMPTS = 25
        const val PAUSE_MILLIS = 20L
    }
}
