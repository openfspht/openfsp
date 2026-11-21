package org.openfsp.validator.attestation

import java.security.SecureRandom

object ShortCodes {
    private const val ALPHABET = "23456789abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val LENGTH = 8
    private val CODE = Regex("^[0-9a-zA-Z]{8,16}$")
    private val random = SecureRandom()

    fun next(): String = (1..LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    fun isCode(value: String): Boolean = CODE.matches(value)
}
