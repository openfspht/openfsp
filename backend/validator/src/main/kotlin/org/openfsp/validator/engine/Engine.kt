package org.openfsp.validator.engine

import org.openfsp.protocol.OrderIdDerivation
import org.openfsp.validator.suite.AwaitSpec
import org.openfsp.validator.suite.JournalAssertion
import org.openfsp.validator.suite.RequestSpec
import org.openfsp.validator.suite.Requirement
import org.openfsp.validator.suite.Scenario
import org.openfsp.validator.suite.Step
import org.openfsp.validator.suite.Suite
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.MissingNode
import java.time.Instant
import java.util.Base64

class VersionRefusedException(
    message: String,
) : RuntimeException(message)

data class RunResult(
    val startedAt: Instant,
    val finishedAt: Instant,
    val targetProtocolVersion: String?,
    val announcedCapabilities: Map<String, Set<String>>?,
    val results: List<ScenarioResult>,
)

interface RunListener {
    fun started(
        index: Int,
        total: Int,
        scenario: Scenario,
        requirement: Requirement,
    ) {}

    fun finished(
        index: Int,
        total: Int,
        result: ScenarioResult,
    ) {}
}

class Engine(
    private val suite: Suite,
    private val configuration: Configuration,
    private val listener: RunListener = object : RunListener {},
    private val http: Transport = Http(configuration.timeout),
) {
    private val json: JsonMapper = JsonMapper.builder().build()
    private val expectations = Expectations(Schemas(suite.schemas), json)
    private val simulator = Simulator(configuration.simulatorUrl, Http(configuration.timeout), json)
    private val derivation = configuration.derivationKey?.let { OrderIdDerivation(Base64.getDecoder().decode(it)) }
    private val runId = Variables.random(RUN_ID_LENGTH).lowercase()
    private val journalSettle = Durations.parse(suite.catalogue.suite.journalSettle)
    private val inputs: Map<String, String> =
        mapOf(Variables.API_KEY to configuration.apiKey) + configuration.inputs.mapKeys { "$INPUT_PREFIX${it.key}" }

    fun run(): RunResult {
        val startedAt = Instant.now()
        val targetVersion = targetProtocolVersion()
        if (targetVersion != null && major(targetVersion) != major(suite.catalogue.suite.protocolVersion)) {
            throw VersionRefusedException("the target announces protocol $targetVersion; this suite tests ${suite.catalogue.suite.protocolVersion} and refuses another major version")
        }
        val capabilities = announcedCapabilities()
        val selected = plan()
        val results =
            selected.mapIndexed { position, scenario ->
                val index = position + 1
                listener.started(index, selected.size, scenario, suite.requirement(scenario.requirement)!!)
                run(index, scenario, capabilities).also { listener.finished(index, selected.size, it) }
            }
        return RunResult(startedAt, Instant.now(), targetVersion, capabilities, results)
    }

    fun plan(): List<Scenario> =
        suite.scenarios
            .filter { scenario -> suite.requirement(scenario.requirement)?.target == configuration.target }
            .filter { configuration.only == null || it.id.startsWith(configuration.only) }

    private fun run(
        index: Int,
        scenario: Scenario,
        capabilities: Map<String, Set<String>>?,
    ): ScenarioResult {
        val requirement = suite.requirement(scenario.requirement)!!
        fun result(
            verdict: Verdict,
            reason: String? = null,
            failure: Failure? = null,
        ) = ScenarioResult(scenario, requirement, verdict, reason, failure)

        scenario.assisted?.let { assistance ->
            val missing = assistance.inputs.filterNot { it in configuration.inputs }
            if (missing.isNotEmpty()) return result(Verdict.ASSISTED, assistance.action)
        }
        if (!requirement.isCore) {
            capabilities ?: return result(Verdict.INCONCLUSIVE, "the capabilities of the target could not be read")
            val announced = scenario.provider?.let { capabilities[it]?.contains(requirement.level) == true } ?: capabilities.values.any { requirement.level in it }
            if (!announced) return result(Verdict.NOT_APPLICABLE, "${requirement.level} is not announced${scenario.provider?.let { " for $it" } ?: ""}")
        }

        val variables = Variables(runId, index, derivation, inputs, scenario.variables + listOfNotNull(scenario.provider?.let { PROVIDER to it }))
        val context = Context(variables)
        for ((position, step) in scenario.steps.withIndex()) {
            val label = step.name ?: "step ${position + 1}"
            val mismatches =
                try {
                    execute(step, context)
                } catch (exception: PreparationException) {
                    return result(Verdict.INCONCLUSIVE, "$label: ${exception.message}")
                } catch (exception: TransportException) {
                    return result(Verdict.INCONCLUSIVE, "$label: ${exception.message}")
                } catch (exception: TemplateException) {
                    return result(Verdict.INCONCLUSIVE, "$label: ${exception.message}")
                }
            if (mismatches.isNotEmpty()) {
                val failure = Failure(label, mismatches, context.last?.redacted(configuration.secrets + variables.secrets))
                return if (step.setup) result(Verdict.INCONCLUSIVE, "$label: the preparation was refused", failure) else result(Verdict.FAIL, failure = failure)
            }
        }
        return result(Verdict.PASS)
    }

    private class Context(
        val variables: Variables,
    ) {
        val exchanges = mutableMapOf<String, Exchange>()
        var last: Exchange? = null
    }

    private fun execute(
        step: Step,
        context: Context,
    ): List<Mismatch> {
        step.simulator?.let { simulator.perform(it, context.variables) }
        val mismatches = mutableListOf<Mismatch>()
        step.request?.let { spec ->
            val exchange = http.send(build(spec, context))
            remember(step, exchange, context)
            step.expect?.let { mismatches += expectations.evaluate(it, exchange, context.variables, context.exchanges::get) }
        }
        step.await?.let { spec ->
            mismatches += await(spec, context)
            context.last?.let { exchange ->
                remember(step, exchange, context)
                step.expect?.let { mismatches += expectations.evaluate(it, exchange, context.variables, context.exchanges::get) }
            }
        }
        if (mismatches.isEmpty()) capture(step, context)
        step.journal?.let { mismatches += journal(it, context.variables) }
        step.check?.let { spec ->
            val check = Checks.find(spec.name) ?: throw TemplateException("unknown check ${spec.name}")
            val exchange = context.last ?: throw TemplateException("check ${spec.name} needs an earlier request")
            mismatches += check.run(spec, exchange, json)
        }
        return mismatches
    }

    private fun remember(
        step: Step,
        exchange: Exchange,
        context: Context,
    ) {
        context.last = exchange
        step.name?.let { context.exchanges[it] = exchange }
        context.exchanges[RequestSpec.PREVIOUS] = exchange
    }

    private fun await(
        spec: AwaitSpec,
        context: Context,
    ): List<Mismatch> {
        val within = Durations.parse(spec.within)
        val every = Durations.parse(spec.every)
        val deadline = Instant.now().plus(within)
        while (true) {
            val exchange = http.send(build(spec.request, context))
            context.last = exchange
            val mismatches = expectations.evaluate(spec.until, exchange, context.variables, context.exchanges::get)
            if (mismatches.isEmpty()) return emptyList()
            if (Instant.now().plus(every).isAfter(deadline)) return mismatches.map { it.copy(expectation = "within ${spec.within}, ${it.expectation}") }
            Thread.sleep(every)
        }
    }

    private fun capture(
        step: Step,
        context: Context,
    ) {
        val exchange = context.last ?: return
        if (step.capture.isNotEmpty()) {
            val body = exchange.json(json)
            step.capture.forEach { (name, pointer) ->
                context.variables.capture(name, body?.let { Pointers.resolve(it, pointer).firstOrNull()?.node } ?: MissingNode.getInstance())
            }
        }
        step.captureHeaders.forEach { (name, header) ->
            context.variables.capture(name, exchange.header(header)?.let { json.nodeFactory.stringNode(it) } ?: MissingNode.getInstance())
        }
    }

    private fun journal(
        assertion: JournalAssertion,
        variables: Variables,
    ): List<Mismatch> {
        Thread.sleep(journalSettle)
        val all = simulator.journal(assertion, variables)
        assertion.mark?.let { name ->
            variables.capture(name, json.nodeFactory.numberNode(all.size))
            return emptyList()
        }
        val earlier = assertion.since?.let { variables.lookup(it).takeIf(JsonNode::isNumber)?.asInt() ?: throw TemplateException("{{$it}} is not a journal mark") } ?: 0
        val records = all.take((all.size - earlier).coerceAtLeast(0))
        val observed = records.size
        val expected =
            listOfNotNull(
                assertion.count?.let { "exactly $it" to (observed == it) },
                assertion.atLeast?.let { "at least $it" to (observed >= it) },
                assertion.atMost?.let { "at most $it" to (observed <= it) },
            )
        val failed = expected.filterNot { it.second }
        if (failed.isEmpty()) return emptyList()
        val selector = listOfNotNull(assertion.method?.let(variables::resolve), assertion.path?.let(variables::resolve)).joinToString(" ").ifEmpty { "any request" }
        return listOf(
            Mismatch(
                "journal of ${variables.resolve(assertion.operator)}: $selector",
                failed.joinToString(", ") { "${it.first} request(s)" },
                "$observed request(s)" + records.joinToString(prefix = if (records.isEmpty()) "" else ": ") { "${it.method} ${it.path}" },
            ),
        )
    }

    private fun build(
        spec: RequestSpec,
        context: Context,
    ): OutgoingRequest {
        val variables = context.variables
        val base =
            spec.sameAs?.let { name ->
                context.exchanges[name]?.request ?: throw TemplateException("no earlier request named $name to repeat")
            }
        val method = (spec.method ?: base?.method ?: GET).uppercase()
        val url = spec.path?.let(variables::resolve)?.let { if (ABSOLUTE.containsMatchIn(it)) it else configuration.baseUrl.trimEnd('/') + it } ?: base?.url ?: throw TemplateException("the request has no path")
        val body =
            spec.rawBody?.let { variables.resolve(it).toByteArray(Charsets.UTF_8) }
                ?: spec.body?.let { json.writeValueAsBytes(variables.resolve(it)) }
                ?: base?.body
        val headers = linkedMapOf<String, String>()
        if (base != null) {
            headers.putAll(base.headers)
        } else {
            body?.let { headers[Expectations.CONTENT_TYPE] = JSON }
        }
        when (val auth = spec.auth) {
            null -> if (base == null) headers[AUTHORIZATION] = "$BEARER ${configuration.apiKey}"
            RequestSpec.NO_AUTH -> headers.keys.removeIf { it.equals(AUTHORIZATION, ignoreCase = true) }
            else -> headers[AUTHORIZATION] = "$BEARER ${variables.resolve(auth)}"
        }
        spec.headers.forEach { (name, value) ->
            headers.keys.removeIf { it.equals(name, ignoreCase = true) }
            headers[name] = variables.resolve(value)
        }
        return OutgoingRequest(method, url, headers, body)
    }

    private fun targetProtocolVersion(): String? {
        val probe = suite.catalogue.suite.discovery
        return runCatching { http.send(OutgoingRequest(GET, configuration.baseUrl.trimEnd('/') + probe.path, emptyMap(), null)) }
            .getOrNull()
            ?.takeIf { it.status == OK }
            ?.json(json)
            ?.at(probe.protocolVersion)
            ?.takeIf(JsonNode::isString)
            ?.asString()
    }

    private fun announcedCapabilities(): Map<String, Set<String>>? {
        val probe = suite.catalogue.suite.capabilities
        val request = OutgoingRequest(GET, configuration.baseUrl.trimEnd('/') + probe.path, mapOf(AUTHORIZATION to "$BEARER ${configuration.apiKey}"), null)
        val document = runCatching { http.send(request) }.getOrNull()?.takeIf { it.status == OK }?.json(json) ?: return null
        val providers = document.at(probe.providers).takeIf { it.isArray } ?: return null
        return providers.associate { provider -> provider.at(probe.id).asString() to buildSet { provider.at(probe.names).forEach { name -> add(name.asString()) } } }
    }

    private fun major(version: String): String = version.substringBefore('.')

    private companion object {
        const val GET = "GET"
        const val OK = 200
        const val JSON = "application/json"
        const val AUTHORIZATION = "Authorization"
        const val BEARER = "Bearer"
        const val PROVIDER = "provider"
        const val INPUT_PREFIX = "input."
        const val RUN_ID_LENGTH = 8
        val ABSOLUTE = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")
    }
}
