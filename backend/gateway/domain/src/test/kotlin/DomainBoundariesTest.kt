package org.openfsp.gateway

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DomainBoundariesTest {
    private val sources = File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.toList()

    @Test
    fun `the domain imports no framework, persistence, serialization, HTTP client nor adapter`() {
        assertTrue(sources.isNotEmpty())
        val offending =
            sources.flatMap { file ->
                file.readLines().filter { line -> line.startsWith("import ") && isForbidden(line.removePrefix("import ")) }.map { "${file.name}: $it" }
            }
        assertEquals(emptyList(), offending)
    }

    @Test
    fun `use cases and ports are interfaces, with only the failures a port declares beside them`() {
        val ports = sources.filter { it.invariantSeparatorsPath.let { path -> ("/api/" in path || "/spi/" in path) && "/stubs/" !in path } }
        assertTrue(ports.size > 20)
        val offending =
            ports.flatMap { file ->
                file.readLines().filter { DECLARATION.matches(it) && !PORT.matches(it) }.map { "${file.name}: $it" }
            }
        assertEquals(emptyList(), offending)
    }

    @Test
    fun `no domain code depends on a stub`() {
        assertEquals(emptyList(), sources.filter { file -> file.readLines().any { it.startsWith("import ") && ".stubs." in it } }.map { it.name })
    }

    private fun isForbidden(import: String): Boolean = FORBIDDEN.any { import.startsWith(it) } || ADAPTER_LAYERS.any { it in import }

    private companion object {
        val DECLARATION = Regex("^(public |internal )?(data |enum |sealed |abstract |open |fun )?(class|interface|object) .*")
        val PORT = Regex("^(fun |sealed )?interface .*|^class \\w+ : RuntimeException\\(.*")
        val ADAPTER_LAYERS = listOf(".web.", ".persistence.", ".resources.")
        val FORBIDDEN =
            listOf(
                "org.springframework.",
                "jakarta.",
                "javax.persistence.",
                "com.fasterxml.",
                "tools.jackson.",
                "org.hibernate.",
                "java.net.http.",
                "org.apache.",
                "org.openfsp.gateway.accounts.",
                "org.openfsp.gateway.admin.",
                "org.openfsp.gateway.apikeys.",
                "org.openfsp.gateway.auditing.",
                "org.openfsp.gateway.capabilities.",
                "org.openfsp.gateway.discovery.",
                "org.openfsp.gateway.payments.",
                "org.openfsp.gateway.providers.",
                "org.openfsp.gateway.shared.",
                "org.openfsp.gateway.statements.",
                "org.openfsp.gateway.webhooks.",
            )
    }
}
