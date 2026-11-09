package org.openfsp.simulator.control

import org.openfsp.simulator.JournalEntry
import org.openfsp.simulator.Outcome
import org.openfsp.simulator.Scenario
import org.openfsp.simulator.SimulatedPayment
import org.openfsp.simulator.SimulatorState
import org.openfsp.simulator.operator.Settlement
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

data class Collection<T>(
    val data: List<T>,
)

@RestController
@RequestMapping("/control")
class ControlSurface(
    private val state: SimulatorState,
    private val settlement: Settlement,
) {
    @GetMapping("/scenarios")
    fun scenarios(): Collection<Scenario> = Collection(Scenario.entries)

    @PutMapping("/operators/{operator}/orders/{orderId}/scenario")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun declare(
        @PathVariable operator: String,
        @PathVariable orderId: String,
        @RequestBody declaration: ScenarioDeclaration,
    ) = state.declare(operator, orderId, declaration.scenario)

    @GetMapping("/operators/{operator}/default-scenario")
    fun defaultScenario(
        @PathVariable operator: String,
    ): ScenarioDeclaration = ScenarioDeclaration(state.defaultFor(operator))

    @PutMapping("/operators/{operator}/default-scenario")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun declareDefault(
        @PathVariable operator: String,
        @RequestBody declaration: ScenarioDeclaration,
    ) = state.declareDefault(operator, declaration.scenario)

    @GetMapping("/payments")
    fun payments(): Collection<SimulatedPayment> = Collection(state.payments())

    @PostMapping("/payments/{paymentId}/resolve")
    fun resolve(
        @PathVariable paymentId: String,
        @RequestBody resolution: Resolution,
    ): ResponseEntity<SimulatedPayment> = ResponseEntity.ofNullable(settlement.resolve(paymentId, resolution.outcome, resolution.reason))

    @PostMapping("/payments/{paymentId}/callback")
    fun deliverCallback(
        @PathVariable paymentId: String,
    ): ResponseEntity<SimulatedPayment> {
        val payment = state.find(paymentId) ?: return ResponseEntity.notFound().build()
        settlement.sendCallback(payment)
        return ResponseEntity.ok(state.find(paymentId))
    }

    @PostMapping("/clock/advance")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun advanceClock(
        @RequestBody advance: ClockAdvance,
    ) = state.advanceClock(Duration.ofSeconds(advance.seconds))

    @GetMapping("/journal")
    fun journal(
        @RequestParam(required = false) operator: String?,
    ): Collection<JournalEntry> = Collection(state.journal(operator))

    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reset() = state.reset()
}
