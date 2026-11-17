package org.openfsp.simulator

import org.junit.jupiter.api.Test
import org.openfsp.ddd.Stub
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.ApplicationContext
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "openfsp.simulator.response-delay=0ms",
        "openfsp.simulator.settle-after=0ms",
        "openfsp.simulator.hang-duration=50ms",
        "server.address=127.0.0.1",
        "openfsp.simulator.http-callback-hosts=gateway",
    ],
)
class SimulatorSpacesTests {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var context: ApplicationContext

    private val client by lazy { localClient(port) }

    private fun open(): String {
        val created = client.post().uri("/runs").retrieve().toEntity(Map::class.java)
        assertEquals(201, created.statusCode.value())
        val space = created.body!!["space"] as String
        assertEquals("/runs/$space", created.headers.location.toString())
        assertEquals("http://127.0.0.1:8090/runs/$space", created.body!!["base_url"])
        return space
    }

    private fun prefix(space: String?) = space?.let { "/runs/$it" } ?: ""

    private fun declare(
        space: String?,
        reference: String,
        scenario: String,
    ) = assertEquals(
        204,
        client
            .put()
            .uri("${prefix(space)}/control/operators/mock_openfsp/references/$reference/scenario")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"scenario":"$scenario"}""")
            .retrieve()
            .toBodilessEntity()
            .statusCode
            .value(),
    )

    private fun create(
        space: String?,
        reference: String,
        payer: Boolean = true,
    ): ResponseEntity<Map<*, *>> =
        client
            .post()
            .uri("${prefix(space)}/mock_openfsp/v1/payments")
            .header("Authorization", "Bearer $API_KEY")
            .header("Idempotency-Key", "key-$reference")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                mapOf("reference" to reference, "amount" to mapOf("amount" to 1000, "currency" to "HTG"), "provider" to "mock_openfsp") +
                    (if (payer) mapOf("payer" to mapOf("phone_number" to "+50934567890")) else emptyMap()),
            ).retrieve()
            .toEntity(Map::class.java)

    private fun statusOf(
        space: String?,
        reference: String,
    ): String? {
        val found =
            client
                .get()
                .uri("${prefix(space)}/mock_openfsp/v1/payments?reference={reference}", reference)
                .header("Authorization", "Bearer $API_KEY")
                .retrieve()
                .body(Map::class.java)!!["data"] as List<*>
        return (found.singleOrNull() as Map<*, *>?)?.get("status") as String?
    }

    private fun awaitStatus(
        space: String?,
        reference: String,
        status: String,
    ) {
        repeat(50) {
            if (statusOf(space, reference) == status) return
            Thread.sleep(20)
        }
        assertEquals(status, statusOf(space, reference))
    }

    private fun payments(space: String?): List<Map<*, *>> =
        (client.get().uri("${prefix(space)}/control/payments?size=100").retrieve().body(Map::class.java)!!["data"] as List<*>).map { it as Map<*, *> }

    private fun journal(space: String?): List<String> =
        (client.get().uri("${prefix(space)}/control/journal?size=100").retrieve().body(Map::class.java)!!["data"] as List<*>).map { (it as Map<*, *>)["path"] as String }

    @Test
    fun `the running simulator uses infrastructure adapters, never the domain test stubs`() {
        assertEquals(emptyList(), context.getBeansWithAnnotation(Stub::class.java).keys.toList())
    }

    @Test
    fun `two spaces with the same reference and conflicting scenarios do not see each other`() {
        val a = open()
        val b = open()
        assertNotEquals(a, b)
        declare(a, "shared-ref", "DECLINE")
        declare(b, "shared-ref", "PENDING_FOREVER")

        val inA = create(a, "shared-ref")
        val inB = create(b, "shared-ref")
        assertEquals(201, inA.statusCode.value())
        assertEquals(201, inB.statusCode.value())
        assertNotEquals(inA.body!!["id"], inB.body!!["id"])
        assertEquals("http://127.0.0.1:8090/runs/$a/mock_openfsp/v1/payments/${inA.body!!["id"]}", inA.headers.location.toString())

        awaitStatus(a, "shared-ref", "failed")
        assertEquals("pending", statusOf(b, "shared-ref"))
        assertEquals(null, statusOf(null, "shared-ref"))
        assertTrue(payments(null).none { it["order_id"] == "shared-ref" })
    }

    @Test
    fun `a scenario is declared by reference only for a provider whose order identifier is the reference`() {
        val refused =
            client
                .put()
                .uri("/control/operators/mock_moncash/references/any-ref/scenario")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"scenario":"DECLINE"}""")
                .retrieve()
                .toBodilessEntity()
        assertEquals(404, refused.statusCode.value())
    }

    @Test
    fun `a reset clears its own space only and each space keeps its own journal`() {
        val a = open()
        val b = open()
        create(a, "reset-a")
        create(b, "reset-b")
        assertEquals(listOf("/mock_openfsp/v1/payments"), journal(a))
        assertEquals(listOf("/mock_openfsp/v1/payments"), journal(b))
        assertTrue(journal(null).none { it.startsWith("/runs/") })

        assertEquals(204, client.post().uri("/runs/$a/control/reset").retrieve().toBodilessEntity().statusCode.value())

        assertTrue(payments(a).isEmpty())
        assertTrue(journal(a).isEmpty())
        assertEquals(listOf("reset-b"), payments(b).map { it["order_id"] })
        assertEquals(1, journal(b).size)
        assertEquals(201, create(a, "reset-b").statusCode.value())
    }

    @Test
    fun `discovery, hosted checkout and MonCash redirects stay in their space`() {
        val a = open()
        val configuration = client.get().uri("/runs/$a/mock_openfsp/.well-known/openfsp-configuration").retrieve().body(Map::class.java)!!
        assertEquals("http://127.0.0.1:8090/runs/$a/mock_openfsp/v1", configuration["api_base"])

        declare(a, "hosted", "PENDING_FOREVER")
        val hosted = create(a, "hosted", payer = false).body!!
        val url = (hosted["next_action"] as Map<*, *>)["redirect_url"] as String
        assertEquals("http://127.0.0.1:8090/runs/$a/mock_openfsp/checkout/${hosted["id"]}", url)
        val page = client.get().uri("/runs/$a/mock_openfsp/checkout/{id}", hosted["id"]).retrieve().body(String::class.java)!!
        assertTrue(page.contains("action=\"/runs/$a/mock_openfsp/checkout/${hosted["id"]}\""))
        assertEquals(404, client.get().uri("/mock_openfsp/checkout/{id}", hosted["id"]).retrieve().toBodilessEntity().statusCode.value())

        val basic = "Basic " + Base64.getEncoder().encodeToString("mock_moncash_client_id:mock_moncash_client_secret".toByteArray())
        val token = client.post().uri("/runs/$a/mock_moncash/Api/oauth/token?grant_type=client_credentials").header("Authorization", basic).retrieve().body(Map::class.java)!!
        val bearer = "Bearer ${token["access_token"]}"
        val create = { space: String? ->
            client.post().uri("${prefix(space)}/mock_moncash/Api/v1/CreatePayment").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .body("""{"amount":100,"orderId":"mc-space"}""").retrieve().toEntity(Map::class.java)
        }
        assertEquals(401, create(null).statusCode.value())
        val created = create(a)
        assertEquals(202, created.statusCode.value())
        val paymentToken = (created.body!!["payment_token"] as Map<*, *>)["token"] as String
        val redirect = client.get().uri("/runs/$a/mock_moncash/Moncash-middleware/Payment/Redirect?token={t}", paymentToken).retrieve().toBodilessEntity()
        assertEquals("/runs/$a/mock_moncash/checkout/$paymentToken", redirect.headers.location.toString())
    }

    @Test
    fun `an unknown, malformed or closed space answers not found and the default space stays`() {
        val missing = client.get().uri("/runs/${"0".repeat(32)}/control/payments").retrieve().toEntity(Map::class.java)
        assertEquals(404, missing.statusCode.value())
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, missing.headers.contentType)
        assertEquals(404, missing.body!!["status"])
        assertEquals(404, client.get().uri("/runs/default/control/payments").retrieve().toBodilessEntity().statusCode.value())

        val a = open()
        assertEquals(200, client.get().uri("/runs/$a").retrieve().toBodilessEntity().statusCode.value())
        assertEquals(204, client.delete().uri("/runs/$a").retrieve().toBodilessEntity().statusCode.value())
        assertEquals(404, client.get().uri("/runs/$a/control/payments").retrieve().toBodilessEntity().statusCode.value())
        assertEquals(404, client.delete().uri("/runs/$a").retrieve().toBodilessEntity().statusCode.value())
        assertEquals(200, client.get().uri("/control/payments").retrieve().toBodilessEntity().statusCode.value())
    }

    @Test
    fun `control errors answer with a problem document`() {
        val unreadable =
            client
                .put()
                .uri("/control/operators/mock_openfsp/default-scenario")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"scenario":"NOT_A_SCENARIO"}""")
                .retrieve()
                .toEntity(Map::class.java)
        val unknownPayment = client.post().uri("/control/payments/pay_unknown/resolve").contentType(MediaType.APPLICATION_JSON).body("""{"outcome":"FAILED"}""").retrieve().toEntity(Map::class.java)
        val unknownSpace = client.get().uri("/runs/${"0".repeat(32)}").retrieve().toEntity(Map::class.java)

        listOf(unreadable to 400, unknownPayment to 404, unknownSpace to 404).forEach { (response, status) ->
            assertEquals(status, response.statusCode.value())
            assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.headers.contentType)
            assertEquals(status, response.body!!["status"])
        }
    }

    private companion object {
        const val API_KEY = "ofsp_test_MockOpenFspKeyPublishedForTheSimulator"
    }
}
