package org.openfsp.validator.runs.http

import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.openfsp.validator.problem.ProblemKind
import org.openfsp.validator.problem.ValidatorException
import org.openfsp.validator.shared.configuration.ValidatorProperties

class TargetGuardTest {
    private val addresses = mutableMapOf<String, List<InetAddress>>()
    private val guard = TargetGuard(ValidatorProperties(developmentMode = false)) { host -> addresses[host].orEmpty() }

    @Test
    fun `a host resolving to one private address among public ones is refused`() {
        addresses["mixed.example"] = listOf(InetAddress.getByName("93.184.215.14"), InetAddress.getByName("10.0.0.5"))
        assertEquals(ProblemKind.PRIVATE_ADDRESS, assertFailsWith<ValidatorException> { guard.pin(java.net.URI("https://mixed.example")) }.kind)
    }

    @Test
    fun `a public host is accepted and an unknown one refused`() {
        addresses["server.example"] = listOf(InetAddress.getByName("93.184.215.14"))
        assertEquals(addresses["server.example"], guard.pin(java.net.URI("https://server.example/v1")).addresses)
        assertEquals(ProblemKind.UNRESOLVABLE_HOST, assertFailsWith<ValidatorException> { guard.pin(java.net.URI("https://nowhere.example")) }.kind)
    }

    @Test
    fun `development mode accepts the loopback server`() {
        val pinned = TargetGuard(ValidatorProperties(developmentMode = true)) { listOf(InetAddress.getByName(it)) }.pin(java.net.URI("http://127.0.0.1:8080"))
        assertEquals(listOf(InetAddress.getByName("127.0.0.1")), pinned.addresses)
    }
}
