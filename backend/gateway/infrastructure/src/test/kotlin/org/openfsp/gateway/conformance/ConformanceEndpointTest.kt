package org.openfsp.gateway.conformance

import org.openfsp.gateway.account.MerchantAccount
import org.openfsp.gateway.TestcontainersConfiguration
import org.openfsp.gateway.webhook.spi.Subscriptions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["openfsp.conformance.enabled=true", "openfsp.conformance.access-code=letmein", "openfsp.conformance.max-active=2"],
)
@ActiveProfiles("development", "test")
class ConformanceEndpointTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var subscriptions: Subscriptions

    private val client by lazy {
        RestClient
            .builder()
            .requestFactory(JdkClientHttpRequestFactory())
            .baseUrl("http://127.0.0.1:$port")
            .defaultStatusHandler(HttpStatusCode::isError) { _, _ -> }
            .build()
    }

    private fun status(
        path: String,
        key: Any?,
    ) = client
        .get()
        .uri(path)
        .header("Authorization", "Bearer $key")
        .retrieve()
        .toBodilessEntity()
        .statusCode
        .value()

    @Test
    fun `the announced endpoint issues an isolated set of test keys and registers the receiver`() {
        val configuration = client.get().uri("/.well-known/openfsp-configuration").retrieve().body(Map::class.java)!!
        assertTrue((configuration["conformance_endpoint"] as String).endsWith("/conformance"))

        val refused = client.post().uri("/conformance").retrieve().toBodilessEntity()
        assertEquals(401, refused.statusCode.value())
        assertEquals("Bearer", refused.headers.getFirst("WWW-Authenticate"))

        val prepared =
            client
                .post()
                .uri("/conformance")
                .header("Authorization", "Bearer letmein")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("webhook_url" to "http://127.0.0.1:9/webhooks"))
                .retrieve()
                .toEntity(Map::class.java)
        assertEquals(201, prepared.statusCode.value())
        val inputs = prepared.body!!["inputs"] as Map<*, *>
        val apiKey = prepared.body!!["api_key"] as String
        assertTrue(apiKey.startsWith("ofsp_test_"))
        assertEquals(200, status("/v1/capabilities", apiKey))
        assertEquals(422, status("/v1/statements?provider=mock_openfsp&business_date=2026-09-28", inputs["statements-key"]))
        assertEquals(403, status("/v1/statements?provider=mock_openfsp&business_date=2026-09-28", inputs["restricted-key"]))
        assertEquals(401, status("/v1/capabilities", inputs["revoked-key"]))
        assertEquals(401, status("/v1/capabilities", inputs["expired-key"]))
        assertTrue(subscriptions.all().any { it.managed && it.name.startsWith("conformance-") && it.account != MerchantAccount.DEFAULT && it.url.toString() == "http://127.0.0.1:9/webhooks" })

        val second = client.post().uri("/conformance").header("Authorization", "Bearer letmein").retrieve().toBodilessEntity()
        assertEquals(201, second.statusCode.value())
        val third = client.post().uri("/conformance").header("Authorization", "Bearer letmein").retrieve().toBodilessEntity()
        assertEquals(429, third.statusCode.value())
    }
}
