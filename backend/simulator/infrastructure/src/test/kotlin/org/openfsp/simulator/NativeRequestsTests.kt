package org.openfsp.simulator

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openfsp.protocol.ResourceIds
import org.openfsp.simulator.controllers.ControlController
import org.openfsp.simulator.operator.control.api.ControlSimulation
import org.openfsp.simulator.operator.scenario.Scenario
import org.openfsp.simulator.operator.space.SpaceId
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "simulator.response-delay=0ms",
        "simulator.settle-after=0ms",
        "simulator.hang-duration=50ms",
        "server.address=127.0.0.1",
        "simulator.http-callback-hosts=gateway",
    ],
)
class NativeRequestsTests {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var control: ControlController

    @Autowired
    private lateinit var state: ControlSimulation

    private val client by lazy { localClient(port) }

    @BeforeEach
    fun reset() {
        control.reset(SpaceId.DEFAULT)
    }

    private fun valid(reference: String): Map<String, Any?> =
        mapOf(
            "reference" to reference,
            "amount" to mapOf("amount" to 125000, "currency" to "HTG"),
            "provider" to "mock_openfsp",
        )

    private fun create(
        body: Any,
        key: String,
        operator: String = "mock_openfsp",
        apiKey: String = API_KEY,
    ): ResponseEntity<Map<*, *>> =
        client
            .post()
            .uri("/$operator/v1/payments")
            .header("Authorization", "Bearer $apiKey")
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .toEntity(Map::class.java)

    private fun ResponseEntity<Map<*, *>>.type(): String = body!!["type"].toString().substringAfterLast('/')

    @Suppress("UNCHECKED_CAST")
    private fun ResponseEntity<Map<*, *>>.fields(): List<Pair<String, String>> = (body!!["errors"] as List<Map<String, Any?>>).map { it["field"].toString() to it["code"].toString() }

    @Test
    fun `a rejected body is recorded under its key so a corrected body is refused and the problem replays`() {
        val invalid = valid("fix-1") - "amount"

        val first = create(invalid, "fix-key")
        val corrected = create(valid("fix-1"), "fix-key")
        val replayed = create(invalid, "fix-key")

        assertEquals(400, first.statusCode.value())
        assertEquals("missing-field", first.type())
        assertEquals(422, corrected.statusCode.value())
        assertEquals("idempotency-key-reused", corrected.type())
        assertEquals(400, replayed.statusCode.value())
        assertEquals("true", replayed.headers.getFirst("Idempotent-Replay"))
        assertEquals(first.body, replayed.body)
        assertEquals(first.body!!["request_id"], replayed.headers.getFirst("Request-Id"))
    }

    private fun rejected(body: Map<String, Any?>): Pair<String, List<Pair<String, String>>> = create(body, "key-${body.hashCode()}").let { it.type() to it.fields() }

    @Test
    fun `metadata follows the data model limits`() {
        val atLimit = (1..20).associate { "k".repeat(38) + "%02d".format(it) to "v".repeat(500) }
        assertEquals(201, create(valid("meta-ok") + ("metadata" to atLimit), "meta-ok").statusCode.value())

        assertEquals("invalid-field" to listOf("/metadata" to "too_many"), rejected(valid("meta-1") + ("metadata" to (0..20).associate { "k$it" to "v" })))
        assertEquals("invalid-field" to listOf("/metadata/a b" to "malformed"), rejected(valid("meta-2") + ("metadata" to mapOf("a b" to "v"))))
        assertEquals("invalid-field" to listOf("/metadata/k" to "too_long"), rejected(valid("meta-3") + ("metadata" to mapOf("k" to "v".repeat(501)))))
        assertEquals("invalid-field" to listOf("/metadata/k" to "invalid_type"), rejected(valid("meta-4") + ("metadata" to mapOf("k" to 3))))
    }

    @Test
    fun `expires_at must be a UTC timestamp with an uppercase Z`() {
        assertEquals(201, create(valid("exp-ok") + ("expires_at" to "2099-01-01T00:00:00.123Z"), "exp-ok").statusCode.value())
        listOf("2099-01-01T00:00:00+00:00", "2099-01-01T00:00:00z", "2099-01-01", "2099-01-01T00:00Z", "2099-01-01T00:00:00.1234Z").forEachIndexed { index, value ->
            assertEquals("invalid-field" to listOf("/expires_at" to "malformed"), rejected(valid("exp-$index") + ("expires_at" to value)), value)
        }
    }

