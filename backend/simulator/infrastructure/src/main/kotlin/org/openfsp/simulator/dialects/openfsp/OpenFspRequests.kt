package org.openfsp.simulator.dialects.openfsp

import org.openfsp.protocol.Currency
import org.openfsp.protocol.FieldError
import org.openfsp.protocol.FieldErrorCode
import org.openfsp.protocol.JsonPointer
import org.openfsp.protocol.Money
import org.openfsp.protocol.ProblemType
import org.openfsp.protocol.References
import org.openfsp.protocol.api.PaymentRequest
import org.openfsp.simulator.configuration.SimulatorProperties
import org.openfsp.simulator.operator.payment.CreatePaymentAtOperator
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.InetAddress
import java.net.URI
import java.time.Instant

@Component
class OpenFspRequests(
    private val json: JsonMapper,
    private val amounts: OpenFspAmounts,
    private val properties: SimulatorProperties,
) {
    fun parse(body: ByteArray?): JsonNode {
        val tree = body?.takeIf { it.isNotEmpty() }?.let { runCatching { json.readTree(it) }.getOrNull() }
        if (tree == null || !tree.isObject) throw OpenFspFailure(ProblemType.MALFORMED_REQUEST)
        return tree
    }

    fun toPayment(
        operator: String,
        tree: JsonNode,
    ): CreatePaymentAtOperator {
        val nested =
            NESTED_FIELDS.flatMap { (member, known) ->
                tree.present(member)?.takeIf { it.isObject }?.propertyNames().orEmpty().filter { it !in known }.map { JsonPointer.of(member, it) }
            }
        val unknown = tree.propertyNames().filter { it !in PaymentRequest.FIELDS }.map { JsonPointer.of(it) } + nested
        if (unknown.isNotEmpty()) throw OpenFspFailure(ProblemType.UNKNOWN_FIELD, errors = unknown.map { FieldError(it, FieldErrorCode.UNKNOWN_FIELD) })
        val missing = REQUIRED.filter { tree.present(it) == null }
        if (missing.isNotEmpty()) throw OpenFspFailure(ProblemType.MISSING_FIELD, errors = missing.map { FieldError(JsonPointer.of(it), FieldErrorCode.REQUIRED) })

        val errors = mutableListOf<FieldError>()
        val reference = tree.text("reference", errors)?.takeIf { References.isValid(it) } ?: null.also { errors.malformed("reference") }
        val amount = amountOf(tree.get("amount"), errors)
        tree.text("provider", errors)?.let { if (it != operator) errors += FieldError(JsonPointer.of("provider"), FieldErrorCode.NOT_ALLOWED, "is not served here") }
        val phoneNumber = tree.present("payer")?.let { phoneNumberOf(it, errors) }
        val description = tree.text("description", errors)?.also { if (it.codePointCount(0, it.length) > MAX_DESCRIPTION) errors += FieldError(JsonPointer.of("description"), FieldErrorCode.TOO_LONG) }
        val returnUrl = tree.text("return_url", errors)?.takeIf(::isAbsoluteHttp) ?: tree.present("return_url")?.let { null.also { errors.malformed("return_url") } }
        tree.text("expires_at", errors)?.let { if (runCatching { Instant.parse(it) }.isFailure) errors.malformed("expires_at") }
        val locale = tree.text("locale", errors)?.also { if (it !in LOCALES) errors += FieldError(JsonPointer.of("locale"), FieldErrorCode.NOT_ALLOWED) }
        tree.present("metadata")?.let { metadata -> if (!metadata.isObject || metadata.properties().any { !it.value.isString }) errors.malformed("metadata") }
        val callbackUrl = tree.text("callback_url", errors)?.takeIf(::isAbsoluteHttp) ?: tree.present("callback_url")?.let { null.also { errors.malformed("callback_url") } }
        if (callbackUrl != null && !isDeliverable(URI(callbackUrl))) {
            errors += FieldError(JsonPointer.of("callback_url"), FieldErrorCode.NOT_ALLOWED, "must use https unless it targets a loopback host")
        }
        if (errors.isNotEmpty()) throw OpenFspFailure(ProblemType.INVALID_FIELD, errors = errors.distinctBy { it.field })

        return CreatePaymentAtOperator(
            orderId = reference!!,
            amount = amounts.toDecimal(amount!!),
            msisdn = phoneNumber,
            description = description,
            callbackUrl = callbackUrl,
            locale = locale,
            returnUrl = returnUrl,
        )
    }

    private fun phoneNumberOf(
        payer: JsonNode,
        errors: MutableList<FieldError>,
    ): String? {
        if (!payer.isObject || payer.propertyNames().any { it != PHONE_NUMBER }) return null.also { errors.malformed("payer") }
        return payer.present(PHONE_NUMBER)?.stringOrNull()?.takeIf(E164::matches) ?: null.also { errors.malformed("payer", PHONE_NUMBER) }
    }

    private fun amountOf(
        node: JsonNode,
        errors: MutableList<FieldError>,
    ): Money? {
        if (!node.isObject || node.propertyNames().any { it !in MONEY_FIELDS }) return null.also { errors.malformed("amount") }
        val cents = node.present(AMOUNT)
        val currency = node.present(CURRENCY)
        if (cents == null || currency == null) return null.also { errors += FieldError(JsonPointer.of("amount", if (cents == null) AMOUNT else CURRENCY), FieldErrorCode.REQUIRED) }
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
        val LOOPBACK_NAMES = setOf("localhost")
        val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
        const val AMOUNT = "amount"
        const val CURRENCY = "currency"
        const val PHONE_NUMBER = "phone_number"
        const val MAX_DESCRIPTION = 255
        val REQUIRED = listOf("reference", "amount", "provider")
        val MONEY_FIELDS = setOf(AMOUNT, CURRENCY)
        val NESTED_FIELDS = mapOf("amount" to MONEY_FIELDS, "payer" to setOf(PHONE_NUMBER))
        val LOCALES = setOf("fr", "ht", "en")
        val SCHEMES = setOf("http", "https")
        val E164 = Regex("^\\+[1-9]\\d{1,14}$")
    }
}
