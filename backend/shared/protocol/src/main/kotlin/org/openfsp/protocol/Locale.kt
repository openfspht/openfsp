package org.openfsp.protocol

import com.fasterxml.jackson.annotation.JsonValue

enum class Locale(
    @get:JsonValue val wire: String,
) {
    FR("fr"),
    HT("ht"),
    EN("en"),
    ;

    companion object {
        fun fromWire(value: String): Locale? = entries.firstOrNull { it.wire == value }
    }
}
