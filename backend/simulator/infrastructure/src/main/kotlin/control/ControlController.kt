package org.openfsp.simulator.control

import jakarta.servlet.http.HttpServletRequest
import org.openfsp.simulator.control.api.ControlSimulation
import org.openfsp.simulator.journal.JournalEntry
import org.openfsp.simulator.payment.Outcome
import org.openfsp.simulator.payment.SimulatedPayment
import org.openfsp.simulator.scenario.Scenario
import org.openfsp.simulator.shared.web.ControlProblem
import org.openfsp.simulator.shared.web.orNotFound
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

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

data class Collection<T>(
    val data: List<T>,
)

@RestController
@RequestMapping(ControlController.PATH, InterfaceControlFilter.PATH)
class ControlController(
    private val control: ControlSimulation,
    private val access: ControlAccess,
) {
    @GetMapping("/operators/{operator}/scenarios")
    fun scenarios(
        @PathVariable operator: String,
        request: HttpServletRequest,
    ): Collection<Scenario> {
        access.scopeOf(request)
        return Collection(control.scenariosFor(operator))
    }

    @PutMapping("/operators/{operator}/orders/{reference}/scenario")
    fun declare(
        @PathVariable operator: String,
        @PathVariable reference: String,
        @RequestBody declaration: ScenarioDeclaration,
        request: HttpServletRequest,
    ): ResponseEntity<Void> = accepted(control.declare(operator, access.scopeOf(request).account, reference, declaration.scenario))

    @GetMapping("/operators/{operator}/default-scenario")
    fun defaultScenario(
        @PathVariable operator: String,
        request: HttpServletRequest,
    ): DefaultScenario {
        val account = access.scopeOf(request).account
        return DefaultScenario(control.defaultFor(operator, account), control.rotates(operator, account))
    }

    @PutMapping("/operators/{operator}/automatic")
    fun rotate(
        @PathVariable operator: String,
        @RequestBody rotation: Rotation,
        request: HttpServletRequest,
    ): ResponseEntity<Void> {
        val account = access.scopeOf(request).account
        return if (control.rotate(operator, account, rotation.enabled)) ResponseEntity.noContent().build() else throw unknownOperator(operator)
    }

    @PutMapping("/operators/{operator}/default-scenario")
    fun declareDefault(
        @PathVariable operator: String,
        @RequestBody declaration: ScenarioDeclaration,
        request: HttpServletRequest,
    ): ResponseEntity<Void> = accepted(control.declareDefault(operator, access.scopeOf(request).account, declaration.scenario))

    @GetMapping("/payments")
    fun payments(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) operator: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) order: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "$DEFAULT_PAGE_SIZE") size: Int,
        request: HttpServletRequest,
    ): PageResource<SimulatedPaymentResource> =
        control
            .payments(access.scopeOf(request))
            .filter { operator.isNullOrBlank() || it.operator == operator }
            .filter { state.isNullOrBlank() || stateOf(it) == state }
            .filter { listOfNotNull(it.paymentId, it.reference, it.msisdn, it.amount).any { value -> value.matches(q) } }
            .sorted(sort, order, PAYMENT_ORDER, compareByDescending { it.createdAt })
            .page(page, size)
            .map { it.toResource() }

    @PostMapping("/payments/{paymentId}/resolve")
    fun resolve(
        @PathVariable paymentId: String,
        @RequestBody resolution: Resolution,
        request: HttpServletRequest,
    ): SimulatedPaymentResource = control.resolve(access.scopeOf(request), paymentId, resolution.outcome, resolution.reason).orNotFound(UNKNOWN_PAYMENT).toResource()

    @PostMapping("/payments/{paymentId}/callback")
    fun deliverCallback(
        @PathVariable paymentId: String,
        request: HttpServletRequest,
    ): SimulatedPaymentResource = control.deliverCallback(access.scopeOf(request), paymentId).orNotFound(UNKNOWN_PAYMENT).toResource()

    @GetMapping("/journal")
    fun journal(
        @RequestParam(required = false) operator: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "$DEFAULT_PAGE_SIZE") size: Int,
        request: HttpServletRequest,
    ): PageResource<JournalEntry> =
        control
            .journal(access.scopeOf(request), operator?.takeIf { it.isNotBlank() })
            .filter { "${it.method} ${it.path} ${it.body}".matches(q) }
            .sortedByDescending { it.receivedAt }
            .page(page, size)

    companion object {
        const val PATH = "/control"
    }

    @PostMapping("/reset")
    fun reset(request: HttpServletRequest): ResponseEntity<Void> {
        control.reset(access.scopeOf(request))
        return ResponseEntity.noContent().build()
    }
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

private fun unknownOperator(operator: String) = ControlProblem(HttpStatus.NOT_FOUND, "No simulated operator is named $operator")

private fun unsupported() = ControlProblem(HttpStatus.UNPROCESSABLE_CONTENT, "The operator cannot produce this scenario")

private fun accepted(supported: Boolean): ResponseEntity<Void> = if (supported) ResponseEntity.noContent().build() else throw unsupported()
