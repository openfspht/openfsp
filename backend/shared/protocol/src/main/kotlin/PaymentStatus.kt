package org.openfsp.protocol

import com.fasterxml.jackson.annotation.JsonValue

enum class PaymentStatus(
    @get:JsonValue val wire: String,
    val isTerminal: Boolean,
) {
    PENDING("pending", isTerminal = false),
    SUCCEEDED("succeeded", isTerminal = true),
    FAILED("failed", isTerminal = true),
    EXPIRED("expired", isTerminal = true),
    CANCELED("canceled", isTerminal = true),
    ;

    fun canTransitionTo(next: PaymentStatus): Boolean = this == PENDING && next.isTerminal

    companion object {
        fun fromWire(value: String): PaymentStatus? = entries.firstOrNull { it.wire == value }
    }
}

enum class FailureReason(
    @get:JsonValue val wire: String,
) {
    DECLINED("declined"),
    INSUFFICIENT_FUNDS("insufficient_funds"),
    PAYER_CANCELED("payer_canceled"),
    PAYER_UNREACHABLE("payer_unreachable"),
    LIMIT_EXCEEDED("limit_exceeded"),
    REJECTED_BY_PROVIDER("rejected_by_provider"),
    PROVIDER_ERROR("provider_error"),
    UNSPECIFIED("unspecified"),
    ;

    companion object {
        fun fromWire(value: String): FailureReason = entries.firstOrNull { it.wire == value } ?: UNSPECIFIED
    }
}
