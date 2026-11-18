package org.openfsp.gateway.conformance

import org.openfsp.gateway.provider.ProviderDescriptor
import org.openfsp.protocol.FailureReason
import org.openfsp.protocol.PaymentStatus

data class ScriptedOutcome(
    val status: PaymentStatus,
    val failureReason: FailureReason? = null,
) {
    init {
        require(status.isTerminal) { "a scripted outcome is terminal" }
        require((status == PaymentStatus.FAILED) == (failureReason != null)) { "a failure reason goes with failed only" }
    }

    companion object {
        val SUCCEEDED = ScriptedOutcome(PaymentStatus.SUCCEEDED)
        val EXPIRED = ScriptedOutcome(PaymentStatus.EXPIRED)
    }
}

enum class Notifications {
    NONE,
    ONCE,
    LATE,
    REPEATED,
}

enum class Channel {
    NOTIFICATION,
    LOOKUP,
}

enum class Scenario(
    val outcome: ScriptedOutcome?,
    val notifications: Notifications = Notifications.ONCE,
    val channel: Channel? = null,
) {
    SUCCESS(ScriptedOutcome.SUCCEEDED),
    DECLINE(ScriptedOutcome(PaymentStatus.FAILED, FailureReason.DECLINED)),
    INSUFFICIENT(ScriptedOutcome(PaymentStatus.FAILED, FailureReason.INSUFFICIENT_FUNDS)),
    TIMEOUT(ScriptedOutcome.SUCCEEDED),
    PENDING_FOREVER(null),
    EXPIRE(null),
    NO_CALLBACK(ScriptedOutcome.SUCCEEDED, Notifications.NONE),
    LATE_CALLBACK(ScriptedOutcome.SUCCEEDED, Notifications.LATE, Channel.NOTIFICATION),
    DUPLICATE_CALLBACK(ScriptedOutcome.SUCCEEDED, Notifications.REPEATED, Channel.NOTIFICATION),
    UNKNOWN_STATUS(ScriptedOutcome.SUCCEEDED, Notifications.NONE, Channel.LOOKUP),
    PROVIDER_ERROR(null, Notifications.NONE),
    ;

    fun isPlayableOn(descriptor: ProviderDescriptor): Boolean =
        when (channel) {
            Channel.NOTIFICATION -> descriptor.signedCallbacks || descriptor.paymentUrl
            Channel.LOOKUP -> descriptor.hasLookup
            null -> true
        }

    companion object {
        fun named(name: String): Scenario? = entries.firstOrNull { it.name == name }
    }
}
