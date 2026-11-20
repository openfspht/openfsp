package org.openfsp.validator.json.spi

import org.openfsp.validator.json.Json

interface JsonCodec {
    fun parse(bytes: ByteArray): Json?

    fun write(value: Json): ByteArray

    fun text(value: Json): String = String(write(value), Charsets.UTF_8)
}
