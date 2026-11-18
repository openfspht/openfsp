package org.openfsp.simulator

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["openfsp.simulator.response-delay=0ms", "openfsp.simulator.settle-after=0ms", "server.address=127.0.0.1"],
)
class StatementEntriesTests {
    @LocalServerPort
    private var port: Int = 0

    private val client by lazy { localClient(port) }

    @BeforeEach
    fun reset() {
        assertEquals(204, client.post().uri("/control/reset").retrieve().toBodilessEntity().statusCode.value())
    }

    private fun book(
        amount: String,
    ) {
        val today = LocalDate.now(ZoneId.of("America/Port-au-Prince"))
        val booked =
            client
                .post()
                .uri("/control/operators/mock_openfsp/statements/$today/entries")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"amount":"$amount","booked_at":"${today}T00:00:0${amount.first()}"}""")
                .retrieve()
                .toBodilessEntity()
        assertEquals(204, booked.statusCode.value())
    }

    private fun entries(
        query: String,
        key: String = API_KEY,
    ): ResponseEntity<Map<*, *>> =
        client
            .get()
            .uri("/mock_openfsp/v1/statement_entries?$query")
            .header("Authorization", "Bearer $key")
            .retrieve()
            .toEntity(Map::class.java)

    @Test
    fun `unmatched entries are listed in booking order, one page at a time`() {
        book("1.00")
        book("2.00")
        val first = entries("match=unmatched&provider=mock_openfsp&limit=1")
        assertEquals(200, first.statusCode.value())
        val page = first.body!!["data"] as List<*>
        assertEquals(1, page.size)
        assertEquals("unmatched", (page.single() as Map<*, *>)["match"])
        assertNull((page.single() as Map<*, *>)["payment"])
        val cursor = first.body!!["next_cursor"] as String
        val second = entries("match=unmatched&limit=1&cursor=$cursor").body!!
        assertEquals(1, (second["data"] as List<*>).size)
        assertNull(second["next_cursor"])
        assertTrue((entries("match=conflict").body!!["data"] as List<*>).isEmpty())
        assertTrue((entries("match=unmatched&provider=natcash").body!!["data"] as List<*>).isEmpty())
    }

    @Test
    fun `the entries need a match and the statements read scope`() {
        assertEquals(400, entries("provider=mock_openfsp").statusCode.value())
        assertEquals(422, entries("match=matched").statusCode.value())
        val forbidden = entries("match=unmatched", SECOND_PRINCIPAL_KEY)
        assertEquals(403, forbidden.statusCode.value())
        assertEquals("https://openfsp.org/problems/forbidden", forbidden.body!!["type"])
    }

    @Test
    fun `an entry with an unreadable amount or booking time is refused and the statement stays readable`() {
        val today = LocalDate.now(ZoneId.of("America/Port-au-Prince"))
        listOf(
            """{"amount":"twelve","booked_at":"${today}T08:00:00"}""",
            """{"amount":"1.234","booked_at":"${today}T08:00:00"}""",
            """{"amount":"1.00","booked_at":"yesterday"}""",
        ).forEach { body ->
            val refused =
                client
                    .post()
                    .uri("/control/operators/mock_openfsp/statements/$today/entries")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(Map::class.java)
            assertEquals(400, refused.statusCode.value(), body)
            assertEquals(MediaType.APPLICATION_PROBLEM_JSON, refused.headers.contentType)
        }
        val statements =
            client
                .get()
                .uri("/mock_openfsp/v1/statements?provider=mock_openfsp&business_date=$today")
                .header("Authorization", "Bearer $API_KEY")
                .retrieve()
                .toEntity(Map::class.java)
        assertEquals(200, statements.statusCode.value())
        assertEquals(200, entries("match=unmatched").statusCode.value())
    }

    private companion object {
        const val API_KEY = "ofsp_test_MockOpenFspKeyPublishedForTheSimulator"
        const val SECOND_PRINCIPAL_KEY = "ofsp_test_MockOpenFspSecondPrincipalKeyForTheSimulator"
    }
}
