package org.openfsp.http

import com.sun.net.httpserver.HttpServer
import org.apache.hc.client5.http.classic.methods.HttpGet
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.UnknownHostException
import java.time.Duration
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GuardedHttpClientsTest {
    private val loopback = InetAddress.getLoopbackAddress()
    private val server =
        HttpServer.create(InetSocketAddress(loopback, 0), 0).apply {
            createContext("/") { exchange ->
                exchange.responseHeaders.add("Location", "http://elsewhere.test/")
                exchange.sendResponseHeaders(REDIRECT, -1)
                exchange.close()
            }
            start()
        }
    private val limits = HttpClientLimits(Duration.ofSeconds(1), Duration.ofSeconds(2))

    @AfterTest
    fun stop() = server.stop(0)

    @Test
    fun `every host is resolved by the caller and redirects are not followed`() {
        val asked = mutableListOf<String>()
        GuardedHttpClients.create({ host -> asked += host; arrayOf(loopback) }, limits).use { client ->
            val status = client.execute(HttpGet("http://service.test:${server.address.port}/")) { it.code }
            assertEquals(REDIRECT, status)
        }
        assertEquals(listOf("service.test"), asked)
    }

    @Test
    fun `a host the resolver refuses is never reached`() {
        GuardedHttpClients.create({ host -> throw UnknownHostException("$host is refused") }, limits.copy(reusesConnections = false, decompresses = false)).use { client ->
            assertFailsWith<IOException> { client.execute(HttpGet("http://service.test:${server.address.port}/")) { it.code } }
        }
    }

    private companion object {
        const val REDIRECT = 302
    }
}
