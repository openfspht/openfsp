package org.openfsp.validator.suites

import org.openfsp.validator.json.Json
import org.openfsp.validator.shared.json.JsonTrees
import org.openfsp.validator.shared.json.ValidatorJson
import org.openfsp.validator.suite.Catalogue
import org.openfsp.validator.suite.Scenario
import org.openfsp.validator.suite.Suite
import org.openfsp.validator.suite.SuiteLocale
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
    const val LOCALES = "locales"
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
        val locales = files(directory.resolve(LOCALES), SCENARIO_EXTENSION).associate { file -> file.nameWithoutExtension to parse<SuiteLocale>(file) }
        return Suite(catalogue, scenarios, schemas, locales).also(::checkLocales)
    }

    private inline fun <reified T> parse(file: Path): T {
        if (!file.isRegularFile()) throw SuiteLoadException("$file does not exist")
        return read(file) { yaml.readValue<T>(it) }
    }

    private fun checkLocales(suite: Suite) {
        val requirements = suite.catalogue.requirements.map { it.id }.toSet()
        val limits = suite.catalogue.limits.map { it.reference }.toSet()
        val scenarios = suite.scenarios.associateBy { it.id }
        val problems =
            suite.locales.flatMap { (language, locale) ->
                coverage("$language requirements", requirements, locale.requirements.keys) +
                    coverage("$language limits", limits, locale.limits.keys) +
                    coverage("$language scenarios", scenarios.keys, locale.scenarios.keys) +
                    locale.scenarios.mapNotNull { (id, text) ->
                        val assisted = scenarios[id]?.assisted != null
                        "$language scenario $id ${if (assisted) "lacks its action" else "has an action but is not assisted"}".takeIf { assisted != (text.action != null) && id in scenarios }
                    }
            }
        if (problems.isNotEmpty()) throw SuiteLoadException("the translations of the suite do not match it", problems)
    }

    private fun coverage(
        what: String,
        expected: Set<String>,
        translated: Set<String>,
    ): List<String> = (expected - translated).map { "$what: $it is not translated" } + (translated - expected).map { "$what: $it does not exist" }

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
