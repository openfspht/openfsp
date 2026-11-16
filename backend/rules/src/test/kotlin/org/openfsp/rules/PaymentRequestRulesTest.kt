package org.openfsp.rules

import org.openfsp.protocol.FieldError
import org.openfsp.protocol.FieldErrorCode
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PaymentRequestRulesTest {
    private fun metadataErrors(entries: Map<String, Any?>): List<Pair<String, FieldErrorCode>> =
        mutableListOf<FieldError>().also { PaymentRequestRules.metadata(entries, it) }.map { it.field to it.code }

    @Test
    fun `metadata accepts twenty keys of forty characters with values of five hundred characters`() {
        val entries = (1..20).associate { "k".repeat(38) + "%02d".format(it) to "v".repeat(500) }
        val errors = mutableListOf<FieldError>()

        assertEquals(entries, PaymentRequestRules.metadata(entries, errors))
        assertEquals(emptyList(), errors)
    }

    @Test
    fun `metadata rejects a twenty-first key, a malformed key, a long value and a value that is not a string`() {
        assertEquals(listOf("/metadata" to FieldErrorCode.TOO_MANY), metadataErrors((0..20).associate { "k$it" to "v" }))
        assertEquals(listOf("/metadata/a b" to FieldErrorCode.MALFORMED), metadataErrors(mapOf("a b" to "v")))
        assertEquals(listOf("/metadata/" + "k".repeat(41) to FieldErrorCode.MALFORMED), metadataErrors(mapOf("k".repeat(41) to "v")))
        assertEquals(listOf("/metadata/k" to FieldErrorCode.TOO_LONG), metadataErrors(mapOf("k" to "v".repeat(501))))
        assertEquals(listOf("/metadata/k" to FieldErrorCode.INVALID_TYPE), metadataErrors(mapOf("k" to 3)))
        assertEquals(listOf("/metadata/k" to FieldErrorCode.INVALID_TYPE), metadataErrors(mapOf("k" to null)))
    }

    @Test
    fun `metadata values are counted in code points`() {
        assertEquals(emptyList(), metadataErrors(mapOf("k" to "😀".repeat(500))))
    }

    @Test
    fun `timestamps must be UTC with an uppercase Z, seconds and at most milliseconds`() {
        assertEquals(Instant.parse("2026-08-17T14:32:07Z"), PaymentRequestRules.utcTimestamp("2026-08-17T14:32:07Z"))
        assertEquals(Instant.parse("2026-08-17T14:32:07.412Z"), PaymentRequestRules.utcTimestamp("2026-08-17T14:32:07.412Z"))
        listOf(
            "2026-08-17T14:32:07+00:00",
            "2026-08-17T14:32:07-05:00",
            "2026-08-17T14:32:07z",
            "2026-08-17t14:32:07Z",
            "2026-08-17T14:32Z",
            "2026-08-17T14:32:07.4123Z",
            "2026-08-17",
            "2026-02-30T00:00:00Z",
            "2026-08-17T24:00:00Z",
            " 2026-08-17T14:32:07Z",
        ).forEach { assertNull(PaymentRequestRules.utcTimestamp(it), it) }
    }

    @Test
    fun `return_url must be a hierarchical https URL with a host`() {
        listOf("https://shop.example/orders/1", "HTTPS://Shop.example/return?x=1", "https://shop.example:8443").forEach {
            assertNotNull(PaymentRequestRules.returnUrl(it, loopbackHttpAllowed = false), it)
        }
        listOf("https:opaque", "https:///path", "/orders/1", "http://example.test/return", "ftp://shop.example/", "https://", "not a url", "http://localhost/return").forEach {
            assertNull(PaymentRequestRules.returnUrl(it, loopbackHttpAllowed = false), it)
        }
    }

    @Test
    fun `http return_url is only accepted on localhost and 127_0_0_1 when the loopback exception applies`() {
        listOf("http://localhost:3000/return", "http://127.0.0.1/return", "http://LOCALHOST/return").forEach {
            assertNotNull(PaymentRequestRules.returnUrl(it, loopbackHttpAllowed = true), it)
        }
        listOf("http://example.test/return", "http://[::1]/return", "http://127.0.0.2/return", "http://localhost.example/return", "http:opaque").forEach {
            assertNull(PaymentRequestRules.returnUrl(it, loopbackHttpAllowed = true), it)
        }
    }
}
