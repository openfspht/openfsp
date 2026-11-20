package org.openfsp.validator.cli

import org.openfsp.validator.engine.CatalogueCheck
import org.openfsp.validator.engine.Checks
import org.openfsp.validator.engine.Configuration
import org.openfsp.validator.engine.TransportException
import org.openfsp.validator.engine.LevelVerdict
import org.openfsp.validator.engine.RunListener
import org.openfsp.validator.engine.ScenarioResult
import org.openfsp.validator.engine.VersionRefusedException
import org.openfsp.validator.engine.spi.Pause
import org.openfsp.validator.engine.spi.SchemaCheckers
import org.openfsp.validator.json.spi.JsonCodec
import org.openfsp.validator.problem.ValidatorException
import org.openfsp.validator.report.ConformanceRunner
import org.openfsp.validator.report.Reports
import org.openfsp.validator.report.api.RunSuite
import org.openfsp.validator.report.spi.ReportFormat
import org.openfsp.validator.reports.JsonReportFormat
import org.openfsp.validator.run.ControlEndpoints
import org.openfsp.validator.run.ServerFixtures
import org.openfsp.validator.run.TestKeys
import org.openfsp.validator.runs.http.JdkTransport
import org.openfsp.validator.runs.http.LocalWebhookReceiver
import org.openfsp.validator.runs.http.WebhookBodies
import org.openfsp.validator.shared.json.JacksonJsonCodec
import org.openfsp.validator.suite.Suite
import org.openfsp.validator.suite.TargetKind
import org.openfsp.validator.suite.spi.SuiteLoadException
import org.openfsp.validator.suites.BundledConformance
import org.openfsp.validator.suites.DirectorySuiteSource
import org.openfsp.validator.suites.NetworkntSchemaCheckers
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.util.concurrent.Executors
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

object ExitCodes {
    const val CORE_PASSED = 0
    const val CORE_FAILED = 1
    const val CORE_INCOMPLETE = 2
    const val REFUSED = 3
    const val USAGE = 64
    const val INVALID_SUITE = 65
    const val UNREACHABLE = 69
    const val INTERNAL = 70
}

class UsageException(
    message: String,
) : RuntimeException(message)

private const val USAGE =
    """Usage: validate BASE_URL [options]

  BASE_URL                 base URL of the server under test (or --base-url URL)
  --api-key KEY            test key of the server (or OPENFSP_API_KEY); asked from the server when omitted
  --control-endpoint URL   control interface of the server, required with --api-key
  --access-code CODE       conformance access code of the target, when it asks for one
  --provider ID            provider to test first, among those the target announces
  --conformance DIR        conformance directory (default OPENFSP_VALIDATOR_CONFORMANCE, then the suite bundled in the validator)
  --report FILE            also write the JSON report to FILE
  --input NAME=VALUE       value an assisted scenario asks for, repeatable
  --only PREFIX            run only the scenarios whose id starts with PREFIX
  --timeout SECONDS        timeout of one HTTP request (default 30)
  --webhook-port PORT      receive the target's webhooks on this local port during the run
  --webhook-bind ADDRESS   address the webhook receiver listens on (default OPENFSP_VALIDATOR_WEBHOOK_BIND, then 127.0.0.1)
  --webhook-url URL        public URL of the receiver, as registered at the target (default http://ADDRESS:PORT/webhooks, 127.0.0.1 for every address)
  --check-catalogue        check the conformance suite and exit"""

class CommandAdapters(
    val json: JsonCodec = JacksonJsonCodec(),
    val schemas: SchemaCheckers = NetworkntSchemaCheckers(),
    val reports: ReportFormat = JsonReportFormat(),
) {
    private val checks = Checks()
    val catalogue = CatalogueCheck(checks)
    val runner: RunSuite = ConformanceRunner(schemas, json, Clock.systemUTC(), Pause(Thread::sleep), Executors.newVirtualThreadPerTaskExecutor(), checks)
}

fun validate(
    args: List<String>,
    environment: Map<String, String>,
    runner: RunSuite? = null,
    adapters: CommandAdapters = CommandAdapters(),
): Int {
    val suiteRunner = runner ?: adapters.runner
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
    val conformance = options.single("conformance") ?: environment[CONFORMANCE_ENVIRONMENT]
    val suite =
        try {
            conformance?.let { DirectorySuiteSource(Path.of(it), adapters.catalogue).suite() } ?: BundledConformance(adapters.catalogue).load()
        } catch (exception: SuiteLoadException) {
            System.err.println(exception.message)
            exception.problems.forEach { System.err.println("catalogue: $it") }
            return ExitCodes.INVALID_SUITE
        }
    if (CHECK_CATALOGUE in options) {
        println("catalogue: ${suite.catalogue.requirements.size} requirements, ${suite.scenarios.size} scenarios, ${suite.schemas.size} schemas")
        return ExitCodes.CORE_PASSED
    }
    val reportPath = options.single("report")?.let(Path::of)
    val receiver =
        try {
            receiver(options, environment, adapters.json)
        } catch (exception: UsageException) {
            System.err.println(exception.message)
            return ExitCodes.USAGE
        }
    val configuration =
        try {
            configuration(options, environment, receiver?.inbox?.url, suite, adapters)
        } catch (exception: UsageException) {
            receiver?.close()
            System.err.println(exception.message)
            System.err.println(USAGE)
            return ExitCodes.USAGE
        } catch (exception: ValidatorException) {
            receiver?.close()
            System.err.println("${exception.kind.title}: ${exception.message}")
            return ExitCodes.REFUSED
        } catch (exception: TransportException) {
            receiver?.close()
            System.err.println("the server under test cannot be reached: ${exception.message}")
            return ExitCodes.UNREACHABLE
        }
    val report =
        try {
            receiver.use { suiteRunner.run(suite, configuration.copy(webhookInbox = it?.inbox), JdkTransport(configuration.timeout), ProgressPrinter) }
        } catch (exception: VersionRefusedException) {
            System.err.println(exception.message)
            return ExitCodes.REFUSED
        } catch (exception: RuntimeException) {
            System.err.println("the run stopped on an internal error and wrote no report: ${exception.message ?: exception.javaClass.name}")
            return ExitCodes.INTERNAL
        }
    print(Reports.summary(report))
    if (reportPath != null) {
        try {
            reportPath.createParentDirectories().writeText(adapters.reports.write(report) + "\n")
            println("Report written to $reportPath")
        } catch (exception: java.io.IOException) {
            System.err.println("the report cannot be written to $reportPath: ${exception.message ?: exception.javaClass.name}")
            return ExitCodes.INTERNAL
        }
    }
    return when (report.core) {
        LevelVerdict.PASS -> ExitCodes.CORE_PASSED
        LevelVerdict.FAIL -> ExitCodes.CORE_FAILED
        else -> ExitCodes.CORE_INCOMPLETE
    }
}

