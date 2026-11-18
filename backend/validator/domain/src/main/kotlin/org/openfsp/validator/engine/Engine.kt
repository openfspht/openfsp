package org.openfsp.validator.engine

import org.openfsp.validator.engine.spi.SchemaChecker
import org.openfsp.validator.engine.spi.Transport
import org.openfsp.validator.engine.spi.WebhookInbox
import org.openfsp.validator.json.Json
import org.openfsp.validator.json.JsonArray
import org.openfsp.validator.json.JsonBoolean
import org.openfsp.validator.json.JsonNumber
import org.openfsp.validator.json.JsonString
import org.openfsp.validator.json.at
import org.openfsp.validator.json.spi.JsonCodec
import org.openfsp.validator.json.string
import org.openfsp.validator.suite.AwaitSpec
import org.openfsp.validator.suite.ReceiverAction
import org.openfsp.validator.suite.RequestSpec
import org.openfsp.validator.suite.Requirement
import org.openfsp.validator.suite.Scenario
import org.openfsp.validator.suite.Step
import org.openfsp.validator.suite.Suite
import org.openfsp.validator.suite.WebhookAssertion
import java.time.Duration
import java.time.Instant
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

    companion object {
        val SILENT = object : RunListener {}
    }
}

class Engine(
    private val suite: Suite,
    private val configuration: Configuration,
    private val listener: RunListener,
    private val http: Transport,
    schemas: () -> SchemaChecker,
    private val json: JsonCodec,
) {
    private val checker by lazy(schemas)
    private val expectations = Expectations({ checker }, json)
    private val control = OutcomeControl(configuration.controlEndpoint, configuration.apiKey, http, json)
    private val runId = Variables.random(RUN_ID_LENGTH).lowercase()
    private val nonces = DeliveryNonces()
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
        val selected = plan()
        val results =
            selected.mapIndexed { position, scenario ->
                val index = position + 1
                listener.started(index, selected.size, scenario, requirementOf(scenario))
                run(index, scenario, capabilities).also { listener.finished(index, selected.size, it) }
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
    ): ScenarioResult {
        val requirement = requirementOf(scenario)
        fun result(
            verdict: Verdict,
            reason: String? = null,
            failure: Failure? = null,
            observations: List<String> = emptyList(),
        ) = ScenarioResult(scenario, requirement, verdict, reason, failure, observations)

        val needs = listOfNotNull(requirement.level.takeUnless { requirement.isCore }) + scenario.withCapabilities
        if (capabilities == null && (needs.isNotEmpty() || scenario.withoutCapabilities.isNotEmpty())) return result(Verdict.INCONCLUSIVE, "the capabilities of the target could not be read")
        val provider =
            when (val choice = provider(capabilities, needs, scenario.withoutCapabilities)) {
                is ProviderChoice.Chosen -> choice.id
                is ProviderChoice.Missing -> return result(Verdict.NOT_APPLICABLE, choice.reason)
            }
        scenario.assisted?.takeIf { configuration.needsAssistance(scenario) }?.let { return result(Verdict.ASSISTED, it.action) }
        val steps = scenario.steps.filter { it.runsOn(configuration.target) }
        if (configuration.controlEndpoint == null && steps.any { it.choosesOutcome }) return result(Verdict.FAIL, UNCONTROLLED)

        val variables = Variables(runId, index, inputs, scenario.variables + listOfNotNull(provider?.let { PROVIDER to it }), json)
        val context = Context(variables)
        for ((position, step) in steps.withIndex()) {
            val label = step.name ?: "step ${position + 1}"
            val mismatches =
                try {
                    execute(step, context)
                } catch (exception: UnavailableScenarioException) {
                    return result(Verdict.FAIL, exception.message)
                } catch (exception: PreparationException) {
                    return result(Verdict.INCONCLUSIVE, "$label: ${exception.message}")
                } catch (exception: TransportException) {
                    return result(Verdict.INCONCLUSIVE, "$label: ${exception.message}")
                } catch (exception: TemplateException) {
                    return result(Verdict.INCONCLUSIVE, "$label: ${exception.message}")
                } catch (exception: IllegalArgumentException) {
                    return result(Verdict.INCONCLUSIVE, "$label: the scenario cannot be evaluated: ${exception.message}")
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
        step.control?.let { control.perform(it, context.variables) }
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
        step.check?.let { spec ->
            val check = Checks.find(spec.name) ?: throw TemplateException("unknown check ${spec.name}")
            val exchange = context.last ?: throw TemplateException("check ${spec.name} needs an earlier request")
            mismatches += check.run(spec, exchange, json, { name -> context.exchanges[context.variables.resolve(name)] }, nonces)
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
            inbox.instruct(rule.where.entries.associate { (pointer, value) -> variables.resolve(pointer) to resolved(variables, value) }, reply, rule.times)
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
        if (mismatches.isNotEmpty() && inbox.refused() > 0) {
            throw PreparationException("the webhook receiver at ${inbox.url} refused ${inbox.refused()} delivery(ies) beyond the budget of this run")
        }
        if (mismatches.isNotEmpty() && inbox.deliveries().isEmpty()) {
            mismatches = mismatches + Mismatch("deliveries to ${inbox.url}", "the events of the run delivered to its webhook receiver", "no delivery during the whole run")
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
        val body = json.parse(delivery.body) ?: return false
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
                spec.where.map { (pointer, value) -> "${variables.resolve(pointer)} = ${variables.resolve(value).let { it?.string ?: it?.let(json::text) ?: Expectations.ABSENT }}" }
        ).joinToString(", ").ifEmpty { "any delivery" }

    private fun summary(delivery: Delivery): String {
        val body = json.parse(delivery.body)
        val type = body?.at("/type")?.string
        val sequence = (body?.at("/sequence") as? JsonNumber)?.bigIntegerOrNull()
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
                context.variables.capture(name, body?.let { Pointers.resolve(it, context.variables.resolve(pointer)).firstOrNull()?.node })
            }
        }
        step.captureHeaders.forEach { (name, header) ->
            context.variables.capture(name, exchange.header(header)?.let(::JsonString))
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
                        throw exception.cause as? RuntimeException ?: exception
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
                ?: spec.body?.let { json.write(resolved(variables, it)) }
                ?: base?.body
        val headers = linkedMapOf<String, String>()
        if (base != null) {
            headers.putAll(base.headers)
        } else {
            body?.let { headers[Expectations.CONTENT_TYPE] = Expectations.JSON }
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

    private fun provider(
        capabilities: Map<String, Set<String>>?,
        needs: List<String>,
        excluded: List<String>,
    ): ProviderChoice {
        val announced = capabilities ?: return configuration.provider?.let(ProviderChoice::Chosen) ?: ProviderChoice.Chosen(null)
        val candidates = announced.filter { (_, names) -> names.containsAll(needs) && excluded.none { it in names } }.keys
        configuration.provider?.let { pinned ->
            if (pinned !in announced) return ProviderChoice.Missing("the target does not announce the provider $pinned")
            if (pinned in candidates) return ProviderChoice.Chosen(pinned)
        }
        candidates.firstOrNull()?.let { return ProviderChoice.Chosen(it) }
        if (needs.isEmpty() && excluded.isEmpty()) return ProviderChoice.Chosen(null)
        val wanted = (needs.map { "announcing $it" } + excluded.map { "without $it" }).joinToString(" and ")
        return ProviderChoice.Missing("no provider of the target is $wanted")
    }

    private sealed interface ProviderChoice {
        data class Chosen(
            val id: String?,
        ) : ProviderChoice

        data class Missing(
            val reason: String,
        ) : ProviderChoice
    }

    private fun targetProtocolVersion(): String? {
        val probe = suite.catalogue.suite.discovery
        return runCatching { http.send(OutgoingRequest(GET, configuration.baseUrl.trimEnd('/') + probe.path, emptyMap(), null)) }
            .getOrNull()
            ?.takeIf { it.status == OK }
            ?.json(json)
            ?.at(probe.protocolVersion)
            ?.string
    }

    private fun announcedCapabilities(): Map<String, Set<String>>? {
        val probe = suite.catalogue.suite.capabilities
        val request = OutgoingRequest(GET, configuration.baseUrl.trimEnd('/') + probe.path, mapOf(AUTHORIZATION to "$BEARER ${configuration.apiKey}"), null)
        val document = runCatching { http.send(request) }.getOrNull()?.takeIf { it.status == OK }?.json(json) ?: return null
        val providers = document.at(probe.providers) as? JsonArray ?: return null
        val announced =
            providers.items.map { provider ->
                Triple(
                    provider.at(probe.id)?.string.orEmpty(),
                    (provider.at(probe.names) as? JsonArray)?.items.orEmpty().mapNotNull { it.string }.toSet(),
                    probe.returnUrlRequired?.let { provider.at(it) } == JsonBoolean(true),
                )
            }
        return announced.sortedBy { it.third }.associate { it.first to it.second }
    }

    private fun major(version: String): String = version.substringBefore('.')

    private fun resolved(
        variables: Variables,
        template: Json,
    ) = variables.resolve(template) ?: throw TemplateException("the template resolves to a member captured as absent")

    private companion object {
        const val GET = "GET"
        const val OK = 200
        const val AUTHORIZATION = "Authorization"
        const val BEARER = "Bearer"
        const val PROVIDER = "provider"
        const val INPUT_PREFIX = "input."
        const val WEBHOOK_URL = "webhook_url"
        const val RUN_ID_LENGTH = 8
        const val UNCONTROLLED =
            "This scenario chooses a payment outcome, and the implementation offers no control interface (conformance 4.9.1)."
        val ABSOLUTE = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")
    }
}
