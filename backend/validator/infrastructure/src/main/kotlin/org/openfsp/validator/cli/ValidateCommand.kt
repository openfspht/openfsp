package org.openfsp.validator.cli

import org.openfsp.validator.engine.Configuration
import org.openfsp.validator.engine.LevelVerdict
import org.openfsp.validator.engine.RunListener
import org.openfsp.validator.engine.ScenarioResult
import org.openfsp.validator.engine.VersionRefusedException
import org.openfsp.validator.engine.api.RunSuite
import org.openfsp.validator.http.JdkTransport
import org.openfsp.validator.http.LocalWebhookReceiver
import org.openfsp.validator.report.Reports
import org.openfsp.validator.shared.configuration.EngineAdapters
import org.openfsp.validator.suite.TargetKind
import org.openfsp.validator.suite.spi.SuiteLoadException
import org.openfsp.validator.suites.DirectorySuiteSource
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

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

  --target KIND            server (default) or client
  --base-url URL           base URL of the implementation under test
  --api-key KEY            API key of the target (or OPENFSP_API_KEY)
  --provider ID            provider to test first, among those the target announces
  --simulator-url URL      control surface of the mock server, when it is the target
  --conformance DIR        conformance directory (default ./conformance)
  --report FILE            JSON report path (default openfsp-conformance-report.json)
  --input NAME=VALUE       value an assisted scenario asks for, repeatable
  --only PREFIX            run only the scenarios whose id starts with PREFIX
  --timeout SECONDS        timeout of one HTTP request (default 30)
  --webhook-port PORT      receive the target's webhooks on this local port during the run
  --webhook-bind ADDRESS   address the webhook receiver listens on (default 127.0.0.1)
  --webhook-url URL        public URL of the receiver, as registered at the target (default http://ADDRESS:PORT/webhooks)
  --check-catalogue        check the conformance directory and exit"""

fun validate(
    args: List<String>,
    environment: Map<String, String>,
    runner: RunSuite = EngineAdapters.runner(),
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
            DirectorySuiteSource(conformance).suite()
        } catch (exception: SuiteLoadException) {
            System.err.println(exception.message)
            exception.problems.forEach { System.err.println("catalogue: $it") }
            return ExitCodes.USAGE
        }
    if (CHECK_CATALOGUE in options) {
        println("catalogue: ${suite.catalogue.requirements.size} requirements, ${suite.scenarios.size} scenarios, ${suite.schemas.size} schemas")
        return ExitCodes.CORE_PASSED
    }
    val reportPath = Path.of(options.single("report") ?: "openfsp-conformance-report.json")
    val configuration =
        try {
            configuration(options, environment)
        } catch (exception: UsageException) {
            System.err.println(exception.message)
            System.err.println(USAGE)
            return ExitCodes.USAGE
        }
    val receiver =
        try {
            receiver(options)
        } catch (exception: UsageException) {
            System.err.println(exception.message)
            return ExitCodes.USAGE
        }
    val report =
        try {
            receiver.use { runner.run(suite, configuration.copy(webhookInbox = it?.inbox), JdkTransport(configuration.timeout), ProgressPrinter) }
        } catch (exception: VersionRefusedException) {
            System.err.println(exception.message)
            return ExitCodes.REFUSED
        }
    reportPath.createParentDirectories().writeText(EngineAdapters.reports.write(report) + "\n")
    print(Reports.summary(report))
    println("Report written to $reportPath")
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
private val VALUED = setOf("target", "base-url", "api-key", "simulator-url", "provider", "conformance", "report", "input", "only", "timeout", "webhook-port", "webhook-bind", "webhook-url")

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
): Configuration {
    val target =
        try {
            TargetKind.of(options.single("target") ?: TargetKind.SERVER.wire)
        } catch (exception: IllegalArgumentException) {
            throw UsageException(exception.message ?: "unknown target kind")
        }
    val inputs =
        options["input"].orEmpty().associate { pair ->
            val name = pair.substringBefore('=', "")
            if (name.isEmpty()) throw UsageException("--input expects NAME=VALUE")
            name to pair.substringAfter('=')
        }
    return Configuration(
        target = target,
        baseUrl = options.single("base-url") ?: throw UsageException("--base-url is required"),
        apiKey = options.single("api-key") ?: environment["OPENFSP_API_KEY"] ?: throw UsageException("--api-key or OPENFSP_API_KEY is required"),
        simulatorUrl = options.single("simulator-url"),
        provider = options.single("provider"),
        inputs = inputs,
        only = options.single("only"),
        timeout = Duration.ofSeconds(options.single("timeout")?.toLongOrNull() ?: DEFAULT_TIMEOUT_SECONDS),
    )
}

private const val DEFAULT_TIMEOUT_SECONDS = 30L
private const val DEFAULT_WEBHOOK_BIND = "127.0.0.1"
private val PORTS = 0..65535

private fun receiver(options: Map<String, List<String>>): LocalWebhookReceiver? {
    val port = options.single("webhook-port") ?: return null.also { if (options.single("webhook-url") != null) throw UsageException("--webhook-url needs --webhook-port") }
    val number = port.toIntOrNull()?.takeIf { it in PORTS } ?: throw UsageException("--webhook-port expects a port number")
    val bind = options.single("webhook-bind") ?: DEFAULT_WEBHOOK_BIND
    return try {
        LocalWebhookReceiver(bind, number, options.single("webhook-url"))
    } catch (exception: java.io.IOException) {
        throw UsageException("the webhook receiver cannot listen on $bind:$number: ${exception.message}")
    } catch (exception: IllegalArgumentException) {
        throw UsageException("--webhook-url is not a URL: ${exception.message}")
    }
}

private object ProgressPrinter : RunListener {
    override fun finished(
        index: Int,
        total: Int,
        result: ScenarioResult,
    ) = System.err.println("${result.verdict} ${result.scenario.id}")
}
