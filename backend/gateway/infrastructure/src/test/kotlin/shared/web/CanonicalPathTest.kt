package org.openfsp.gateway.shared.web

import org.openfsp.gateway.TestcontainersConfiguration
import org.openfsp.gateway.shared.security.AdminTokenHashing
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.Socket
import kotlin.test.Test
import kotlin.test.assertEquals

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("development", "simulated", "test")
class CanonicalPathTest {
    @LocalServerPort
    private var port: Int = 0

    private fun status(path: String): Int =
        Socket("127.0.0.1", port).use { socket ->
            socket.getOutputStream().write("GET $path HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
            socket.getInputStream().bufferedReader(Charsets.US_ASCII).readLine().split(' ')[1].toInt()
        }

    @Test
    fun `the admin requires a session on its plain path`() {
        assertEquals(401, status("/admin/accounts"))
    }

    @Test
    fun `a percent-encoded admin path still requires a session`() {
        assertEquals(401, status("/%61dmin/accounts"))
        assertEquals(401, status("/%61dmin/keys"))
        assertEquals(401, status("/admin/%6Beys"))
    }

    @Test
    fun `a path parameter is refused before any routing`() {
        assertEquals(400, status("/admin;x/accounts"))
        assertEquals(400, status("/admin/accounts;x"))
        assertEquals(400, status("/v1;x/payments"))
    }

    @Test
    fun `encoded separators and dot segments are refused`() {
        assertEquals(400, status("/admin%2Fkeys"))
        assertEquals(400, status("/admin%5Ckeys"))
        assertEquals(400, status("/admin/%2e%2e/admin/keys"))
        assertEquals(400, status("/x/../admin/keys"))
        assertEquals(400, status("/./admin/keys"))
        assertEquals(400, status("//admin/keys"))
    }

    @Test
    fun `an encoded API path still requires a key`() {
        assertEquals(401, status("/%761/payments"))
        assertEquals(401, status("/v1/payments"))
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun adminToken(registry: DynamicPropertyRegistry) {
            registry.add("openfsp.admin-token") { AdminTokenHashing().hash("canonical-path-admin-token") }
        }
    }
}
