package org.openfsp.server

object References {
    private val pattern = Regex("^[A-Za-z0-9._:/-]{1,128}$")

    fun isValid(value: String): Boolean = pattern.matches(value)
}
