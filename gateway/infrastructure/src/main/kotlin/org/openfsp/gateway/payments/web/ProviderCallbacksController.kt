package org.openfsp.gateway.payments.web

import jakarta.servlet.http.HttpServletRequest
import org.openfsp.gateway.common.spi.OperatorSignals
import org.openfsp.gateway.payment.CallbackBase
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
    private val callbackBase: CallbackBase,
) {
    @RequestMapping("/callbacks/{provider}", method = [RequestMethod.GET, RequestMethod.POST])
    fun signed(
        @PathVariable provider: String,
        @RequestBody(required = false) body: ByteArray?,
        request: HttpServletRequest,
    ): ResponseEntity<Void> =
        when (settlement.onSignedCallback(provider, callbackOf(request, body, provider, null))) {
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
        if (settlement.onPaymentUrlCallback(provider, token, callbackOf(request, body, provider, token)) != null) return ResponseEntity.noContent().build()
        signals.signal(provider, "rejected a callback on a payment URL: unknown token, terminal payment, bad signature or unreadable body")
        return ResponseEntity.notFound().build()
    }

    private fun callbackOf(
        request: HttpServletRequest,
        body: ByteArray?,
        provider: String,
        token: String?,
    ) = CallbackRequest(
        headers = request::getHeader,
        parameters = request.parameterMap.mapValues { it.value.first() },
        body = body ?: ByteArray(0),
        method = request.method,
        targetUri = callbackBase.forProvider(provider, token),
    )
}
