package org.openfsp.protocol

object JsonPointer {
    fun of(vararg tokens: String): String = tokens.joinToString("") { "/" + it.replace("~", "~0").replace("/", "~1") }
}
