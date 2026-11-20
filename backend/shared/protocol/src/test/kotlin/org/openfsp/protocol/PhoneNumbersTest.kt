package org.openfsp.protocol

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhoneNumbersTest {
    @Test
    fun `a phone number is in E164 format`() {
        listOf("+50934567890", "+12", "+123456789012345").forEach { assertTrue(PhoneNumbers.isValid(it), it) }
        listOf("50934567890", "+0934567890", "+1", "+1234567890123456", "+509 3456 7890", "+5093456789a").forEach { assertFalse(PhoneNumbers.isValid(it), it) }
    }
}
