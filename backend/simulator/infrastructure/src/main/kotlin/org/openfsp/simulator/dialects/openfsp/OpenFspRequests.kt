package org.openfsp.simulator.dialects.openfsp

import org.openfsp.protocol.Currency
import org.openfsp.protocol.FieldError
import org.openfsp.protocol.FieldErrorCode
import org.openfsp.protocol.JsonPointer
import org.openfsp.protocol.Money
import org.openfsp.protocol.ProblemType
import org.openfsp.protocol.References
import org.openfsp.protocol.api.PaymentRequest
import org.openfsp.rules.PaymentRequestRules
import org.openfsp.simulator.payment.CreatePaymentAtOperator
import org.openfsp.simulator.shared.configuration.SimulatorProperties
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.InetAddress
import java.net.URI

@Component
class OpenFspRequests(
    private val json: JsonMapper,
    private val amounts: OpenFspAmounts,
    private val properties: SimulatorProperties,
) {
    fun parse(body: ByteArray?): JsonNode? {
        if (body == null || body.isEmpty()) return null
        return runCatching { json.readTree(body) }.getOrNull() ?: throw OpenFspException(ProblemType.MALFORMED_REQUEST)
    }

    fun webhookUrlOf(body: JsonNode?): String? {
        val tree = body ?: return null
        if (!tree.isObject) throw OpenFspException(ProblemType.MALFORMED_REQUEST)
        val errors = mutableListOf<FieldError>()
        val url = tree.text(WEBHOOK_URL, errors)
        if (url != null && !isAbsoluteHttp(url)) errors.malformed(WEBHOOK_URL)
        if (errors.isNotEmpty()) throw OpenFspException(ProblemType.INVALID_FIELD, errors = errors)
        return url
    }

    fun toPayment(
        operator: String,
        body: JsonNode?,
        perPaymentUrl: Boolean,
    ): CreatePaymentAtOperator {
        val tree = body?.takeIf { it.isObject } ?: throw OpenFspException(ProblemType.MALFORMED_REQUEST)
        val nested =
            NESTED_FIELDS.flatMap { (member, known) ->
                tree.present(member)?.takeIf { it.isObject }?.propertyNames().orEmpty().filter { it !in known }.map { JsonPointer.of(member, it) }
            }
        val unknown = tree.propertyNames().filter { it !in PaymentRequest.FIELDS }.map { JsonPointer.of(it) } + nested
        if (unknown.isNotEmpty()) throw OpenFspException(ProblemType.UNKNOWN_FIELD, errors = unknown.map { FieldError(it, FieldErrorCode.UNKNOWN_FIELD) })
        val missing =
            REQUIRED.mapNotNull { field -> JsonPointer.of(field).takeIf { tree.present(field) == null } } +
                NESTED_REQUIRED.flatMap { (member, required) ->
                    tree.present(member)?.takeIf { it.isObject }?.let { parent -> required.filter { parent.present(it) == null }.map { JsonPointer.of(member, it) } }.orEmpty()
                }
        if (missing.isNotEmpty()) throw OpenFspException(ProblemType.MISSING_FIELD, errors = missing.sortedBy { POINTER_ORDER.indexOf(it) }.map { FieldError(it, FieldErrorCode.REQUIRED) })

        val errors = mutableListOf<FieldError>()
        val reference = tree.text("reference", errors)?.takeIf { References.isValid(it) } ?: null.also { errors.malformed("reference") }
        val amount = amountOf(tree.get("amount"), errors)
        tree.text("provider", errors)?.let { if (it != operator) errors += FieldError(JsonPointer.of("provider"), FieldErrorCode.NOT_ALLOWED, "is not served here") }
        val phoneNumber = tree.present("payer")?.let { phoneNumberOf(it, errors) }
        val description = tree.text("description", errors)?.also { if (it.codePointCount(0, it.length) > MAX_DESCRIPTION) errors += FieldError(JsonPointer.of("description"), FieldErrorCode.TOO_LONG) }
        val returnUrl = tree.text("return_url", errors)?.let { url -> url.takeIf { PaymentRequestRules.returnUrl(it, loopbackHttpAllowed = true) != null } ?: null.also { errors.malformed("return_url") } }
        tree.text("expires_at", errors)?.let { if (PaymentRequestRules.utcTimestamp(it) == null) errors.malformed("expires_at") }
        val locale = tree.text("locale", errors)?.also { if (it !in OpenFspLocales.SUPPORTED) errors += FieldError(JsonPointer.of("locale"), FieldErrorCode.NOT_ALLOWED) }
        val metadata =
            tree.present("metadata")?.let { metadata ->
                if (!metadata.isObject) {
                    null.also { errors += FieldError(JsonPointer.of("metadata"), FieldErrorCode.INVALID_TYPE) }
                } else {
                    PaymentRequestRules.metadata(metadata.properties().associate { it.key to it.value.takeUnless { value -> value.isNull }?.let { value -> value.stringOrNull() ?: value } }, errors)
                }
            }
        val callbackUrl = tree.text("callback_url", errors)?.takeIf(::isAbsoluteHttp) ?: tree.present("callback_url")?.let { null.also { errors.malformed("callback_url") } }
        if (callbackUrl != null && !isDeliverable(URI(callbackUrl))) {
            errors += FieldError(JsonPointer.of("callback_url"), FieldErrorCode.NOT_ALLOWED, "must use https unless it targets a loopback host")
        }
        if (errors.isNotEmpty()) throw OpenFspException(ProblemType.INVALID_FIELD, errors = errors.distinctBy { it.field })
        if (callbackUrl != null && !perPaymentUrl) throw OpenFspException(ProblemType.CAPABILITY_NOT_SUPPORTED, "callback_url needs webhooks.per_payment_url, which this operator does not announce")

        return CreatePaymentAtOperator(
            reference = reference!!,
            amount = amounts.toDecimal(amount!!),
            msisdn = phoneNumber,
            description = description,
            metadata = metadata,
            callbackUrl = callbackUrl,
            locale = locale,
            returnUrl = returnUrl,
        )
    }

    private fun phoneNumberOf(
        payer: JsonNode,
        errors: MutableList<FieldError>,
    ): String? {
        if (!payer.isObject) return null.also { errors += FieldError(JsonPointer.of("payer"), FieldErrorCode.INVALID_TYPE) }
        val phoneNumber = payer.get(PHONE_NUMBER)
        if (!phoneNumber.isString) return null.also { errors += FieldError(JsonPointer.of("payer", PHONE_NUMBER), FieldErrorCode.INVALID_TYPE) }
        return phoneNumber.stringValue().takeIf(E164::matches) ?: null.also { errors.malformed("payer", PHONE_NUMBER) }
    }

    private fun amountOf(
        node: JsonNode,
        errors: MutableList<FieldError>,
    ): Money? {
        if (!node.isObject) return null.also { errors += FieldError(JsonPointer.of("amount"), FieldErrorCode.INVALID_TYPE) }
        val cents = node.get(AMOUNT)
        val currency = node.get(CURRENCY)
        if (!currency.isString) return null.also { errors += FieldError(JsonPointer.of("amount", CURRENCY), FieldErrorCode.INVALID_TYPE) }
        if (!cents.isIntegralNumber || !cents.canConvertToLong()) return null.also { errors += FieldError(JsonPointer.of("amount", AMOUNT), FieldErrorCode.INVALID_TYPE) }
        if (cents.longValue() <= 0) return null.also { errors += FieldError(JsonPointer.of("amount", AMOUNT), FieldErrorCode.NOT_POSITIVE) }
        if (cents.longValue() > Money.MAX_AMOUNT) return null.also { errors += FieldError(JsonPointer.of("amount", AMOUNT), FieldErrorCode.TOO_LARGE) }
        if (currency.stringOrNull() != Currency.HTG.code) return null.also { errors += FieldError(JsonPointer.of("amount", CURRENCY), FieldErrorCode.NOT_ALLOWED) }
        return Money.htg(cents.longValue())
    }

    private fun JsonNode.present(name: String): JsonNode? = get(name)?.takeUnless { it.isNull || it.isMissingNode }

    private fun JsonNode.stringOrNull(): String? = if (isString) stringValue() else null

    private fun JsonNode.text(
        name: String,
        errors: MutableList<FieldError>,
    ): String? {
        val node = present(name) ?: return null
        return node.stringOrNull() ?: null.also { errors += FieldError(JsonPointer.of(name), FieldErrorCode.INVALID_TYPE) }
    }

    private fun MutableList<FieldError>.malformed(vararg path: String) {
        add(FieldError(JsonPointer.of(*path), FieldErrorCode.MALFORMED))
    }

    private fun isAbsoluteHttp(value: String): Boolean = runCatching { URI(value) }.getOrNull()?.let { it.isAbsolute && it.scheme in SCHEMES && it.host != null } == true

    private fun isDeliverable(url: URI): Boolean = url.scheme == HTTPS || url.host.lowercase() in LOOPBACK_NAMES + properties.httpCallbackHosts || isLoopbackLiteral(url.host)

    private fun isLoopbackLiteral(host: String): Boolean {
        val literal = host.removePrefix("[").removeSuffix("]")
        if (!IPV4.matches(literal) && !literal.contains(':')) return false
        return runCatching { InetAddress.getByName(literal).isLoopbackAddress }.getOrDefault(false)
    }

    private companion object {
        const val HTTPS = "https"
        const val WEBHOOK_URL = "webhook_url"
        val LOOPBACK_NAMES = setOf("localhost")
        val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
        const val AMOUNT = "amount"
        const val CURRENCY = "currency"
        const val PHONE_NUMBER = "phone_number"
        const val MAX_DESCRIPTION = 255
        val REQUIRED = listOf("reference", "amount", "provider")
        val MONEY_FIELDS = setOf(AMOUNT, CURRENCY)
        val NESTED_FIELDS = mapOf("amount" to MONEY_FIELDS, "payer" to setOf(PHONE_NUMBER))
        val NESTED_REQUIRED = mapOf("amount" to listOf(AMOUNT, CURRENCY), "payer" to listOf(PHONE_NUMBER))
        val POINTER_ORDER = listOf("/reference", "/amount", "/amount/amount", "/amount/currency", "/provider", "/payer/phone_number")
        val SCHEMES = setOf("http", "https")
        val E164 = Regex("^\\+[1-9]\\d{1,14}$")
    }
}
