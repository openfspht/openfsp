package org.openfsp.validator

import org.junit.jupiter.api.Assumptions.assumeTrue
import tools.jackson.databind.JsonNode
import tools.jackson.dataformat.yaml.YAMLMapper
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TraceabilityTest {
    private val conformance = Path.of(System.getProperty("openfsp.conformance")).toAbsolutePath().normalize()
    private val repository = conformance.parent
    private val specification = repository.resolve("../openfsp.org/content/docs/spec").normalize()
    private val yaml = YAMLMapper.builder().build()

    private val entries: List<JsonNode> = yaml.readTree(conformance.resolve("traceability.yaml").readText()).get("clauses").children()
    private val requirementIds: Set<String> =
        yaml
            .readTree(conformance.resolve("requirements.yaml").readText())
            .get("requirements")
            .children()
            .map { it.get("id").asString() }
            .toSet()

    @Test
    fun `every numbered clause of the English specification is traced once, in order`() {
        assumeTrue(specification.isDirectory(), "the openfsp.org checkout is not next to this repository")
        val pages =
            yaml
                .readTree(specification.resolve("meta.json").readText())
                .get("pages")
                .children()
                .map { it.asString() }
                .filterNot { it.startsWith("-") }
        val clauses =
            pages.flatMap { page ->
                CLAUSE.findAll(specification.resolve("$page.mdx").readText()).map { "$page#${it.groupValues[1]}" }.toList()
            }
        assertEquals(clauses, entries.map { it.get("clause").asString() })
    }

    @Test
    fun `each entry names its clause once with a valid responsibility, target and verification`() {
        val clauses = entries.map { it.get("clause").asString() }
        assertEquals(clauses.size, clauses.toSet().size, "duplicated clauses")
        val problems =
            entries.flatMap { entry ->
                val clause = entry.get("clause").asString()
                val responsibility = entry.text("responsibility")
                val verification = entry.text("verification")
                buildList {
                    if (!CLAUSE_ID.matches(clause)) add("$clause: malformed clause")
                    if (responsibility !in RESPONSIBILITIES) add("$clause: responsibility $responsibility")
                    val targets = entry.strings("targets")
                    if (responsibility == "server" && (targets.isEmpty() || !TARGETS.containsAll(targets))) add("$clause: targets $targets")
                    if (responsibility != "server" && entry.has("targets")) add("$clause: targets outside a server clause")
                    when {
                        responsibility == "informative" -> if (verification != null) add("$clause: an informative clause has no verification")
                        verification !in VERIFICATIONS -> add("$clause: verification $verification")
                        verification == "gap" && responsibility == "client" -> add("$clause: a client clause cannot be a gap")
                    }
                    if ((verification == "requirement") != entry.has("requirements")) add("$clause: requirements")
                    if ((verification == "unit") != entry.has("tests")) add("$clause: tests")
                    if ((verification == "review") != !entry.text("reason").isNullOrBlank()) add("$clause: reason")
                    if ((verification == "gap") != !entry.text("note").isNullOrBlank()) add("$clause: note")
                }
            }
        assertEquals(emptyList(), problems)
    }

    @Test
    fun `cited requirements exist and every requirement traces a clause`() {
        val cited = entries.flatMap { entry -> entry.strings("requirements") }
        assertEquals(emptySet(), cited.toSet() - requirementIds, "unknown requirements")
        assertEquals(emptySet(), requirementIds - cited.toSet(), "requirements no clause cites")
    }

    @Test
    fun `every cited test file exists`() {
        val missing =
            entries
                .flatMap { entry -> entry.strings("tests") }
                .distinct()
                .filterNot { repository.resolve(it).isRegularFile() }
        assertTrue(missing.isEmpty(), "missing test files: $missing")
    }

    private fun JsonNode.children(): List<JsonNode> = (0 until size()).map { get(it) }

    private fun JsonNode.member(name: String): JsonNode? = get(name)?.takeUnless { it.isNull }

    private fun JsonNode.text(name: String): String? = member(name)?.asString()

    private fun JsonNode.strings(name: String): List<String> = member(name)?.children()?.map { it.asString() }.orEmpty()

    private companion object {
        val CLAUSE = Regex("""(?m)^\*\*(\d+(?:\.\d+)+)\.(?:\*\*| [^*\n]*\*\*)""")
        val CLAUSE_ID = Regex("""^[a-z-]+#\d+(\.\d+)*$""")
        val RESPONSIBILITIES = setOf("server", "client", "operator", "suite", "informative")
        val TARGETS = setOf("gateway", "native-operator")
        val VERIFICATIONS = setOf("requirement", "unit", "review", "gap")
    }
}
