package org.openfsp.gateway.webhook.spi

import org.openfsp.gateway.webhook.AttemptActivity
import org.openfsp.gateway.webhook.EndpointCondition
import org.openfsp.gateway.webhook.RecordedEvent

interface DeliveryQueries {
    fun retryingCount(): Long

    fun disabledEndpointCount(): Long

    fun pendingCount(endpoint: String): Long

    fun conditionOf(endpoint: String): EndpointCondition?

    fun eventsOf(paymentId: String): List<RecordedEvent>

    fun latestAttempts(limit: Int): List<AttemptActivity>

    fun countAttemptsUpTo(cap: Int): Long
}
