package org.openfsp.gateway.payments.web

import jakarta.servlet.http.HttpServletRequest
import org.openfsp.gateway.payment.api.CallbackOutcome
import org.openfsp.gateway.payment.api.SettlePayment
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
    private val settlement: SettlePayment,
) {
    @RequestMapping("/callbacks/{provider}", method = [RequestMethod.GET, RequestMethod.POST])
    fun signed(
        @PathVariable provider: String,
        @RequestBody(required = false) body: ByteArray?,
        request: HttpServletRequest,
    ): ResponseEntity<Void> =
        when (settlement.onSignedCallback(provider, callbackOf(request, body))) {
            is CallbackOutcome.Applied -> ResponseEntity.noContent().build()
            CallbackOutcome.Unverified -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            CallbackOutcome.UnknownProvider, CallbackOutcome.UnknownPayment -> ResponseEntity.notFound().build()
        }

    @RequestMapping("/callbacks/{provider}/{token}", method = [RequestMethod.GET, RequestMethod.POST])
    fun onPaymentUrl(
        @PathVariable provider: String,
        @PathVariable token: String,
        @RequestBody(required = false) body: ByteArray?,
        request: HttpServletRequest,
    ): ResponseEntity<Void> =
        if (settlement.onPaymentUrlCallback(provider, token, callbackOf(request, body)) != null) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()

    private fun callbackOf(
        request: HttpServletRequest,
        body: ByteArray?,
    ) = CallbackRequest(
        headers = request::getHeader,
        parameters = request.parameterMap.mapValues { it.value.first() },
        body = body ?: ByteArray(0),
        method = request.method,
    )
}
