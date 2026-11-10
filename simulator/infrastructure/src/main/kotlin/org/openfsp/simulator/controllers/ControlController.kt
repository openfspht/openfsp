package org.openfsp.simulator.controllers

import org.openfsp.simulator.operator.JournalEntry
import org.openfsp.simulator.operator.Outcome
import org.openfsp.simulator.operator.Scenario
import org.openfsp.simulator.operator.SimulatedPayment
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
import java.time.Duration
import java.time.LocalDate

data class ScenarioDeclaration(
    val scenario: Scenario,
)

data class Resolution(
    val outcome: Outcome,
    val reason: String? = null,
)

data class ClockAdvance(
    val seconds: Long,
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
    @GetMapping("/scenarios")
    fun scenarios(): Collection<Scenario> = Collection(Scenario.entries)

    @PutMapping("/operators/{operator}/orders/{orderId}/scenario")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun declare(
        @PathVariable operator: String,
        @PathVariable orderId: String,
        @RequestBody declaration: ScenarioDeclaration,
    ) = control.declare(operator, orderId, declaration.scenario)

    @GetMapping("/operators/{operator}/default-scenario")
    fun defaultScenario(
        @PathVariable operator: String,
    ): ScenarioDeclaration = ScenarioDeclaration(control.defaultFor(operator))

    @PutMapping("/operators/{operator}/default-scenario")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun declareDefault(
        @PathVariable operator: String,
        @RequestBody declaration: ScenarioDeclaration,
    ) = control.declareDefault(operator, declaration.scenario)

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
    fun payments(): Collection<SimulatedPayment> = Collection(control.payments())

    @PostMapping("/payments/{paymentId}/resolve")
    fun resolve(
        @PathVariable paymentId: String,
        @RequestBody resolution: Resolution,
    ): ResponseEntity<SimulatedPayment> = ResponseEntity.ofNullable(control.resolve(paymentId, resolution.outcome, resolution.reason))

    @PostMapping("/payments/{paymentId}/callback")
    fun deliverCallback(
        @PathVariable paymentId: String,
    ): ResponseEntity<SimulatedPayment> = ResponseEntity.ofNullable(control.deliverCallback(paymentId))

    @PostMapping("/clock/advance")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun advanceClock(
        @RequestBody advance: ClockAdvance,
    ) = control.advanceClock(Duration.ofSeconds(advance.seconds))

    @GetMapping("/journal")
    fun journal(
        @RequestParam(required = false) operator: String?,
    ): Collection<JournalEntry> = Collection(control.journal(operator))

    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reset() = control.reset()
}
