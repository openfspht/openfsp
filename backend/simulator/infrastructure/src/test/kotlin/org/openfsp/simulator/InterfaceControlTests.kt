package org.openfsp.simulator

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openfsp.simulator.control.ControlScope
import org.openfsp.simulator.control.api.ControlSimulation
import org.openfsp.simulator.control.web.InterfaceSessions
import org.openfsp.simulator.shared.configuration.SimulatorProperties
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "openfsp.simulator.response-delay=0ms",
        "openfsp.simulator.settle-after=0ms",
        "openfsp.simulator.ui.control-key=$PUBLISHED_KEY",
        "server.address=127.0.0.1",
    ],
)
@ActiveProfiles("conformance")
class InterfaceControlTests {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var control: ControlSimulation

    @Autowired
    private lateinit var properties: SimulatorProperties

    private val client by lazy { localClient(port) }

    @BeforeEach
    fun reset() = control.reset(ControlScope.Instance)

    @Test
    fun `a page of the interface opens a strict session cookie and never holds the control key`() {
        val page = client.get().uri("/activity").retrieve().toEntity(String::class.java)
        assertEquals(200, page.statusCode.value())
        val cookie = page.headers.getFirst(HttpHeaders.SET_COOKIE)!!
        assertTrue(cookie.startsWith("${InterfaceSessions.COOKIE}="))
        listOf("HttpOnly", "SameSite=Strict", "Path=/simulator/control").forEach { assertTrue(cookie.contains(it), it) }
        assertFalse(page.body!!.contains(PUBLISHED_KEY))
        assertFalse(page.headers.toString().contains(PUBLISHED_KEY))
        val asset = client.get().uri("/assets/simulator-test.js").retrieve().toEntity(String::class.java)
        assertEquals(200, asset.statusCode.value())
        assertNull(asset.headers.getFirst(HttpHeaders.SET_COOKIE))
        assertFalse(asset.body!!.contains(PUBLISHED_KEY))
    }

    @Test
    fun `the interface reaches the control interface as the merchant account of the configured key`() {
        create(PUBLISHED_KEY, "interface-published")
        val run = client.post().uri("/mock_openfsp/conformance").retrieve().body(Map::class.java)!!
        create(run["api_key"] as String, "interface-run")

        val payments = client.get().uri("/simulator/control/payments").header(HttpHeaders.COOKIE, session()).header(FETCH_SITE, "same-origin").retrieve().body(Map::class.java)!!
        assertEquals(listOf("interface-published"), (payments["data"] as List<*>).map { (it as Map<*, *>)["order_id"] })
    }

    @Test
    fun `the interface path refuses every call without a valid session from the same origin`() {
        assertEquals(401, status())
        assertEquals(401, status(HttpHeaders.AUTHORIZATION to "Bearer $PUBLISHED_KEY"))
        assertEquals(401, status(HttpHeaders.COOKIE to "${InterfaceSessions.COOKIE}=4102444800.forged"))
        assertEquals(403, status(HttpHeaders.COOKIE to session(), FETCH_SITE to "cross-site"))
        assertEquals(403, status(HttpHeaders.COOKIE to session(), FETCH_SITE to "same-site"))
        assertEquals(200, status(HttpHeaders.COOKIE to session()))
    }

    @Test
    fun `a session expires after its lifetime`() {
        val start = Instant.parse("2026-11-19T12:00:00Z")
        val issued = InterfaceSessions(properties, Clock.fixed(start, ZoneOffset.UTC))
        val token = issued.open().value
        assertTrue(issued.isValid(token))
        val later = InterfaceSessions(properties, Clock.fixed(start.plus(Duration.ofHours(9)), ZoneOffset.UTC))
        assertFalse(later.isValid(token))
        assertFalse(issued.isValid(token.substringBefore('.') + ".x"))
        assertFalse(issued.isValid(null))
    }

    private fun session(): String = client.get().uri("/").retrieve().toBodilessEntity().headers.getFirst(HttpHeaders.SET_COOKIE)!!.substringBefore(';')

    private fun status(vararg headers: Pair<String, String>): Int =
        client
            .get()
            .uri("/simulator/control/payments")
            .headers { all -> headers.forEach { (name, value) -> all.set(name, value) } }
            .retrieve()
            .toBodilessEntity()
            .statusCode
            .value()

    private fun create(
        key: String,
        reference: String,
    ) {
        client
            .post()
            .uri("/mock_openfsp/v1/payments")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $key")
            .header("Idempotency-Key", "key-$reference")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("reference" to reference, "amount" to mapOf("amount" to 125000, "currency" to "HTG"), "provider" to "mock_openfsp"))
            .retrieve()
            .toBodilessEntity()
    }

    private companion object {
        const val FETCH_SITE = "Sec-Fetch-Site"
    }
}

private const val PUBLISHED_KEY = "ofsp_test_MockOpenFspKeyPublishedForTheSimulator"

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["server.address=127.0.0.1"])
class InterfaceControlDisabledTests {
    @LocalServerPort
    private var port: Int = 0

    private val client by lazy { localClient(port) }

    @Test
    fun `without a control key the interface opens no session and its control path answers 404`() {
        val page = client.get().uri("/").retrieve().toEntity(String::class.java)
        assertEquals(200, page.statusCode.value())
        assertNull(page.headers.getFirst(HttpHeaders.SET_COOKIE))
        val refused = client.get().uri("/simulator/control/payments").header(HttpHeaders.AUTHORIZATION, "Bearer $PUBLISHED_KEY").retrieve().toBodilessEntity()
        assertEquals(404, refused.statusCode.value())
    }

    @Test
    fun `interface pages carry the security headers and unknown paths stay 404`() {
        val page = client.get().uri("/requests").retrieve().toBodilessEntity()
        assertTrue(page.headers.getFirst("Content-Security-Policy")!!.contains("frame-ancestors 'none'"))
        assertEquals("nosniff", page.headers.getFirst("X-Content-Type-Options"))
        assertEquals("DENY", page.headers.getFirst("X-Frame-Options"))
        assertEquals("no-referrer", page.headers.getFirst("Referrer-Policy"))
        assertEquals(404, client.get().uri("/control-nothing").retrieve().toBodilessEntity().statusCode.value())
        assertEquals(404, client.get().uri("/mock_openfsp/v1/nothing").retrieve().toBodilessEntity().statusCode.value())
    }
}
