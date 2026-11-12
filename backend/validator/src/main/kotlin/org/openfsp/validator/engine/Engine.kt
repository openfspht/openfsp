package org.openfsp.validator.engine

import org.openfsp.protocol.OrderIdDerivation
import org.openfsp.validator.suite.AwaitSpec
import org.openfsp.validator.suite.JournalAssertion
import org.openfsp.validator.suite.ReceiverAction
import org.openfsp.validator.suite.RecordSelector
import org.openfsp.validator.suite.RequestSpec
import org.openfsp.validator.suite.Requirement
import org.openfsp.validator.suite.Scenario
import org.openfsp.validator.suite.Step
import org.openfsp.validator.suite.Suite
import org.openfsp.validator.suite.TargetKind
import org.openfsp.validator.suite.WebhookAssertion
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.MissingNode
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Locale
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

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
    private val orderIds: ((String) -> String)? =
        if (configuration.target == TargetKind.NATIVE_OPERATOR) {
            { reference -> reference }
        } else {
            configuration.derivationKey?.let { OrderIdDerivation(Base64.getDecoder().decode(it)) }?.let { derivation -> derivation::derive }
        }
    private val runId = Variables.random(RUN_ID_LENGTH).lowercase()
    private val journalSettle = Durations.parse(suite.catalogue.suite.journalSettle)
    private val inputs: Map<String, String> =
        mapOf(Variables.API_KEY to configuration.apiKey) +
            listOfNotNull(configuration.webhookInbox?.let { WEBHOOK_URL to it.url }) +
            configuration.inputs.mapKeys { "$INPUT_PREFIX${it.key}" }

    fun run(): RunResult {
        val startedAt = Instant.now()
        val targetVersion = targetProtocolVersion()
        if (targetVersion != null && major(targetVersion) != major(suite.catalogue.suite.protocolVersion)) {
            throw VersionRefusedException("the target announces protocol $targetVersion; this suite tests ${suite.catalogue.suite.protocolVersion} and refuses another major version")
        }
        val capabilities = announcedCapabilities()
        val ownProvider = capabilities?.keys?.singleOrNull().takeIf { configuration.target == TargetKind.NATIVE_OPERATOR }
        val selected = plan()
        val results =
            selected.mapIndexed { position, scenario ->
                val index = position + 1
                listener.started(index, selected.size, scenario, requirementOf(scenario))
                run(index, scenario, capabilities, ownProvider).also { listener.finished(index, selected.size, it) }
            }
        return RunResult(startedAt, Instant.now(), targetVersion, capabilities, results)
    }

    fun plan(): List<Scenario> =
        suite.scenarios
            .filter { scenario -> suite.applies(scenario, configuration.target) }
            .filter { configuration.only == null || it.id.startsWith(configuration.only) }

    fun requirementOf(scenario: Scenario): Requirement = suite.requirement(scenario.requirement, configuration.target)!!

    private fun run(
        index: Int,
        scenario: Scenario,
        capabilities: Map<String, Set<String>>?,
        ownProvider: String?,
    ): ScenarioResult {
        val requirement = requirementOf(scenario)
        fun result(
            verdict: Verdict,
            reason: String? = null,
            failure: Failure? = null,
            observations: List<String> = emptyList(),
        ) = ScenarioResult(scenario, requirement, verdict, reason, failure, observations)

        scenario.assisted?.let { assistance ->
            val missing = assistance.inputs.filterNot { it in configuration.inputs }
            if (missing.isNotEmpty()) return result(Verdict.ASSISTED, assistance.action)
        }
        val provider =
            if (configuration.target == TargetKind.NATIVE_OPERATOR && scenario.provider != null) {
                ownProvider ?: return result(Verdict.INCONCLUSIVE, "a native operator announces exactly one provider, itself, in its capabilities; they could not be read or name several")
            } else {
                scenario.provider
            }

        fun announces(capability: String): Boolean = provider?.let { capabilities?.get(it)?.contains(capability) == true } ?: capabilities.orEmpty().values.any { capability in it }
        val forWhom = provider?.let { " for $it" }.orEmpty()
        if (capabilities == null && (!requirement.isCore || scenario.withoutCapability != null)) return result(Verdict.INCONCLUSIVE, "the capabilities of the target could not be read")
        if (!requirement.isCore && !announces(requirement.level)) return result(Verdict.NOT_APPLICABLE, "${requirement.level} is not announced$forWhom")
        scenario.withoutCapability?.let { if (announces(it)) return result(Verdict.NOT_APPLICABLE, "$it is announced$forWhom; this scenario tests its absence") }
        val steps = scenario.steps.filter { it.runsOn(configuration.target) }
        if (configuration.simulatorUrl == null && steps.any { it.drivesSimulator }) return result(Verdict.ASSISTED, UNCONTROLLED)

        val variables = Variables(runId, index, orderIds, inputs, scenario.variables + listOfNotNull(provider?.let { PROVIDER to it }))
        val context = Context(variables)
        for ((position, step) in steps.withIndex()) {
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
                return if (step.setup) result(Verdict.INCONCLUSIVE, "$label: the preparation was refused", failure) else result(Verdict.FAIL, failure = failure, observations = context.observations)
            }
        }
        return result(Verdict.PASS, observations = context.observations)
    }

    private class Context(
        val variables: Variables,
    ) {
        val exchanges = mutableMapOf<String, Exchange>()
        var last: Exchange? = null
        val observations = mutableListOf<String>()
    }

    private fun execute(
        step: Step,
        context: Context,
    ): List<Mismatch> {
        step.simulator?.let { simulator.perform(it, context.variables) }
        step.receiver?.let { receiver(it, context.variables) }
        val mismatches = mutableListOf<Mismatch>()
        if (step.parallel.isNotEmpty()) mismatches += parallel(step.parallel, context)
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
        step.webhook?.let { spec ->
            val (delivery, found) = webhook(spec, context)
            mismatches += found
            delivery?.let { remember(step, it, context) }
            if (found.isEmpty() && step.request == null && step.await == null) {
                step.expect?.let { expectation ->
                    mismatches += delivery?.let { expectations.evaluate(expectation, it, context.variables, context.exchanges::get) } ?: listOf(Mismatch("delivery", "a delivery to examine", "none selected"))
                }
            }
        }
        if (mismatches.isEmpty()) capture(step, context)
        step.journal?.let { assertion ->
            val (newest, found) = journal(assertion, context.variables) { held -> context.observations += "${step.name ?: "journal"}: observed for ${seconds(held)}" }
            mismatches += found
            if (found.isEmpty() && step.request == null && step.await == null && step.webhook == null) {
                step.expect?.let { expectation ->
                    mismatches += newest?.let { expectations.evaluate(expectation, it, context.variables, context.exchanges::get) } ?: listOf(Mismatch("journal", "a request to examine", "none selected"))
                }
            }
        }
        step.check?.let { spec ->
            val check = Checks.find(spec.name) ?: throw TemplateException("unknown check ${spec.name}")
            val exchange = context.last ?: throw TemplateException("check ${spec.name} needs an earlier request")
            mismatches += check.run(spec, exchange, json) { name -> context.exchanges[context.variables.resolve(name)] }
        }
        return mismatches
    }

    private fun inbox(): WebhookInbox = configuration.webhookInbox ?: throw PreparationException("no webhook receiver is configured for this run")

    private fun receiver(
        action: ReceiverAction,
        variables: Variables,
    ) {
        val inbox = inbox()
        action.reply?.let { rule ->
            val reply =
                Reply(
                    status = rule.status,
                    headers = rule.headers.mapValues { variables.resolve(it.value) },
                    body = rule.body?.let(variables::resolve)?.toByteArray(Charsets.UTF_8) ?: ByteArray(0),
                    delay = rule.delay?.let(Durations::parse) ?: Duration.ZERO,
                )
            inbox.instruct(rule.where.entries.associate { (pointer, value) -> variables.resolve(pointer) to variables.resolve(value) }, reply, rule.times)
        }
    }

    private fun webhook(
        spec: WebhookAssertion,
        context: Context,
    ): Pair<Exchange?, List<Mismatch>> {
        val inbox = inbox()
        val variables = context.variables
        val every = Durations.parse(spec.every)

        fun once(): Pair<List<Delivery>, List<Mismatch>> {
            val selected = inbox.deliveries().filter { selects(spec, it, variables) }
            val observed = selected.size
            val expected =
                listOfNotNull(
                    spec.count?.let { "exactly $it" to (observed == it) },
                    spec.atLeast?.let { "at least $it" to (observed >= it) },
                    spec.atMost?.let { "at most $it" to (observed <= it) },
                ).ifEmpty { listOf("at least ${spec.pick}" to (observed >= spec.pick)) }
            val failed = expected.filterNot { it.second }
            if (failed.isEmpty()) return selected to emptyList()
            val label = "deliveries to ${inbox.url}: ${describe(spec, variables)}"
            val seen = "$observed delivery(ies)" + selected.joinToString(prefix = if (selected.isEmpty()) "" else ": ") { summary(it) }
            return selected to listOf(Mismatch(label, failed.joinToString(", ") { "${it.first} delivery(ies)" }, seen))
        }
        var (selected, mismatches) = once()
        spec.during?.let { during ->
            val end = Instant.now().plus(Durations.parse(during))
            while (mismatches.isEmpty() && !Instant.now().plus(every).isAfter(end)) {
                Thread.sleep(every)
                val (now, found) = once()
                selected = now
                mismatches = found.map { it.copy(expectation = "during $during, ${it.expectation}") }
            }
        }
        spec.within?.let { within ->
            val deadline = Instant.now().plus(Durations.parse(within))
            while (mismatches.isNotEmpty() && !Instant.now().plus(every).isAfter(deadline)) {
                Thread.sleep(every)
                val (now, found) = once()
                selected = now
                mismatches = found
            }
            if (mismatches.isNotEmpty()) mismatches = mismatches.map { it.copy(expectation = "within $within, ${it.expectation}") }
        }
        if (mismatches.isNotEmpty() && inbox.deliveries().isEmpty()) {
            throw PreparationException("the webhook receiver at ${inbox.url} has received no delivery during this run; register it at the target for every event type")
        }
        val picked = selected.getOrNull(spec.pick - 1)?.exchange()
        val delay = spec.notSooner
        if (mismatches.isEmpty() && picked?.receivedAt != null && delay != null) {
            val reference = spec.after?.let { context.exchanges[variables.resolve(it)] } ?: throw TemplateException("not-sooner needs after, naming an earlier step that selected a delivery")
            val from = reference.receivedAt ?: throw TemplateException("step ${spec.after} selected no delivery")
            if (picked.receivedAt.isBefore(from.plus(Durations.parse(delay)))) {
                mismatches = listOf(Mismatch("delay since ${spec.after}", "at least $delay", "${Duration.between(from, picked.receivedAt).toMillis()}ms"))
            }
        }
        return picked to mismatches
    }

    private fun selects(
        spec: WebhookAssertion,
        delivery: Delivery,
        variables: Variables,
    ): Boolean {
        if (spec.method != null && !delivery.method.equals(variables.resolve(spec.method), ignoreCase = true)) return false
        if (spec.path != null && !Regex(variables.resolve(spec.path)).matches(delivery.path)) return false
        if (spec.headers.any { (name, value) -> delivery.header(name) != variables.resolve(value) }) return false
        if (spec.where.isEmpty()) return true
        val body = runCatching { json.readTree(delivery.body) }.getOrNull()?.takeUnless { it.isMissingNode } ?: return false
        return spec.where.all { (pointer, value) ->
            val expected = variables.resolve(value)
            Pointers.resolve(body, variables.resolve(pointer)).let { located -> located.isNotEmpty() && located.all { it.node == expected } }
        }
    }

    private fun describe(
        spec: WebhookAssertion,
        variables: Variables,
    ): String =
        (
            listOfNotNull(spec.method?.let(variables::resolve), spec.path?.let { "path ${variables.resolve(it)}" }) +
                spec.headers.map { (name, value) -> "$name: ${variables.resolve(value)}" } +
                spec.where.map { (pointer, value) -> "${variables.resolve(pointer)} = ${variables.resolve(value).let { if (it.isString) it.asString() else it.toString() }}" }
        ).joinToString(", ").ifEmpty { "any delivery" }

    private fun summary(delivery: Delivery): String {
        val body = runCatching { json.readTree(delivery.body) }.getOrNull()
        val type = body?.path("type")?.takeIf { it.isString }?.asString()
        val sequence = body?.path("sequence")?.takeIf { it.isNumber }?.asInt()
        return listOfNotNull(delivery.method, delivery.path, type, sequence?.let { "sequence $it" }, "answered ${delivery.status}").joinToString(" ")
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
                context.variables.capture(name, body?.let { Pointers.resolve(it, context.variables.resolve(pointer)).firstOrNull()?.node } ?: MissingNode.getInstance())
            }
        }
        step.captureHeaders.forEach { (name, header) ->
            context.variables.capture(name, exchange.header(header)?.let { json.nodeFactory.stringNode(it) } ?: MissingNode.getInstance())
        }
    }

    private fun parallel(
        steps: List<Step>,
        context: Context,
    ): List<Mismatch> {
        val requests = steps.map { step -> build(step.request ?: throw TemplateException("a parallel step needs a request"), context) }
        val exchanges =
            Executors.newVirtualThreadPerTaskExecutor().use { executor ->
                requests.map { request -> executor.submit<Exchange> { http.send(request) } }.map { future ->
                    try {
                        future.get()
                    } catch (exception: ExecutionException) {
                        throw exception.cause as? TransportException ?: exception
                    }
                }
            }
        return steps.zip(exchanges).flatMap { (step, exchange) ->
            remember(step, exchange, context)
            val mismatches = step.expect?.let { expectations.evaluate(it, exchange, context.variables, context.exchanges::get) }.orEmpty()
            if (mismatches.isEmpty()) capture(step, context)
            mismatches.map { it.copy(expectation = "${step.name ?: "parallel request"}: ${it.expectation}") }
        }
    }

    private fun journal(
        assertion: JournalAssertion,
        variables: Variables,
        held: (Duration) -> Unit,
    ): Pair<Exchange?, List<Mismatch>> {
        val every = Durations.parse(assertion.every)
        assertion.during?.let { during ->
            val started = Instant.now()
            val end = started.plus(Durations.parse(during))
            while (true) {
                Thread.sleep(journalSettle)
                val (newest, mismatches) = journalOnce(assertion, variables)
                if (mismatches.isNotEmpty()) return newest to mismatches.map { it.copy(expectation = "during $during, after ${seconds(Duration.between(started, Instant.now()))}, ${it.expectation}") }
                if (!Instant.now().isBefore(end)) {
                    held(Duration.between(started, Instant.now()))
                    return newest to emptyList()
                }
                Thread.sleep(minOf(every, Duration.between(Instant.now(), end).coerceAtLeast(Duration.ZERO)))
            }
        }
        val deadline = assertion.within?.let { Instant.now().plus(Durations.parse(it)) }
        while (true) {
            Thread.sleep(journalSettle)
            val (newest, mismatches) = journalOnce(assertion, variables)
            if (mismatches.isEmpty() || deadline == null || Instant.now().plus(every).isAfter(deadline)) {
                return newest to if (deadline == null) mismatches else mismatches.map { it.copy(expectation = "within ${assertion.within}, ${it.expectation}") }
            }
            Thread.sleep(every)
        }
    }

    private fun journalOnce(
        assertion: JournalAssertion,
        variables: Variables,
    ): Pair<Exchange?, List<Mismatch>> {
        val all = simulator.journal(assertion, variables)
        assertion.mark?.let { name ->
            variables.capture(name, json.nodeFactory.numberNode(all.size))
            return null to emptyList()
        }
        val earlier = assertion.since?.let { variables.lookup(it).takeIf(JsonNode::isNumber)?.asInt() ?: throw TemplateException("{{$it}} is not a journal mark") } ?: 0
        val recent = all.take((all.size - earlier).coerceAtLeast(0))
        val selector = RecordSelector(assertion.method, assertion.path)
        val records = recent.filter { selects(selector, it, variables) }
        val observed = records.size
        val expected =
            listOfNotNull(
                assertion.count?.let { "exactly $it" to (observed == it) },
                assertion.atLeast?.let { "at least $it" to (observed >= it) },
                assertion.atMost?.let { "at most $it" to (observed <= it) },
            )
        val label = "journal of ${variables.resolve(assertion.operator)}: ${describe(selector, variables)}"
        val mismatches = mutableListOf<Mismatch>()
        val failed = expected.filterNot { it.second }
        if (failed.isNotEmpty()) {
            mismatches += Mismatch(label, failed.joinToString(", ") { "${it.first} request(s)" }, "$observed request(s)" + records.joinToString(prefix = if (records.isEmpty()) "" else ": ") { "${it.method} ${it.path}" })
        }
        mismatches += order(assertion, recent.reversed(), variables)
        val newest = records.firstOrNull()
        val view = newest?.let(::view)
        if (mismatches.isEmpty() && assertion.capture.isNotEmpty()) {
            assertion.capture.forEach { (name, pointer) ->
                variables.capture(name, view?.let { Pointers.resolve(it, variables.resolve(pointer)).firstOrNull()?.node } ?: MissingNode.getInstance())
            }
        }
        val exchange = if (newest == null || view == null) null else Exchange(OutgoingRequest(newest.method, newest.path, emptyMap(), null), newest.status, emptyMap(), json.writeValueAsBytes(view))
        return exchange to mismatches
    }

    private fun order(
        assertion: JournalAssertion,
        chronological: List<JournalRecord>,
        variables: Variables,
    ): List<Mismatch> =
        assertion.order.zipWithNext().mapNotNull { (first, then) ->
            val later = chronological.indexOfFirst { selects(then, it, variables) }
            if (later < 0 || chronological.take(later).any { selects(first, it, variables) }) {
                null
            } else {
                Mismatch(
                    "journal of ${variables.resolve(assertion.operator)}: order",
                    "${describe(first, variables)} before the first ${describe(then, variables)}",
                    chronological.take(later + 1).joinToString { "${it.method} ${it.path}" },
                )
            }
        }

    private fun selects(
        selector: RecordSelector,
        record: JournalRecord,
        variables: Variables,
    ): Boolean =
        (selector.method == null || record.method.equals(variables.resolve(selector.method), ignoreCase = true)) &&
            (selector.path == null || Regex(variables.resolve(selector.path)).matches(record.path))

    private fun describe(
        selector: RecordSelector,
        variables: Variables,
    ): String = listOfNotNull(selector.method?.let(variables::resolve), selector.path?.let(variables::resolve)).joinToString(" ").ifEmpty { "any request" }

    private fun view(record: JournalRecord): JsonNode =
        json.nodeFactory.objectNode().apply {
            put("method", record.method)
            put("path", record.path)
            put("status", record.status)
            set("body", parsed(record.body))
            set("response_body", parsed(record.responseBody))
        }

    private fun parsed(text: String): JsonNode = runCatching { json.readTree(text) }.getOrNull()?.takeUnless { it.isMissingNode } ?: json.nodeFactory.stringNode(text)

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

    private fun seconds(duration: Duration): String = "%.1f s".format(Locale.ROOT, duration.toMillis() / MILLIS_PER_SECOND)

    private companion object {
        const val GET = "GET"
        const val OK = 200
        const val JSON = "application/json"
        const val AUTHORIZATION = "Authorization"
        const val BEARER = "Bearer"
        const val PROVIDER = "provider"
        const val INPUT_PREFIX = "input."
        const val WEBHOOK_URL = "webhook_url"
        const val RUN_ID_LENGTH = 8
        const val MILLIS_PER_SECOND = 1000.0
        const val UNCONTROLLED =
            "This scenario declares an operator outcome or reads the request journal on the simulator control surface: run it against the simulator, or against mock_openfsp as a native operator, with --simulator-url. A real native operator has to provoke the outcome by its own test means."
        val ABSOLUTE = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")
    }
}
