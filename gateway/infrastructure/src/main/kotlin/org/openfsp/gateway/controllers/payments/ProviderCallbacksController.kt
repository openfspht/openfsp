package org.openfsp.gateway.controllers.payments

import jakarta.servlet.http.HttpServletRequest
import org.openfsp.gateway.common.spi.OperatorSignals
import org.openfsp.gateway.payment.api.CallbackOutcome
import org.openfsp.gateway.payment.api.SettleAPayment
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class ProviderCallbacksController(
    private val settlement: SettleAPayment,
    private val signals: OperatorSignals,
) {
    @PostMapping("/callbacks/{provider}")
    fun signed(
        @PathVariable provider: String,
        @RequestBody body: ByteArray,
        request: HttpServletRequest,
    ): ResponseEntity<Void> =
        when (settlement.onSignedCallback(provider, request::getHeader, body)) {
            is CallbackOutcome.Applied -> ResponseEntity.noContent().build()
            CallbackOutcome.Unverified -> {
                signals.signal(provider, "rejected a callback: signature not verified")
                ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            }
            CallbackOutcome.UnknownProvider, CallbackOutcome.UnknownPayment -> ResponseEntity.notFound().build()
        }

    @PostMapping("/callbacks/{provider}/{token}")
    fun onPaymentUrl(
        @PathVariable provider: String,
        @PathVariable token: String,
        @RequestBody body: ByteArray,
    ): ResponseEntity<Void> {
        if (settlement.onPaymentUrlCallback(provider, token, body) != null) return ResponseEntity.noContent().build()
        signals.signal(provider, "rejected a callback on a payment URL: unknown token, terminal payment or unreadable body")
        return ResponseEntity.notFound().build()
    }
}