    @Test
    fun `a nested required member that is absent is a missing field`() {
        assertEquals("missing-field" to listOf("/amount/currency" to "required"), rejected(valid("nest-1") + ("amount" to mapOf("amount" to 100))))
        assertEquals("missing-field" to listOf("/amount/amount" to "required"), rejected(valid("nest-2") + ("amount" to mapOf("currency" to "HTG"))))
        assertEquals("missing-field" to listOf("/payer/phone_number" to "required"), rejected(valid("nest-3") + ("payer" to emptyMap<String, Any>())))
        assertEquals(
            "missing-field" to listOf("/reference" to "required", "/amount/amount" to "required", "/payer/phone_number" to "required"),
            rejected(valid("nest-4") - "reference" + ("amount" to mapOf("currency" to "HTG")) + ("payer" to mapOf("phone_number" to null))),
        )
        assertEquals("invalid-field" to listOf("/amount" to "invalid_type"), rejected(valid("nest-5") + ("amount" to "100 HTG")))
        assertEquals("invalid-field" to listOf("/payer/phone_number" to "invalid_type"), rejected(valid("nest-6") + ("payer" to mapOf("phone_number" to 5))))
    }

    @Test
    fun `return_url must be an https URL with a host, http being reserved to loopback hosts`() {
        listOf("https:opaque", "https:///orders", "http://example.test/return", "http://[::1]/return", "ftp://shop.example/").forEachIndexed { index, value ->
            assertEquals("invalid-field" to listOf("/return_url" to "malformed"), rejected(valid("ret-$index") + ("return_url" to value)), value)
        }
        assertEquals(201, create(valid("ret-ok-1") + ("return_url" to "https://shop.example/orders/1"), "ret-ok-1").statusCode.value())
        assertEquals(201, create(valid("ret-ok-2") + ("return_url" to "http://localhost:3000/return"), "ret-ok-2").statusCode.value())
    }

    @Test
    fun `native payment ids are opaque resource ids rather than a counter`() {
        val ids = (1..3).map { create(valid("opaque-$it"), "opaque-$it").body!!["id"].toString() }

        assertTrue(ids.all { ResourceIds.isValid(it) && it.startsWith("pay_") }, ids.toString())
        assertEquals(3, ids.toSet().size)
        assertTrue(ids.none { Regex("\\d{8}$").containsMatchIn(it) }, ids.toString())
    }

    private fun noSource(reference: String): Map<String, Any?> = valid(reference) + mapOf("provider" to "mock_no_source", "payer" to mapOf("phone_number" to "+50934567890"))

    private fun call(
        method: String,
        path: String,
    ): ResponseEntity<Map<*, *>> =
        client
            .method(HttpMethod.valueOf(method))
            .uri(path)
            .header("Authorization", "Bearer $NO_SOURCE_API_KEY")
            .retrieve()
            .toEntity(Map::class.java)

    @Test
    fun `synchronize without payments lookup is not supported for a pending payment but reading it stays possible`() {
        state.declare(SpaceId.DEFAULT, "mock_no_source", "nolookup-1", Scenario.PENDING_FOREVER)
        val id = create(noSource("nolookup-1"), "nolookup-1", operator = "mock_no_source", apiKey = NO_SOURCE_API_KEY).body!!["id"]

        val read = call("GET", "/mock_no_source/v1/payments/$id")
        val synchronized = call("POST", "/mock_no_source/v1/payments/$id/synchronize")
        val unknown = call("POST", "/mock_no_source/v1/payments/pay_unknown/synchronize")

        assertEquals(200, read.statusCode.value())
        assertEquals("pending", read.body!!["status"])
        assertEquals(422, synchronized.statusCode.value())
        assertEquals("capability-not-supported", synchronized.type())
        assertEquals(404, unknown.statusCode.value())
    }

    @Test
    fun `synchronize without payments lookup returns a terminal payment unchanged`() {
        val id = create(noSource("nolookup-2"), "nolookup-2", operator = "mock_no_source", apiKey = NO_SOURCE_API_KEY).body!!["id"]
        repeat(50) { if (call("GET", "/mock_no_source/v1/payments/$id").body!!["status"] == "pending") Thread.sleep(20) }

        val synchronized = call("POST", "/mock_no_source/v1/payments/$id/synchronize")

        assertEquals(200, synchronized.statusCode.value())
        assertEquals("succeeded", synchronized.body!!["status"])
    }

    companion object {
        const val API_KEY = "ofsp_test_MockOpenFspKeyPublishedForTheSimulator"
        const val NO_SOURCE_API_KEY = "ofsp_test_MockNoSourceKeyPublishedForTheSimulator"
    }
}
