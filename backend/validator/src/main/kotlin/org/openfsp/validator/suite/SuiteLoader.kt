package org.openfsp.validator.suite

import tools.jackson.core.JacksonException
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.module.kotlin.KotlinModule
import tools.jackson.module.kotlin.readValue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

class SuiteLoadException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

data class Suite(
    val directory: Path,
    val catalogue: Catalogue,
    val scenarios: List<Scenario>,
    val schemas: Map<String, JsonNode>,
) {
    private val byId = catalogue.requirements.associateBy { it.id }

    fun requirement(id: String): Requirement? = byId[id]

    fun requirement(
        id: String,
        target: TargetKind,
    ): Requirement? = byId[id]?.on(target)

    fun applies(
        scenario: Scenario,
        target: TargetKind,
    ): Boolean = requirement(scenario.requirement)?.targets?.contains(target) == true && scenario.runsOn(target)
}

object SuiteLoader {
    const val CATALOGUE = "requirements.yaml"
    const val SCHEMAS = "schemas"
    const val SCENARIOS = "scenarios"
    const val SCHEMA_EXTENSION = "json"

    val yaml: YAMLMapper =
        YAMLMapper
            .builder()
            .addModule(KotlinModule.Builder().build())
            .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build()

    fun load(directory: Path): Suite {
        if (!directory.isDirectory()) throw SuiteLoadException("conformance directory $directory does not exist")
        val catalogue = parse<Catalogue>(directory.resolve(CATALOGUE))
        val scenarioRoot = directory.resolve(SCENARIOS)
        val scenarios =
            files(scenarioRoot, "yaml").map { file ->
                parse<Scenario>(file).also { it.id = scenarioRoot.relativize(file).invariantSeparatorsPathString.removeSuffix(".yaml") }
            }
        val schemas =
            files(directory.resolve(SCHEMAS), SCHEMA_EXTENSION).associate { file ->
                file.nameWithoutExtension to readJson(file)
            }
        return Suite(directory, catalogue, scenarios, schemas)
    }

    private inline fun <reified T> parse(file: Path): T {
        if (!file.isRegularFile()) throw SuiteLoadException("$file does not exist")
        return try {
            yaml.readValue<T>(file.readText())
        } catch (exception: JacksonException) {
            throw SuiteLoadException("$file: ${exception.originalMessage}", exception)
        }
    }

    private fun readJson(file: Path): JsonNode =
        try {
            yaml.readTree(file.readText())
        } catch (exception: JacksonException) {
            throw SuiteLoadException("$file: ${exception.originalMessage}", exception)
        }

    private fun files(
        root: Path,
        extension: String,
    ): List<Path> {
        if (!root.isDirectory()) return emptyList()
        return Files.walk(root).use { paths -> paths.filter { it.isRegularFile() && it.extension == extension }.sorted().toList() }
    }
}
