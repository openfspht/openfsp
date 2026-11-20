package org.openfsp.gateway.admin.session

import org.openfsp.gateway.TestcontainersConfiguration
import org.openfsp.gateway.shared.security.AdminTokenHashing
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.client.RestClient
import kotlin.test.Test
import kotlin.test.assertEquals

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("development", "simulated", "test")
class AdminSessionTest {
    @LocalServerPort
    private var port: Int = 0

    private val client by lazy {
        RestClient
            .builder()
            .requestFactory(JdkClientHttpRequestFactory())
            .baseUrl("http://127.0.0.1:$port")
            .defaultStatusHandler(HttpStatusCode::isError) { _, _ -> }
            .build()
    }

    private fun accounts(cookie: String) = client.get().uri("/admin/accounts").header(HttpHeaders.COOKIE, cookie).retrieve().toBodilessEntity().statusCode.value()

    @Test
    fun `a signed out session is refused even when its cookie is presented again`() {
        val signIn = client.post().uri("/admin/session").contentType(MediaType.APPLICATION_JSON).body("""{"token":"$TOKEN"}""").retrieve().toBodilessEntity()
        assertEquals(204, signIn.statusCode.value())
        val cookie = signIn.headers.getFirst(HttpHeaders.SET_COOKIE)!!.substringBefore(';')
        assertEquals(200, accounts(cookie))

        assertEquals(204, client.delete().uri("/admin/session").header(HttpHeaders.COOKIE, cookie).retrieve().toBodilessEntity().statusCode.value())

        assertEquals(401, accounts(cookie))
    }

    companion object {
        private const val TOKEN = "admin-session-test-token"

        @JvmStatic
        @DynamicPropertySource
        fun adminToken(registry: DynamicPropertyRegistry) {
            registry.add("openfsp.admin-token") { AdminTokenHashing().hash(TOKEN) }
        }
    }
}
