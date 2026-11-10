package org.openfsp.simulator.controllers

import org.openfsp.simulator.operator.JournalEntry
import org.openfsp.simulator.operator.Outcome
import org.openfsp.simulator.operator.Scenario
import org.openfsp.simulator.operator.SimulatedPayment
import org.openfsp.simulator.operator.StatementDays
import org.openfsp.simulator.operator.api.ControlSimulation
import org.springframework.http.HttpStatus
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

data class ScenarioDeclaration(
    val scenario: Scenario,
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
) {
    @GetMapping("/operators/{operator}/scenarios")
    fun scenarios(
        @PathVariable operator: String,
    ): Collection<Scenario> = Collection(control.scenariosFor(operator))

    @PutMapping("/operators/{operator}/orders/{orderId}/scenario")
    fun declare(
        @PathVariable operator: String,
        @PathVariable orderId: String,
        @RequestBody declaration: ScenarioDeclaration,
    ): ResponseEntity<Void> = accepted(control.declare(operator, orderId, declaration.scenario))

    @GetMapping("/operators/{operator}/default-scenario")
    fun defaultScenario(
        @PathVariable operator: String,
    ): ScenarioDeclaration = ScenarioDeclaration(control.defaultFor(operator))

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
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun addEntry(
        @PathVariable operator: String,
        @PathVariable day: LocalDate,
        @RequestBody entry: UnmatchedEntry,
    ) = control.addEntry(operator, day, entry.amount, entry.bookedAt)

    @GetMapping("/payments")
    fun payments(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) operator: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) order: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "$DEFAULT_PAGE_SIZE") size: Int,
    ): PageResource<SimulatedPayment> =
        control
            .payments()
            .filter { operator.isNullOrBlank() || it.operator == operator }
            .filter { state.isNullOrBlank() || stateOf(it) == state }
            .filter { listOfNotNull(it.paymentId, it.orderId, it.msisdn, it.amount).any { value -> value.matches(q) } }
            .sorted(sort, order, PAYMENT_ORDER, compareByDescending { it.createdAt })
            .page(page, size)

    @PostMapping("/payments/{paymentId}/resolve")
    fun resolve(
        @PathVariable paymentId: String,
        @RequestBody resolution: Resolution,
    ): ResponseEntity<SimulatedPayment> = ResponseEntity.ofNullable(control.resolve(paymentId, resolution.outcome, resolution.reason))

    @PostMapping("/payments/{paymentId}/callback")
    fun deliverCallback(
        @PathVariable paymentId: String,
    ): ResponseEntity<SimulatedPayment> = ResponseEntity.ofNullable(control.deliverCallback(paymentId))

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

private fun accepted(supported: Boolean): ResponseEntity<Void> = if (supported) ResponseEntity.noContent().build() else ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).build()
