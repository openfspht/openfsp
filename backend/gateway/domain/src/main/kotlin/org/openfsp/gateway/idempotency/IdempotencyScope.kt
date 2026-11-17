package org.openfsp.gateway.idempotency

data class IdempotencyScope(
    val principal: String,
    val operation: String,
    val key: String,
)

class RecordedResponse(
    val status: Int,
    val body: ByteArray,
) {
    override fun equals(other: Any?): Boolean = other is RecordedResponse && other.status == status && other.body.contentEquals(body)

    override fun hashCode(): Int = 31 * status + body.contentHashCode()
}

data class IdempotencyRecord(
    val fingerprint: String,
    val response: RecordedResponse?,
)
