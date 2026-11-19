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
        val imports: List<String> = text.lines().filter { it.startsWith("import ") }.map { it.removePrefix("import ").trim() }
        val layer: String? = packageName.split('.').firstOrNull { it in LAYERS }

        fun importsAny(prefixes: List<String>): List<String> = imports.filter { line -> prefixes.any { line.startsWith(it) || line.contains(it) } }
    }

    private val sources = File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.map(::Source).toList()

    private fun violations(rule: (Source) -> List<String>): List<String> = sources.flatMap { source -> rule(source).map { "${source.name}: $it" } }

    @Test
    fun `the sources are found`() {
        assertTrue(sources.size > 100)
        assertTrue(sources.count { it.layer == WEB } > 20 && sources.count { it.layer == PERSISTENCE_LAYER } > 10)
    }

    @Test
    fun `the web layer touches no persistence`() {
        assertEquals(emptyList(), violations { source -> if (source.layer == WEB) source.importsAny(PERSISTENCE) else emptyList() })
    }

    @Test
    fun `the persistence layer knows nothing of the web`() {
        assertEquals(emptyList(), violations { source -> if (source.layer == PERSISTENCE_LAYER) source.importsAny(listOf(".web.", "org.springframework.web.", "jakarta.servlet.")) else emptyList() })
    }

    @Test
    fun `controllers and request filters live in web packages`() {
        assertEquals(emptyList(), violations { source -> if (source.layer != WEB && WEB_ANNOTATIONS.any { it in source.text }) listOf(source.packageName) else emptyList() })
        assertEquals(emptyList(), violations { source -> if (source.layer != WEB && FILTER in source.text) listOf(source.packageName) else emptyList() })
    }

    @Test
    fun `entities, repositories and SQL live in persistence packages`() {
        assertEquals(emptyList(), violations { source -> if (source.layer != PERSISTENCE_LAYER) source.importsAny(JPA) else emptyList() })
    }

    @Test
    fun `no adapter reaches into the persistence of another domain`() {
        assertEquals(
            emptyList(),
            violations { source ->
                val domain = source.packageName.removePrefix(ROOT).substringBefore('.')
                source.imports.filter { it.startsWith(ROOT) && it.contains(".persistence.") }.filterNot { it.removePrefix(ROOT).substringBefore('.') in setOf(domain, SHARED) }
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
        const val WEB = "web"
        const val PERSISTENCE_LAYER = "persistence"
        val LAYERS = setOf(WEB, PERSISTENCE_LAYER)
        val PACKAGE = Regex("^package ([\\w.]+)", RegexOption.MULTILINE)
        val TOP_LEVEL_OBJECT = Regex("^(internal |private )?object \\w+.*")
        val WEB_ANNOTATIONS = listOf("@RestController", "@Controller\n", "@RestControllerAdvice")
        const val FILTER = ": OncePerRequestFilter()"
        val JPA = listOf("jakarta.persistence.", "org.springframework.data.", "org.springframework.jdbc.", "org.hibernate.")
        val PERSISTENCE = JPA + ".persistence."
    }
}
