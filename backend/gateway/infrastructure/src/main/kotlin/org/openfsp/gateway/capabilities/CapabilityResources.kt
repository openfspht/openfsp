package org.openfsp.gateway.capabilities

import org.openfsp.protocol.Money
import org.openfsp.protocol.NextAction

data class CapabilitiesResource(
    val protocolVersion: String,
    val providers: List<ProviderCapabilitiesResource>,
)

data class ProviderCapabilitiesResource(
    val id: String,
    val displayName: String,
    val logoUrl: String?,
    val capabilities: List<String>,
    val payments: PaymentsCapability,
)

data class PaymentsCapability(
    val nextActions: List<NextAction.Type>,
    val payerRequired: Boolean,
    val returnUrlRequired: Boolean,
    val expiryGuaranteed: Boolean,
    val limits: Limits?,
    val locales: List<String>,
)

data class Limits(
    val minAmount: Money?,
    val maxAmount: Money?,
    val amountStep: Money?,
)
