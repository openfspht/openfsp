package org.openfsp.gateway.admin.web

import org.openfsp.protocol.Money
import java.time.Instant

data class ActivityPayment(
    val id: String,
    val reference: String,
    val provider: String,
    val amount: Money,
    val status: String,
)

data class ActivityItem(
    val id: String,
    val at: Instant,
    val kind: String,
    val source: String?,
    val payment: ActivityPayment?,
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
