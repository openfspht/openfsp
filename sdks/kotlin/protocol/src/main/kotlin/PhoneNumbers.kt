package org.openfsp.protocol

object PhoneNumbers {
    private val E164 = Regex("^\\+[1-9]\\d{1,14}$")

    fun isValid(value: String): Boolean = E164.matches(value)
}
