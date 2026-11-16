package org.openfsp.simulator

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestClient
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["simulator.response-delay=0ms", "simulator.settle-after=0ms", "server.address=127.0.0.1"],
)
class StatementEntriesTests {
    @LocalServerPort
    private var port: Int = 0

    private val client by lazy {
        RestClient
            .builder()
            .baseUrl("http://127.0.0.1:$port")
            .defaultStatusHandler(HttpStatusCode::isError) { _, _ -> }
            .build()
    }

    private fun space(): String = client.post().uri("/runs").retrieve().body(Map::class.java)!!["space"] as String

    private fun book(
        space: String,
        amount: String,
    ) {
        val today = LocalDate.now(ZoneId.of("America/Port-au-Prince"))
        val booked =
            client
                .post()
                .uri("/runs/$space/control/operators/mock_openfsp/statements/$today/entries")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"amount":"$amount","booked_at":"${today}T00:00:0${amount.first()}"}""")
                .retrieve()
                .toBodilessEntity()
        assertEquals(204, booked.statusCode.value())
    }

    private fun entries(
        space: String,
        query: String,
        key: String = API_KEY,
    ): ResponseEntity<Map<*, *>> =
        client
            .get()
            .uri("/runs/$space/mock_openfsp/v1/statement_entries?$query")
            .header("Authorization", "Bearer $key")
            .retrieve()
            .toEntity(Map::class.java)

    @Test
    fun `unmatched entries are listed in booking order, one page at a time`() {
        val space = space()
        book(space, "1.00")
        book(space, "2.00")
        val first = entries(space, "match=unmatched&provider=mock_openfsp&limit=1")
        assertEquals(200, first.statusCode.value())
        val page = first.body!!["data"] as List<*>
        assertEquals(1, page.size)
        assertEquals("unmatched", (page.single() as Map<*, *>)["match"])
        assertNull((page.single() as Map<*, *>)["payment"])
        val cursor = first.body!!["next_cursor"] as String
        val second = entries(space, "match=unmatched&limit=1&cursor=$cursor").body!!
        assertEquals(1, (second["data"] as List<*>).size)
        assertNull(second["next_cursor"])
        assertTrue((entries(space, "match=conflict").body!!["data"] as List<*>).isEmpty())
        assertTrue((entries(space, "match=unmatched&provider=natcash").body!!["data"] as List<*>).isEmpty())
    }

    @Test
    fun `the entries need a match and the statements read scope`() {
        val space = space()
        assertEquals(400, entries(space, "provider=mock_openfsp").statusCode.value())
        assertEquals(422, entries(space, "match=matched").statusCode.value())
        val forbidden = entries(space, "match=unmatched", SECOND_PRINCIPAL_KEY)
        assertEquals(403, forbidden.statusCode.value())
        assertEquals("https://openfsp.org/problems/forbidden", forbidden.body!!["type"])
    }

    private companion object {
        const val API_KEY = "ofsp_test_MockOpenFspKeyPublishedForTheSimulator"
        const val SECOND_PRINCIPAL_KEY = "ofsp_test_MockOpenFspSecondPrincipalKeyForTheSimulator"
    }
}
