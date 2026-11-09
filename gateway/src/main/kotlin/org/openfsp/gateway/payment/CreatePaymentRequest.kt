package org.openfsp.gateway.payment

import org.openfsp.gateway.web.ApiException
import org.openfsp.protocol.Currency
import org.openfsp.protocol.FieldError
import org.openfsp.protocol.Money
import org.openfsp.protocol.ProblemType
import org.openfsp.protocol.References
import java.net.URI
import java.text.Normalizer
import java.time.Instant

data class MoneyInput(
    val amount: Long?,
    val currency: String?,
)

data class PayerInput(
    val phoneNumber: String?,
)

data class CreatePaymentRequest(
    val reference: String?,
    val amount: MoneyInput?,
    val provider: String?,
    val payer: PayerInput?,
    val description: String?,
    val returnUrl: String?,
    val expiresAt: String?,
    val locale: String?,
    val metadata: Map<String, Any?>?,
)

data class CreatePayment(
    val reference: String,
    val amount: Money,
    val provider: String,
    val payerPhoneNumber: String?,
    val description: String?,
    val returnUrl: URI?,
    val expiresAt: Instant?,
    val locale: String?,
    val metadata: Map<String, String>?,
)

fun CreatePaymentRequest.validate(developmentMode: Boolean = false): CreatePayment {
    val missing =
        listOfNotNull(
            "reference".takeIf { reference == null },
            "amount".takeIf { amount == null },
            "amount.amount".takeIf { amount != null && amount.amount == null },
            "amount.currency".takeIf { amount != null && amount.currency == null },
            "provider".takeIf { provider == null },
            "payer.phone_number".takeIf { payer != null && payer.phoneNumber == null },
        )
    if (missing.isNotEmpty()) {
        throw ApiException(ProblemType.MISSING_FIELD, "Missing: ${missing.joinToString()}", missing.map { FieldError(it, "is required") })
    }

    val errors = mutableListOf<FieldError>()
    val normalizedReference = Normalizer.normalize(reference!!, Normalizer.Form.NFC)
    if (!References.isValid(normalizedReference)) errors += FieldError("reference", "must be 1 to 128 characters among A-Za-z0-9._:/-")
    if (amount!!.amount!! <= 0) errors += FieldError("amount.amount", "must be a positive integer in cents")
    if (amount.amount > Money.MAX_AMOUNT) errors += FieldError("amount.amount", "must not exceed 2^53 - 1")
    if (amount.currency != Currency.HTG.code) errors += FieldError("amount.currency", "must be HTG")
    if (!PROVIDER.matches(provider!!)) errors += FieldError("provider", "is not a provider identifier")
    payer?.phoneNumber?.let { if (!E164.matches(it)) errors += FieldError("payer.phone_number", "must be in E.164 format") }
    description?.let { if (it.codePointCount(0, it.length) > MAX_DESCRIPTION) errors += FieldError("description", "must be at most 255 characters") }
    val parsedReturnUrl =
        returnUrl?.let {
            runCatching { URI(it) }.getOrNull()?.takeIf { uri -> uri.isAbsolute && (uri.scheme == "https" || (developmentMode && uri.isLoopbackHttp())) }
                ?: null.also { errors += FieldError("return_url", "must be an absolute https URL") }
        }
    val parsedExpiresAt =
        expiresAt?.let {
            runCatching { Instant.parse(it) }.getOrNull()?.takeIf { _ -> TIMESTAMP.matches(it) }
                ?: null.also { errors += FieldError("expires_at", "must be an RFC 3339 UTC timestamp ending in Z") }
        }
    locale?.let { if (it !in LOCALES) errors += FieldError("locale", "must be fr, ht or en") }
    val parsedMetadata = metadata?.let { validateMetadata(it, errors) }
    if (errors.isNotEmpty()) throw ApiException(ProblemType.INVALID_FIELD, errors = errors)

    return CreatePayment(
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

private fun validateMetadata(
    metadata: Map<String, Any?>,
    errors: MutableList<FieldError>,
): Map<String, String> {
    if (metadata.size > MAX_METADATA_KEYS) errors += FieldError("metadata", "must have at most 20 keys")
    return metadata.mapNotNull { (key, value) ->
        when {
            !METADATA_KEY.matches(key) -> null.also { errors += FieldError("metadata.$key", "key must be 1 to 40 characters among A-Za-z0-9_.-") }
            value !is String -> null.also { errors += FieldError("metadata.$key", "must be a string") }
            value.codePointCount(0, value.length) > MAX_METADATA_VALUE -> null.also { errors += FieldError("metadata.$key", "must be at most 500 characters") }
            else -> key to value
        }
    }.toMap()
}

private fun URI.isLoopbackHttp(): Boolean = scheme == "http" && host in setOf("localhost", "127.0.0.1", "[::1]")

private val PROVIDER = Regex("^[a-z0-9_]{1,32}$")
private val E164 = Regex("^\\+[1-9]\\d{1,14}$")
private val TIMESTAMP = Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,3})?Z$")
private val METADATA_KEY = Regex("^[A-Za-z0-9_.-]{1,40}$")
private val LOCALES = setOf("fr", "ht", "en")
private const val MAX_DESCRIPTION = 255
private const val MAX_METADATA_KEYS = 20
private const val MAX_METADATA_VALUE = 500
