package org.openfsp.gateway.console

import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.payment.PaymentSummary
import org.openfsp.gateway.payment.RecordedConflict
import org.openfsp.gateway.payment.RecordedTransition
import org.openfsp.gateway.payment.StatusTotal
import org.openfsp.gateway.webhook.RecordedEvent
import java.time.Instant
import java.time.LocalDate

data class ConsoleOverview(
    val environment: String,
    val businessDate: LocalDate,
    val payments: List<StatusTotal>,
    val retryingDeliveries: Long,
    val disabledEndpoints: Long,
    val activeKeys: Long,
)

data class PaymentInspection(
    val payment: Payment,
    val automaticSources: Boolean,
    val indeterminate: List<String>,
    val transitions: List<RecordedTransition>,
    val conflicts: List<RecordedConflict>,
    val events: List<RecordedEvent>,
)

data class EndpointView(
    val account: String,
    val name: String,
    val url: String,
    val events: List<String>,
    val enabled: Boolean,
    val managed: Boolean,
    val pending: Long,
    val disabledAt: Instant?,
    val reason: String?,
)

enum class ActivityKind(
    val wire: String,
) {
    TRANSITION("transition"),
    CONFLICT("conflict"),
    DELIVERY("delivery"),
    KEY("key"),
    ADMIN("admin"),
    ;

    companion object {
        fun fromWire(wire: String): ActivityKind? = entries.firstOrNull { it.wire == wire }
    }
}

data class Activity(
    val id: String,
    val at: Instant,
    val kind: ActivityKind,
    val source: String?,
    val payment: PaymentSummary?,
    val fromStatus: String? = null,
    val toStatus: String? = null,
    val eventType: String? = null,
    val endpoint: String? = null,
    val statusCode: Int? = null,
    val attempt: Int? = null,
    val durationMs: Int? = null,
    val subject: String? = null,
    val result: String? = null,
)
