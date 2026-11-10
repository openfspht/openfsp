package org.openfsp.gateway.controllers.payments

import jakarta.servlet.http.HttpServletRequest
import org.openfsp.gateway.common.spi.OperatorSignals
import org.openfsp.gateway.payment.api.CallbackOutcome
import org.openfsp.gateway.payment.api.SettleAPayment
import org.openfsp.gateway.provider.CallbackRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
class ProviderCallbacksController(
    private val settlement: SettleAPayment,
    private val signals: OperatorSignals,
) {
    @RequestMapping("/callbacks/{provider}", method = [RequestMethod.GET, RequestMethod.POST])
    fun signed(
        @PathVariable provider: String,
        @RequestBody(required = false) body: ByteArray?,
        request: HttpServletRequest,
    ): ResponseEntity<Void> =
        when (settlement.onSignedCallback(provider, callbackOf(request, body))) {
            is CallbackOutcome.Applied -> ResponseEntity.noContent().build()
            CallbackOutcome.Unverified -> {
                signals.signal(provider, "rejected a callback: signature not verified")
                ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            }
            CallbackOutcome.UnknownProvider, CallbackOutcome.UnknownPayment -> ResponseEntity.notFound().build()
        }

    @RequestMapping("/callbacks/{provider}/{token}", method = [RequestMethod.GET, RequestMethod.POST])
    fun onPaymentUrl(
        @PathVariable provider: String,
        @PathVariable token: String,
        @RequestBody(required = false) body: ByteArray?,
        request: HttpServletRequest,
    ): ResponseEntity<Void> {
        if (settlement.onPaymentUrlCallback(provider, token, callbackOf(request, body)) != null) return ResponseEntity.noContent().build()
        signals.signal(provider, "rejected a callback on a payment URL: unknown token, terminal payment, bad signature or unreadable body")
        return ResponseEntity.notFound().build()
    }

    private fun callbackOf(
        request: HttpServletRequest,
        body: ByteArray?,
    ) = CallbackRequest(request::getHeader, request.parameterMap.mapValues { it.value.first() }, body ?: ByteArray(0))
}
