package org.openfsp.gateway

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GatewayArchitectureTest {
    private class Source(
        val name: String,
        val text: String,
    ) {
        val packageName: String = PACKAGE.find(text)?.groupValues?.get(1).orEmpty()
        val domain: String = packageName.removePrefix(ROOT).substringBefore('.')
        val imports: List<String> = text.lines().filter { it.startsWith("import ") }.map { it.removePrefix("import ").substringBefore(" as ").trim() }
        val declarations: List<String> = DECLARATION.findAll(text).map { it.groupValues[1] }.toList()
        val persistence: Boolean = imports.any { line -> JPA.any { line.startsWith(it) } }
        val web: Boolean = WEB_MARKERS.any { it in text } || imports.any { line -> WEB_IMPORTS.any { line.startsWith(it) } }

        fun references(name: String): Boolean = Regex("\\b$name\\b").containsMatchIn(text)

        fun uses(
            pkg: String,
            type: String,
        ): Boolean =
            "$pkg.$type" in imports ||
                Regex("\\b${Regex.escape("$pkg.$type")}\\b").containsMatchIn(text) ||
                ((pkg == packageName || "$pkg.*" in imports) && references(type))
    }

    private val sources = File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.map { Source(it.name, it.readText()) }.toList()
    private val persistenceTypes = sources.filter { it.persistence }.flatMap { source -> source.declarations.map { source.packageName to it } }.toSet()

    private fun violations(rule: (Source) -> List<String>): List<String> = sources.flatMap { source -> rule(source).map { "${source.name}: $it" } }

    private fun webLayerViolations(source: Source): List<String> {
        if (!source.web) return emptyList()
        val jpa = JPA.filter { Regex("\\b${Regex.escape(it)}").containsMatchIn(source.text) }
        return jpa + persistenceTypes.filter { (pkg, type) -> source.uses(pkg, type) }.map { it.second }
    }

    @Test
    fun `the sources are found`() {
        assertTrue(sources.size > 100)
        assertTrue(sources.count { it.web } > 20 && sources.count { it.persistence } > 10)
    }

    @Test
    fun `the web layer touches no persistence`() {
        assertEquals(
            emptyList(),
            violations(::webLayerViolations),
        )
    }

    @Test
    fun `a controller reaching persistence fails the web layer rule however it names it`() {
        val controller = "@RestController\nclass C(val r: %s)"
        listOf(
            "package org.openfsp.gateway.admin\n" + controller.format("org.openfsp.gateway.apikeys.ApiKeyJpaRepository"),
            "package org.openfsp.gateway.admin\nimport org.openfsp.gateway.apikeys.ApiKeyJpaRepository as Keys\n" + controller.format("Keys"),
            "package org.openfsp.gateway.admin\nimport org.openfsp.gateway.apikeys.*\n" + controller.format("ApiKeyJpaRepository"),
            "package org.openfsp.gateway.apikeys\n" + controller.format("ApiKeyJpaRepository"),
            "package org.openfsp.gateway.admin\n" + controller.format("jakarta.persistence.EntityManager"),
            "package org.openfsp.gateway.admin\n" + controller.format("org.springframework.jdbc.core.simple.JdbcClient"),
        ).forEach { text -> assertTrue(webLayerViolations(Source("C.kt", text)).isNotEmpty(), text) }
        assertEquals(emptyList(), webLayerViolations(Source("C.kt", "package org.openfsp.gateway.admin\n" + controller.format("ApiKeyJpaRepositoryView"))))
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
                    .filter { (pkg, type) -> pkg != source.packageName && source.uses(pkg, type) }
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
