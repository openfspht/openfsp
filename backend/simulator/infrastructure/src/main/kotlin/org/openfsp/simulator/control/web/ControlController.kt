package org.openfsp.simulator.control.web

import org.openfsp.simulator.control.Declaration
import org.openfsp.simulator.control.api.ControlSimulation
import org.openfsp.simulator.dialects.openfsp.OpenFspAmounts
import org.openfsp.simulator.journal.JournalEntry
import org.openfsp.simulator.payment.Outcome
import org.openfsp.simulator.payment.SimulatedPayment
import org.openfsp.simulator.scenario.Scenario
import org.openfsp.simulator.space.SpaceId
import org.openfsp.simulator.statement.StatementDays
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalDateTime

data class ScenarioDeclaration(
    val scenario: Scenario,
)

data class DefaultScenario(
    val scenario: Scenario,
    val automatic: Boolean,
)

data class Rotation(
    val enabled: Boolean,
)

data class Resolution(
    val outcome: Outcome,
    val reason: String? = null,
)

data class UnmatchedEntry(
    val amount: String,
    val bookedAt: String,
)

data class Collection<T>(
    val data: List<T>,
)

@RestController
@RequestMapping("/control")
class ControlController(
    private val control: ControlSimulation,
    private val amounts: OpenFspAmounts,
) {
    @GetMapping("/operators/{operator}/scenarios")
    fun scenarios(
        @PathVariable operator: String,
    ): Collection<Scenario> = Collection(control.scenariosFor(operator))

    @PutMapping("/operators/{operator}/orders/{orderId}/scenario")
    fun declare(
        space: SpaceId,
        @PathVariable operator: String,
        @PathVariable orderId: String,
        @RequestBody declaration: ScenarioDeclaration,
    ): ResponseEntity<Void> = accepted(control.declare(space, operator, orderId, declaration.scenario))

    @PutMapping("/operators/{operator}/references/{reference}/scenario")
    fun declareByReference(
        space: SpaceId,
        @PathVariable operator: String,
        @PathVariable reference: String,
        @RequestBody declaration: ScenarioDeclaration,
    ): ResponseEntity<Void> =
        when (control.declareByReference(space, operator, reference, declaration.scenario)) {
            Declaration.DECLARED -> ResponseEntity.noContent().build()
            Declaration.UNSUPPORTED -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).build()
            Declaration.UNKNOWN_OPERATOR -> ResponseEntity.notFound().build()
        }

    @GetMapping("/operators/{operator}/default-scenario")
    fun defaultScenario(
        space: SpaceId,
        @PathVariable operator: String,
    ): DefaultScenario = DefaultScenario(control.defaultFor(space, operator), control.rotates(space, operator))

    @PutMapping("/operators/{operator}/automatic")
    fun rotate(
        space: SpaceId,
        @PathVariable operator: String,
        @RequestBody rotation: Rotation,
    ): ResponseEntity<Void> = if (control.rotate(space, operator, rotation.enabled)) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()

    @PutMapping("/operators/{operator}/default-scenario")
    fun declareDefault(
        space: SpaceId,
        @PathVariable operator: String,
        @RequestBody declaration: ScenarioDeclaration,
    ): ResponseEntity<Void> = accepted(control.declareDefault(space, operator, declaration.scenario))

    @GetMapping("/operators/{operator}/statements")
    fun statementDays(
        space: SpaceId,
        @PathVariable operator: String,
    ): StatementDays = control.statementDays(space, operator)

    @PostMapping("/operators/{operator}/statements/{day}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun closeDay(
        space: SpaceId,
        @PathVariable operator: String,
        @PathVariable day: LocalDate,
    ) = control.closeDay(space, operator, day)

    @PostMapping("/operators/{operator}/statements/{day}/entries")
    fun addEntry(
        space: SpaceId,
        @PathVariable operator: String,
        @PathVariable day: LocalDate,
        @RequestBody entry: UnmatchedEntry,
    ): ResponseEntity<Any> {
        val amount = entry.amount.toBigDecimalOrNull()?.takeIf { runCatching { amounts.toMoney(it) }.isSuccess } ?: return invalid("amount must be a decimal amount in HTG with at most two decimals")
        val bookedAt = runCatching { LocalDateTime.parse(entry.bookedAt) }.getOrNull() ?: return invalid("booked_at must be a local date-time such as 2026-11-11T08:30:00")
        control.addEntry(space, operator, day, amount, bookedAt)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/payments")
    fun payments(
        space: SpaceId,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) operator: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) order: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "$DEFAULT_PAGE_SIZE") size: Int,
    ): PageResource<SimulatedPayment> =
        control
            .payments(space)
            .filter { operator.isNullOrBlank() || it.operator == operator }
            .filter { state.isNullOrBlank() || stateOf(it) == state }
            .filter { listOfNotNull(it.paymentId, it.orderId, it.msisdn, it.amount).any { value -> value.matches(q) } }
            .sorted(sort, order, PAYMENT_ORDER, compareByDescending { it.createdAt })
            .page(page, size)

    @PostMapping("/payments/{paymentId}/resolve")
    fun resolve(
        space: SpaceId,
        @PathVariable paymentId: String,
        @RequestBody resolution: Resolution,
    ): ResponseEntity<SimulatedPayment> = ResponseEntity.ofNullable(control.resolve(space, paymentId, resolution.outcome, resolution.reason))

    @PostMapping("/payments/{paymentId}/callback")
    fun deliverCallback(
        space: SpaceId,
        @PathVariable paymentId: String,
    ): ResponseEntity<SimulatedPayment> = ResponseEntity.ofNullable(control.deliverCallback(space, paymentId))

    @GetMapping("/journal")
    fun journal(
        space: SpaceId,
        @RequestParam(required = false) operator: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "$DEFAULT_PAGE_SIZE") size: Int,
    ): PageResource<JournalEntry> =
        control
            .journal(space, operator?.takeIf { it.isNotBlank() })
            .filter { "${it.method} ${it.path} ${it.body}".matches(q) }
            .sortedByDescending { it.receivedAt }
            .page(page, size)

    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reset(space: SpaceId) = control.reset(space)
}

private const val WAITING = "waiting"

private fun stateOf(payment: SimulatedPayment): String = payment.outcome?.name?.lowercase() ?: WAITING

private val PAYMENT_ORDER: Map<String, Comparator<SimulatedPayment>> =
    mapOf(
        "created_at" to compareBy { it.createdAt },
        "amount" to compareBy { it.amount.toBigDecimal() },
        "operator" to compareBy { it.operator },
        "state" to compareBy { stateOf(it) },
    )

private fun invalid(detail: String): ResponseEntity<Any> = ResponseEntity.of(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail)).build()

private fun accepted(supported: Boolean): ResponseEntity<Void> = if (supported) ResponseEntity.noContent().build() else ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).build()
