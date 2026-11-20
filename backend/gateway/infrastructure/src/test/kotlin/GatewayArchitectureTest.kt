package org.openfsp.gateway

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GatewayArchitectureTest {
    private class Source(
        file: File,
    ) {
        val name: String = file.name
        val text: String = file.readText()
        val packageName: String = PACKAGE.find(text)?.groupValues?.get(1).orEmpty()
        val domain: String = packageName.removePrefix(ROOT).substringBefore('.')
        val imports: List<String> = text.lines().filter { it.startsWith("import ") }.map { it.removePrefix("import ").trim() }
        val declarations: List<String> = DECLARATION.findAll(text).map { it.groupValues[1] }.toList()
        val persistence: Boolean = imports.any { line -> JPA.any { line.startsWith(it) } }
        val web: Boolean = WEB_MARKERS.any { it in text } || imports.any { line -> WEB_IMPORTS.any { line.startsWith(it) } }

        fun references(name: String): Boolean = Regex("\\b$name\\b").containsMatchIn(text)
    }

    private val sources = File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.map(::Source).toList()
    private val persistenceTypes = sources.filter { it.persistence }.flatMap { source -> source.declarations.map { source.packageName to it } }.toSet()

    private fun violations(rule: (Source) -> List<String>): List<String> = sources.flatMap { source -> rule(source).map { "${source.name}: $it" } }

    @Test
    fun `the sources are found`() {
        assertTrue(sources.size > 100)
        assertTrue(sources.count { it.web } > 20 && sources.count { it.persistence } > 10)
    }

    @Test
    fun `the web layer touches no persistence`() {
        assertEquals(
            emptyList(),
            violations { source ->
                if (!source.web) return@violations emptyList()
                val jpa = source.imports.filter { line -> JPA.any { line.startsWith(it) } }
                val types = persistenceTypes.filter { (pkg, type) -> (pkg == source.packageName && source.references(type)) || "$pkg.$type" in source.imports }.map { it.second }
                jpa + types
            },
        )
    }

    @Test
    fun `the persistence layer knows nothing of the web`() {
        assertEquals(emptyList(), violations { source -> if (source.persistence) source.imports.filter { line -> WEB_IMPORTS.any { line.startsWith(it) } } else emptyList() })
    }

    @Test
    fun `no adapter reaches into the persistence of another domain`() {
        assertEquals(
            emptyList(),
            violations { source ->
                persistenceTypes
                    .filter { (pkg, type) -> "$pkg.$type" in source.imports }
                    .filterNot { (pkg, _) -> pkg.removePrefix(ROOT).substringBefore('.') in setOf(source.domain, SHARED) }
                    .map { "${it.first}.${it.second}" }
            },
        )
    }

    @Test
    fun `behaviour is held by beans, not by singletons`() {
        assertEquals(emptyList(), violations { source -> source.text.lines().filter { TOP_LEVEL_OBJECT.matches(it) } })
    }

    private companion object {
        const val ROOT = "org.openfsp.gateway."
        const val SHARED = "shared"
        val PACKAGE = Regex("^package ([\\w.]+)", RegexOption.MULTILINE)
        val DECLARATION = Regex("^(?:(?:public|internal|private|data|abstract|open|sealed|enum|value) )*(?:class|interface|object) (\\w+)", RegexOption.MULTILINE)
        val TOP_LEVEL_OBJECT = Regex("^(internal |private )?object \\w+.*")
        val WEB_MARKERS = listOf("@RestController", "@Controller\n", "@RestControllerAdvice", ": OncePerRequestFilter()")
        val WEB_IMPORTS = listOf("org.springframework.web.", "jakarta.servlet.")
        val JPA = listOf("jakarta.persistence.", "org.springframework.data.", "org.springframework.jdbc.", "org.hibernate.")
    }
}
