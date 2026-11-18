package org.openfsp.gateway.idempotency

import java.time.Instant

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

data class RequestFingerprint(
    val value: String,
    val legacy: String? = null,
) {
    fun matches(record: IdempotencyRecord): Boolean = record.fingerprint == if (record.fingerprintVersion == VERSION) value else legacy

    companion object {
        const val VERSION = 2
    }
}

data class IdempotencyRecord(
    val fingerprint: String,
    val response: RecordedResponse?,
    val createdAt: Instant,
    val generation: Long,
    val fingerprintVersion: Int = RequestFingerprint.VERSION,
) {
    companion object {
        const val FIRST_GENERATION = 1L
    }
}
