package org.openfsp.simulator

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openfsp.simulator.control.ControlScope
import org.openfsp.simulator.control.api.ControlSimulation
import org.openfsp.simulator.control.web.ControlAccess
import org.openfsp.simulator.shared.web.ControlProblem
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "openfsp.simulator.response-delay=0ms",
        "openfsp.simulator.settle-after=0ms",
        "server.address=127.0.0.1",
    ],
)
@ActiveProfiles("conformance")
class ControlAccessTests {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var control: ControlSimulation

    @Autowired
    private lateinit var access: ControlAccess

    private val client by lazy { localClient(port) }

    @BeforeEach
    fun reset() = control.reset(ControlScope.Instance)

    @Test
    fun `a caller off the loopback without a key is refused`() {
        val remote = MockHttpServletRequest().apply { remoteAddr = "203.0.113.7" }
        val refusal = assertFailsWith<ControlProblem> { access.scopeOf(remote) }
        assertEquals(HttpStatus.UNAUTHORIZED, refusal.status)
        assertEquals(ControlScope.Instance, access.scopeOf(MockHttpServletRequest().apply { remoteAddr = "127.0.0.1" }))
    }

    @Test
    fun `a run key sees only the payments and the journal of its own merchant account`() {
        val run = prepare()
        create(run["api_key"] as String, "scope-run")
        create(PUBLISHED_KEY, "scope-published")

        assertEquals(listOf("scope-run"), references(run["api_key"] as String))
        assertEquals(listOf("scope-published"), references(PUBLISHED_KEY))
        val journal = journal(run["api_key"] as String)
        assertTrue(journal.isNotEmpty())
        assertTrue(journal.none { it.contains("scope-published") })
        assertTrue(journal(PUBLISHED_KEY).none { it.contains("scope-run") })
    }

    @Test
    fun `the restricted key of a run is forbidden and an unknown key is unauthenticated`() {
        val run = prepare()
        val restricted = (run["inputs"] as Map<*, *>)["restricted-key"] as String
        assertEquals(403, status("/control/payments", restricted))
        assertEquals(401, status("/control/payments", "ofsp_test_NoSuchKey"))
        assertEquals(401, status("/control/journal", "ofsp_test_NoSuchKey"))
    }

    @Test
    fun `a run key resets only its own merchant account`() {
        val run = prepare()
        create(run["api_key"] as String, "reset-run")
        create(PUBLISHED_KEY, "reset-published")

        val reset = client.post().uri("/control/reset").header(AUTHORIZATION, "Bearer ${run["api_key"]}").retrieve().toBodilessEntity()

        assertEquals(204, reset.statusCode.value())
        assertEquals(listOf("reset-published"), control.payments(ControlScope.Instance).map { it.reference })
    }

    @Test
    fun `the journal masks every member whose name holds a key, a secret or a token`() {
        val run = prepare()
        val inputs = run["inputs"] as Map<*, *>
        val preparation = control.journal(ControlScope.Instance, "mock_openfsp").single { it.path.endsWith("/conformance") }

        listOf(run["api_key"], inputs["restricted-key"], inputs["statements-key"]).forEach { secret ->
            assertFalse(preparation.responseBody.contains(secret as String), "the journal leaks $secret")
        }
        assertTrue(preparation.responseBody.contains("\"restricted-key\":\"[REDACTED]\""))
        assertEquals(null, preparation.account)
    }

    @Test
    fun `a default scenario set with a run key never applies to the published merchant account`() {
        val run = prepare()
        val declared =
            client
                .put()
                .uri("/control/operators/mock_openfsp/default-scenario")
                .header(AUTHORIZATION, "Bearer ${run["api_key"]}")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("scenario" to "PENDING_FOREVER"))
                .retrieve()
                .toBodilessEntity()

        assertEquals(204, declared.statusCode.value())
        assertEquals("PENDING_FOREVER", defaultScenario(run["api_key"] as String))
        assertEquals("SUCCESS", defaultScenario(PUBLISHED_KEY))
    }

    private fun defaultScenario(key: String) = client.get().uri("/control/operators/mock_openfsp/default-scenario").header(AUTHORIZATION, "Bearer $key").retrieve().body(Map::class.java)!!["scenario"]

    private fun prepare(): Map<*, *> = client.post().uri("/mock_openfsp/conformance").retrieve().body(Map::class.java)!!

    private fun create(
        key: String,
        reference: String,
    ) {
        client
            .post()
            .uri("/mock_openfsp/v1/payments")
            .header(AUTHORIZATION, "Bearer $key")
            .header("Idempotency-Key", "key-$reference")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("reference" to reference, "amount" to mapOf("amount" to 125000, "currency" to "HTG"), "provider" to "mock_openfsp"))
            .retrieve()
            .toBodilessEntity()
    }

    private fun references(key: String): List<String?> =
        (client.get().uri("/control/payments").header(AUTHORIZATION, "Bearer $key").retrieve().body(Map::class.java)!!["data"] as List<*>).map { (it as Map<*, *>)["order_id"] as String? }

    private fun journal(key: String): List<String> =
        (client.get().uri("/control/journal?size=100").header(AUTHORIZATION, "Bearer $key").retrieve().body(Map::class.java)!!["data"] as List<*>).map { (it as Map<*, *>).toString() }

    private fun status(
        path: String,
        key: String,
    ): Int = client.get().uri(path).header(AUTHORIZATION, "Bearer $key").retrieve().toBodilessEntity().statusCode.value()

    private companion object {
        const val AUTHORIZATION = "Authorization"
        const val PUBLISHED_KEY = "ofsp_test_MockOpenFspKeyPublishedForTheSimulator"
    }
}
