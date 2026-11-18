package org.openfsp.simulator.control.web

import org.openfsp.simulator.control.Declaration
import org.openfsp.simulator.control.api.ControlSimulation
import org.openfsp.simulator.dialects.openfsp.OpenFspAmounts
import org.openfsp.simulator.journal.JournalEntry
import org.openfsp.simulator.payment.Outcome
import org.openfsp.simulator.payment.SimulatedPayment
import org.openfsp.simulator.scenario.Scenario
import org.openfsp.simulator.statement.StatementDays
import org.springframework.http.HttpStatus
import org.openfsp.simulator.shared.web.ControlProblem
import org.openfsp.simulator.shared.web.orNotFound
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

    @PutMapping("/operators/{operator}/orders/{reference}/scenario")
    fun declare(
        @PathVariable operator: String,
        @PathVariable reference: String,
        @RequestBody declaration: ScenarioDeclaration,
    ): ResponseEntity<Void> = accepted(control.declare(operator, reference, declaration.scenario))

    @PutMapping("/operators/{operator}/references/{reference}/scenario")
    fun declareByReference(
        @PathVariable operator: String,
        @PathVariable reference: String,
        @RequestBody declaration: ScenarioDeclaration,
    ): ResponseEntity<Void> =
        when (control.declareByReference(operator, reference, declaration.scenario)) {
            Declaration.DECLARED -> ResponseEntity.noContent().build()
            Declaration.UNSUPPORTED -> throw unsupported()
            Declaration.UNKNOWN_OPERATOR -> throw ControlProblem(HttpStatus.NOT_FOUND, "$operator does not take scenarios by reference")
        }

    @GetMapping("/operators/{operator}/default-scenario")
    fun defaultScenario(
        @PathVariable operator: String,
    ): DefaultScenario = DefaultScenario(control.defaultFor(operator), control.rotates(operator))

    @PutMapping("/operators/{operator}/automatic")
    fun rotate(
        @PathVariable operator: String,
        @RequestBody rotation: Rotation,
    ): ResponseEntity<Void> = if (control.rotate(operator, rotation.enabled)) ResponseEntity.noContent().build() else throw unknownOperator(operator)

    @PutMapping("/operators/{operator}/default-scenario")
    fun declareDefault(
        @PathVariable operator: String,
        @RequestBody declaration: ScenarioDeclaration,
    ): ResponseEntity<Void> = accepted(control.declareDefault(operator, declaration.scenario))

    @GetMapping("/operators/{operator}/statements")
    fun statementDays(
        @PathVariable operator: String,
    ): StatementDays = control.statementDays(operator)

    @PostMapping("/operators/{operator}/statements/{day}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun closeDay(
        @PathVariable operator: String,
        @PathVariable day: LocalDate,
    ) = control.closeDay(operator, day)

    @PostMapping("/operators/{operator}/statements/{day}/entries")
    fun addEntry(
        @PathVariable operator: String,
        @PathVariable day: LocalDate,
        @RequestBody entry: UnmatchedEntry,
    ): ResponseEntity<Any> {
        val amount = entry.amount.toBigDecimalOrNull()?.takeIf { runCatching { amounts.toMoney(it) }.isSuccess } ?: throw invalid("amount must be a decimal amount in HTG with at most two decimals")
        val bookedAt = runCatching { LocalDateTime.parse(entry.bookedAt) }.getOrNull() ?: throw invalid("booked_at must be a local date-time such as 2026-11-11T08:30:00")
        control.addEntry(operator, day, amount, bookedAt)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/payments")
    fun payments(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) operator: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) order: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "$DEFAULT_PAGE_SIZE") size: Int,
    ): PageResource<SimulatedPaymentResource> =
        control
            .payments()
            .filter { operator.isNullOrBlank() || it.operator == operator }
            .filter { state.isNullOrBlank() || stateOf(it) == state }
            .filter { listOfNotNull(it.paymentId, it.reference, it.msisdn, it.amount).any { value -> value.matches(q) } }
            .sorted(sort, order, PAYMENT_ORDER, compareByDescending { it.createdAt })
            .page(page, size)
            .map { it.toResource() }

    @GetMapping("/operators/{operator}/references/{reference}")
    fun paymentByReference(
        @PathVariable operator: String,
        @PathVariable reference: String,
    ): SimulatedPaymentState = control.paymentByReference(operator, reference).orNotFound("$operator holds no payment with this reference").toState()

    @PostMapping("/payments/{paymentId}/resolve")
    fun resolve(
        @PathVariable paymentId: String,
        @RequestBody resolution: Resolution,
    ): SimulatedPaymentResource = control.resolve(paymentId, resolution.outcome, resolution.reason).orNotFound(UNKNOWN_PAYMENT).toResource()

    @PostMapping("/payments/{paymentId}/callback")
    fun deliverCallback(
        @PathVariable paymentId: String,
    ): SimulatedPaymentResource = control.deliverCallback(paymentId).orNotFound(UNKNOWN_PAYMENT).toResource()

    @GetMapping("/journal")
    fun journal(
        @RequestParam(required = false) operator: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "$DEFAULT_PAGE_SIZE") size: Int,
    ): PageResource<JournalEntry> =
        control
            .journal(operator?.takeIf { it.isNotBlank() })
            .filter { "${it.method} ${it.path} ${it.body}".matches(q) }
            .sortedByDescending { it.receivedAt }
            .page(page, size)

    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reset() = control.reset()
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

private const val UNKNOWN_PAYMENT = "No simulated payment has this id"

private fun invalid(detail: String) = ControlProblem(HttpStatus.BAD_REQUEST, detail)

private fun unknownOperator(operator: String) = ControlProblem(HttpStatus.NOT_FOUND, "No simulated operator is named $operator")

private fun unsupported() = ControlProblem(HttpStatus.UNPROCESSABLE_CONTENT, "The operator cannot produce this scenario")

private fun accepted(supported: Boolean): ResponseEntity<Void> = if (supported) ResponseEntity.noContent().build() else throw unsupported()