private const val HELP = "help"
private const val BASE_URL = "base-url"
private const val CONFORMANCE_ENVIRONMENT = "OPENFSP_VALIDATOR_CONFORMANCE"
private const val WEBHOOK_BIND_ENVIRONMENT = "OPENFSP_VALIDATOR_WEBHOOK_BIND"
private const val CHECK_CATALOGUE = "check-catalogue"
private val FLAGS = setOf(HELP, CHECK_CATALOGUE)
private val REPEATABLE = setOf("input")
private val VALUED = setOf("base-url", "api-key", "control-endpoint", "access-code", "provider", "conformance", "report", "input", "only", "timeout", "webhook-port", "webhook-bind", "webhook-url")

private fun parse(args: List<String>): Map<String, List<String>> {
    val options = linkedMapOf<String, MutableList<String>>()
    var index = 0
    while (index < args.size) {
        val argument = args[index]
        if (!argument.startsWith("--")) {
            if (BASE_URL in options) throw UsageException("unexpected argument $argument")
            options[BASE_URL] = mutableListOf(argument)
            index++
            continue
        }
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
    webhookUrl: String?,
    suite: Suite,
    adapters: CommandAdapters,
): Configuration {
    val inputs =
        options["input"].orEmpty().associate { pair ->
            val name = pair.substringBefore('=', "")
            if (name.isEmpty()) throw UsageException("--input expects NAME=VALUE")
            name to pair.substringAfter('=')
        }
    val baseUrl = (options.single("base-url") ?: throw UsageException("the base URL of the server under test is required")).trimEnd('/')
    val timeout = Duration.ofSeconds(options.single("timeout")?.let(::seconds) ?: DEFAULT_TIMEOUT_SECONDS)
    val givenKey = options.single("api-key") ?: environment["OPENFSP_API_KEY"]
    if (givenKey != null && !givenKey.startsWith(TestKeys.PREFIX)) throw UsageException("the key must be a test key, starting with ${TestKeys.PREFIX}")
    val givenControl = options.single("control-endpoint")?.trimEnd('/')
    if (givenKey != null && givenControl == null) throw UsageException("--control-endpoint is required with --api-key: the suite chooses payment outcomes through it (conformance 4.9.1)")
    if (givenKey == null && givenControl != null) throw UsageException("--control-endpoint goes with --api-key; without a key the server announces its own")
    if (givenControl != null && !ControlEndpoints.sameOrigin(givenControl, baseUrl)) throw UsageException("--control-endpoint must share the origin of --base-url")
    val provided = if (givenKey == null) ServerFixtures(adapters.json, adapters.schemas).obtain(JdkTransport(timeout), baseUrl, webhookUrl, options.single("access-code"), suite.schemas) else null
    return Configuration(
        target = TargetKind.SERVER,
        baseUrl = baseUrl,
        apiKey = givenKey ?: provided?.apiKey ?: throw UsageException("the target prepares no conformance run: --api-key or OPENFSP_API_KEY is required"),
        controlEndpoint = givenControl ?: provided?.controlEndpoint,
        provider = options.single("provider"),
        inputs = provided?.inputs.orEmpty() + inputs,
        only = options.single("only"),
        timeout = timeout,
        preparedBy = if (provided == null) Configuration.OPERATOR else Configuration.SERVER,
    )
}

private const val DEFAULT_TIMEOUT_SECONDS = 30L

private fun seconds(text: String): Long = text.toLongOrNull()?.takeIf { it > 0 } ?: throw UsageException("--timeout expects a positive number of seconds")
private const val DEFAULT_WEBHOOK_BIND = "127.0.0.1"
private val PORTS = 0..65535

private fun receiver(
    options: Map<String, List<String>>,
    environment: Map<String, String>,
    json: JsonCodec,
): LocalWebhookReceiver? {
    val port = options.single("webhook-port") ?: return null.also { if (options.single("webhook-url") != null) throw UsageException("--webhook-url needs --webhook-port") }
    val number = port.toIntOrNull()?.takeIf { it in PORTS } ?: throw UsageException("--webhook-port expects a port number")
    val bind = options.single("webhook-bind") ?: environment[WEBHOOK_BIND_ENVIRONMENT] ?: DEFAULT_WEBHOOK_BIND
    return try {
        LocalWebhookReceiver(bind, number, options.single("webhook-url"), json, WebhookBodies())
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
