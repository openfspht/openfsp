package org.openfsp.simulator

import org.junit.jupiter.api.Test
import org.openfsp.ddd.Stub
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.ApplicationContext
import org.springframework.http.MediaType
import kotlin.test.assertEquals

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
class SimulatorControlTests {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var context: ApplicationContext

    private val client by lazy { localClient(port) }

    private fun statusOf(path: String) = client.get().uri(path).retrieve().toBodilessEntity().statusCode.value()

    @Test
    fun `the running simulator uses infrastructure adapters, never the domain test stubs`() {
        assertEquals(emptyList(), context.getBeansWithAnnotation(Stub::class.java).keys.toList())
    }

    @Test
    fun `the default configuration exposes the three operators and keeps the conformance ones out`() {
        listOf("mock_openfsp", "mock_moncash", "mock_natcash").forEach { assertEquals(200, statusOf("/control/operators/$it/scenarios"), it) }
        assertEquals(200, statusOf("/mock_openfsp/.well-known/openfsp-configuration"))
        listOf("mock_no_source", "mock_statement_only").forEach { assertEquals(404, statusOf("/$it/.well-known/openfsp-configuration"), it) }
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

        listOf(unreadable to 400, unknownPayment to 404).forEach { (response, status) ->
            assertEquals(status, response.statusCode.value())
            assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.headers.contentType)
            assertEquals(status, response.body!!["status"])
        }
    }
}
