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
                file.readLines().filter { line -> line.startsWith("import ") && FORBIDDEN.any { line.removePrefix("import ").startsWith(it) } }.map { "${file.name}: $it" }
            }
        assertEquals(emptyList(), offending)
    }

    private companion object {
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
                "org.openfsp.gateway.admin.",
                "org.openfsp.gateway.apikeys.",
                "org.openfsp.gateway.auditing.",
                "org.openfsp.gateway.payments.",
                "org.openfsp.gateway.providers.",
                "org.openfsp.gateway.shared.",
                "org.openfsp.gateway.statements.",
                "org.openfsp.gateway.webhooks.",
            )
    }
}
