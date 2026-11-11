package org.openfsp.validator

import org.openfsp.protocol.OrderIdDerivation
import org.openfsp.validator.engine.Configuration
import org.openfsp.validator.engine.Engine
import org.openfsp.validator.engine.LevelVerdict
import org.openfsp.validator.engine.VersionRefusedException
import org.openfsp.validator.report.Reports
import org.openfsp.validator.suite.CatalogueCheck
import org.openfsp.validator.suite.SuiteLoadException
import org.openfsp.validator.suite.SuiteLoader
import org.openfsp.validator.suite.TargetKind
import java.nio.file.Path
import java.time.Duration
import java.util.Base64
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText
import kotlin.system.exitProcess

object ExitCodes {
    const val CORE_PASSED = 0
    const val CORE_FAILED = 1
    const val CORE_INCOMPLETE = 2
    const val REFUSED = 3
    const val USAGE = 64
}

class UsageException(
    message: String,
) : RuntimeException(message)

private const val USAGE =
    """Usage: openfsp-validate --base-url URL [options]

  --target KIND            gateway (default), native-operator or client
  --base-url URL           base URL of the implementation under test
  --api-key KEY            API key of the target (or OPENFSP_API_KEY)
  --simulator-url URL      control surface of the simulator
  --derivation-key BASE64  order id derivation key given to the target (or OPENFSP_DERIVATION_KEY)
  --conformance DIR        conformance directory (default ./conformance)
  --report FILE            JSON report path (default openfsp-conformance-report.json)
  --input NAME=VALUE       value an assisted scenario asks for, repeatable
  --only PREFIX            run only the scenarios whose id starts with PREFIX
  --timeout SECONDS        timeout of one HTTP request (default 30)
  --check-catalogue        check the conformance directory and exit"""

fun main(args: Array<String>) {
    exitProcess(run(args.toList(), System.getenv()))
}

fun run(
    args: List<String>,
    environment: Map<String, String>,
): Int {
    val options =
        try {
            parse(args)
        } catch (exception: UsageException) {
            System.err.println(exception.message)
            System.err.println(USAGE)
            return ExitCodes.USAGE
        }
    if (HELP in options) {
        println(USAGE)
        return ExitCodes.CORE_PASSED
    }
    val conformance = Path.of(options.single("conformance") ?: "conformance")
    val suite =
        try {
            SuiteLoader.load(conformance)
        } catch (exception: SuiteLoadException) {
            System.err.println(exception.message)
            return ExitCodes.USAGE
        }
    val problems = CatalogueCheck.problems(suite)
    if (problems.isNotEmpty()) {
        problems.forEach { System.err.println("catalogue: $it") }
        return ExitCodes.USAGE
    }
    if (CHECK_CATALOGUE in options) {
        println("catalogue: ${suite.catalogue.requirements.size} requirements, ${suite.scenarios.size} scenarios, ${suite.schemas.size} schemas")
        return ExitCodes.CORE_PASSED
    }
    val configuration =
        try {
            configuration(options, environment, conformance)
        } catch (exception: UsageException) {
            System.err.println(exception.message)
            System.err.println(USAGE)
            return ExitCodes.USAGE
        }
    val run =
        try {
            Engine(suite, configuration) { System.err.println("${it.verdict} ${it.scenario.id}") }.run()
        } catch (exception: VersionRefusedException) {
            System.err.println(exception.message)
            return ExitCodes.REFUSED
        }
    val report = Reports.of(suite, configuration, run)
    configuration.report.createParentDirectories().writeText(Reports.json.writeValueAsString(report) + "\n")
    print(Reports.summary(report))
    println("Report written to ${configuration.report}")
    return when (report.core) {
        LevelVerdict.PASS -> ExitCodes.CORE_PASSED
        LevelVerdict.FAIL -> ExitCodes.CORE_FAILED
        else -> ExitCodes.CORE_INCOMPLETE
    }
}

private const val HELP = "help"
private const val CHECK_CATALOGUE = "check-catalogue"
private val FLAGS = setOf(HELP, CHECK_CATALOGUE)
private val REPEATABLE = setOf("input")
private val VALUED = setOf("target", "base-url", "api-key", "simulator-url", "derivation-key", "conformance", "report", "input", "only", "timeout")

private fun parse(args: List<String>): Map<String, List<String>> {
    val options = linkedMapOf<String, MutableList<String>>()
    var index = 0
    while (index < args.size) {
        val argument = args[index]
        if (!argument.startsWith("--")) throw UsageException("unexpected argument $argument")
        val name = argument.removePrefix("--").substringBefore('=')
        val inline = argument.substringAfter('=', "").takeIf { '=' in argument }
        when (name) {
            in FLAGS -> options.getOrPut(name) { mutableListOf() }
            in VALUED -> {
                val value = inline ?: args.getOrNull(++index) ?: throw UsageException("--$name needs a value")
                val values = options.getOrPut(name) { mutableListOf() }
                if (values.isNotEmpty() && name !in REPEATABLE) throw UsageException("--$name is given twice")
                values += value
            }
            else -> throw UsageException("unknown option --$name")
        }
        index++
    }
    return options
}

private fun Map<String, List<String>>.single(name: String): String? = this[name]?.single()

private fun configuration(
    options: Map<String, List<String>>,
    environment: Map<String, String>,
    conformance: Path,
): Configuration {
    val target =
        try {
            TargetKind.of(options.single("target") ?: TargetKind.GATEWAY.wire)
        } catch (exception: IllegalArgumentException) {
            throw UsageException(exception.message ?: "unknown target kind")
        }
    val inputs =
        options["input"].orEmpty().associate { pair ->
            val name = pair.substringBefore('=', "")
            if (name.isEmpty()) throw UsageException("--input expects NAME=VALUE")
            name to pair.substringAfter('=')
        }
    val derivationKey = options.single("derivation-key") ?: environment["OPENFSP_DERIVATION_KEY"]
    derivationKey?.let { key ->
        try {
            OrderIdDerivation(Base64.getDecoder().decode(key))
        } catch (exception: IllegalArgumentException) {
            throw UsageException("the derivation key must be the base64 form of at least 32 bytes: ${exception.message}")
        }
    }
    return Configuration(
        target = target,
        baseUrl = options.single("base-url") ?: throw UsageException("--base-url is required"),
        apiKey = options.single("api-key") ?: environment["OPENFSP_API_KEY"] ?: throw UsageException("--api-key or OPENFSP_API_KEY is required"),
        simulatorUrl = options.single("simulator-url"),
        derivationKey = derivationKey,
        conformance = conformance,
        report = Path.of(options.single("report") ?: "openfsp-conformance-report.json"),
        inputs = inputs,
        only = options.single("only"),
        timeout = Duration.ofSeconds(options.single("timeout")?.toLongOrNull() ?: DEFAULT_TIMEOUT_SECONDS),
    )
}

private const val DEFAULT_TIMEOUT_SECONDS = 30L
