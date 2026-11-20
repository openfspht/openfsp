package org.openfsp.sdk.model

import org.openfsp.protocol.Locale

typealias NextActionType = org.openfsp.protocol.NextAction.Type

data class AmountLimits(
    val min: Money?,
    val max: Money?,
    val step: Money?,
)

data class Provider(
    val id: String,
    val displayName: String,
    val logoUrl: String?,
    val capabilities: List<String>,
    val nextActions: List<NextActionType>,
    val payerRequired: Boolean,
    val returnUrlRequired: Boolean,
    val expiryGuaranteed: Boolean,
    val limits: AmountLimits,
    val locales: List<Locale>,
) {
    fun supports(capability: String): Boolean = capability in capabilities

    fun accepts(amount: Money): Boolean {
        val (min, max, step) = limits
        if (min != null && amount < min) return false
        if (max != null && amount > max) return false
        return step == null || step.centimes == 0L || amount.centimes % step.centimes == 0L
    }
}
