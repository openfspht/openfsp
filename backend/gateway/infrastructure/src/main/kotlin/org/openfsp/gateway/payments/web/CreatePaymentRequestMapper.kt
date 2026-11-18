package org.openfsp.gateway.payments.web

import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.payment.CreatePaymentCommand
import org.openfsp.gateway.shared.configuration.GatewayProperties
import org.openfsp.protocol.Currency
import org.openfsp.protocol.FieldError
import org.openfsp.protocol.FieldErrorCode
import org.openfsp.protocol.JsonPointer
import org.openfsp.protocol.Money
import org.openfsp.protocol.ProblemType
import org.openfsp.protocol.References
import org.openfsp.rules.PaymentRequestRules
import org.springframework.stereotype.Component
import java.text.Normalizer

@Component
class CreatePaymentRequestMapper(
    properties: GatewayProperties,
) {
    private val developmentMode = properties.developmentMode

    fun toCommand(
        account: String,
        request: CreatePaymentRequest,
    ): CreatePaymentCommand = with(request) {
        val missing =
            listOfNotNull(
                JsonPointer.of("reference").takeIf { reference == null },
                JsonPointer.of("amount").takeIf { amount == null },
                JsonPointer.of("amount", "amount").takeIf { amount != null && amount.amount == null },
                JsonPointer.of("amount", "currency").takeIf { amount != null && amount.currency == null },
                JsonPointer.of("provider").takeIf { provider == null },
                JsonPointer.of("payer", "phone_number").takeIf { payer != null && payer.phoneNumber == null },
            )
        if (missing.isNotEmpty()) {
            throw OpenFspException(ProblemType.MISSING_FIELD, "Missing: ${missing.joinToString()}", missing.map { FieldError(it, FieldErrorCode.REQUIRED) })
        }

        if (callbackUrl != null) {
            throw OpenFspException(ProblemType.CAPABILITY_NOT_SUPPORTED, "No provider of this gateway declares ${PER_PAYMENT_URL}: callback_url is not accepted")
        }

        val errors = mutableListOf<FieldError>()
        val normalizedReference = Normalizer.normalize(reference!!, Normalizer.Form.NFC)
        if (!References.isValid(normalizedReference)) errors += FieldError(JsonPointer.of("reference"), FieldErrorCode.MALFORMED, "must be 1 to 128 characters among A-Za-z0-9._:/-")
        if (amount!!.amount!! <= 0) errors += FieldError(JsonPointer.of("amount", "amount"), FieldErrorCode.NOT_POSITIVE, "must be a positive integer in cents")
        if (amount.amount > Money.MAX_AMOUNT) errors += FieldError(JsonPointer.of("amount", "amount"), FieldErrorCode.TOO_LARGE, "must not exceed 2^53 - 1")
        if (amount.currency != Currency.HTG.code) errors += FieldError(JsonPointer.of("amount", "currency"), FieldErrorCode.NOT_ALLOWED, "must be HTG")
        if (!PROVIDER.matches(provider!!)) errors += FieldError(JsonPointer.of("provider"), FieldErrorCode.MALFORMED, "is not a provider identifier")
        payer?.phoneNumber?.let { if (!E164.matches(it)) errors += FieldError(JsonPointer.of("payer", "phone_number"), FieldErrorCode.MALFORMED, "must be in E.164 format") }
        description?.let { if (it.codePointCount(0, it.length) > MAX_DESCRIPTION) errors += FieldError(JsonPointer.of("description"), FieldErrorCode.TOO_LONG, "must be at most 255 characters") }
        val parsedReturnUrl =
            returnUrl?.let {
                PaymentRequestRules.returnUrl(it, loopbackHttpAllowed = developmentMode)
                    ?: null.also { errors += FieldError(JsonPointer.of("return_url"), FieldErrorCode.MALFORMED, "must be an absolute https URL") }
            }
        val parsedExpiresAt =
            expiresAt?.let {
                PaymentRequestRules.utcTimestamp(it)
                    ?: null.also { errors += FieldError(JsonPointer.of("expires_at"), FieldErrorCode.MALFORMED, "must be an RFC 3339 UTC timestamp ending in Z") }
            }
        locale?.let { if (it !in LOCALES) errors += FieldError(JsonPointer.of("locale"), FieldErrorCode.NOT_ALLOWED, "must be fr, ht or en") }
        val parsedMetadata = metadata?.let { PaymentRequestRules.metadata(it, errors) }
        if (errors.isNotEmpty()) throw OpenFspException(ProblemType.INVALID_FIELD, errors = errors)

        return@with CreatePaymentCommand(
            account = account,
            reference = normalizedReference,
            amount = Money.htg(amount.amount),
            provider = provider,
            payerPhoneNumber = payer?.phoneNumber,
            description = description?.let { Normalizer.normalize(it, Normalizer.Form.NFC) },
            returnUrl = parsedReturnUrl,
            expiresAt = parsedExpiresAt,
            locale = locale,
            metadata = parsedMetadata,
        )
    }

    private companion object {
        const val PER_PAYMENT_URL = "webhooks.per_payment_url"
        val PROVIDER = Regex("^[a-z0-9_]{1,32}$")
        val E164 = Regex("^\\+[1-9]\\d{1,14}$")
        val LOCALES = setOf("fr", "ht", "en")
        const val MAX_DESCRIPTION = 255
    }
}
