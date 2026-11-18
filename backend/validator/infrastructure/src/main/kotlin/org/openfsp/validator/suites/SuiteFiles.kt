package org.openfsp.validator.suites

import org.openfsp.validator.json.Json
import org.openfsp.validator.shared.json.JsonTrees
import org.openfsp.validator.shared.json.ValidatorJson
import org.openfsp.validator.suite.Catalogue
import org.openfsp.validator.suite.Scenario
import org.openfsp.validator.suite.Suite
import org.openfsp.validator.suite.spi.SuiteLoadException
import tools.jackson.core.JacksonException
import tools.jackson.databind.DeserializationFeature
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

object SuiteFiles {
    const val CATALOGUE = "requirements.yaml"
    const val SCHEMAS = "schemas"
    const val SCENARIOS = "scenarios"
    private const val SCENARIO_EXTENSION = "yaml"
    private const val SCHEMA_EXTENSION = "json"

    val yaml: YAMLMapper =
        YAMLMapper
            .builder()
            .addModule(KotlinModule.Builder().build())
            .addModule(ValidatorJson.suite)
            .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build()

    fun load(directory: Path): Suite {
        if (!directory.isDirectory()) throw SuiteLoadException("conformance directory $directory does not exist")
        val catalogue = parse<Catalogue>(directory.resolve(CATALOGUE))
        val scenarioRoot = directory.resolve(SCENARIOS)
        val scenarios =
            files(scenarioRoot, SCENARIO_EXTENSION).map { file ->
                parse<Scenario>(file).copy(id = scenarioRoot.relativize(file).invariantSeparatorsPathString.removeSuffix(".$SCENARIO_EXTENSION"))
            }
        val schemas = files(directory.resolve(SCHEMAS), SCHEMA_EXTENSION).associate { file -> file.nameWithoutExtension to tree(file) }
        return Suite(catalogue, scenarios, schemas)
    }

    private inline fun <reified T> parse(file: Path): T {
        if (!file.isRegularFile()) throw SuiteLoadException("$file does not exist")
        return read(file) { yaml.readValue<T>(it) }
    }

    private fun tree(file: Path): Json = read(file) { JsonTrees.toJson(yaml.readTree(it)) }

    private fun <T> read(
        file: Path,
        parse: (String) -> T,
    ): T =
        try {
            parse(file.readText())
        } catch (exception: JacksonException) {
            throw SuiteLoadException("$file: ${exception.originalMessage}", cause = exception)
        } catch (exception: IllegalArgumentException) {
            throw SuiteLoadException("$file: ${exception.message}", cause = exception)
        }

    private fun files(
        root: Path,
        extension: String,
    ): List<Path> {
        if (!root.isDirectory()) return emptyList()
        return Files.walk(root).use { paths -> paths.filter { it.isRegularFile() && it.extension == extension }.sorted().toList() }
    }
}
