package org.openfsp.simulator.checkout.web

import org.openfsp.simulator.dialects.DialectReturns
import org.openfsp.simulator.payment.api.OperatePayments
import org.openfsp.simulator.space.SpaceId
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/{operator}/checkout/{paymentId}")
class CheckoutController(
    private val operations: OperatePayments,
    private val page: CheckoutPage,
    returns: List<DialectReturns>,
) {
    private val returnsByDialect = returns.associateBy { it.dialect }

    @GetMapping(produces = ["text/html"])
    fun show(
        space: SpaceId,
        @PathVariable operator: String,
        @PathVariable paymentId: String,
    ): ResponseEntity<String> {
        val payment = operations.checkout(space, operator, paymentId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(page.render(space, operator, payment))
    }

    @PostMapping
    fun decide(
        space: SpaceId,
        @PathVariable operator: String,
        @PathVariable paymentId: String,
        @RequestParam decision: String,
    ): ResponseEntity<Void> {
        val payment = operations.pay(space, operator, paymentId, decision == PAY) ?: return ResponseEntity.notFound().build()
        val profile = operations.profile(operator) ?: return ResponseEntity.notFound().build()
        val target = payment.returnUrl ?: profile.returnUrl?.let { returnsByDialect.getValue(profile.dialect).returnTo(it, payment) } ?: return ResponseEntity.noContent().build()
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(target)).build()
    }

    private companion object {
        const val PAY = "pay"
    }
}
