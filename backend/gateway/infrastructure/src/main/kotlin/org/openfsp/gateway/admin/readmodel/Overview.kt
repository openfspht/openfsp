package org.openfsp.gateway.admin.readmodel

import org.openfsp.gateway.apikeys.persistence.ApiKeyJpaRepository
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.payments.persistence.PaymentJpaRepository
import org.openfsp.gateway.shared.configuration.GatewayProperties
import org.openfsp.gateway.webhooks.persistence.DeliveryJpaRepository
import org.openfsp.gateway.webhooks.persistence.EndpointStateJpaRepository
import org.openfsp.protocol.Money
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

data class OverviewStatusTotal(
    val status: String,
    val count: Long,
    val amount: Money,
)

data class OverviewResource(
    val environment: String,
    val businessDate: LocalDate,
    val payments: List<OverviewStatusTotal>,
    val retryingDeliveries: Long,
    val disabledEndpoints: Long,
    val activeKeys: Long,
)

@Component
class Overview(
    private val payments: PaymentJpaRepository,
    private val deliveries: DeliveryJpaRepository,
    private val endpoints: EndpointStateJpaRepository,
    private val keys: ApiKeyJpaRepository,
    private val calendar: BusinessCalendar,
    private val properties: GatewayProperties,
) {
    @Transactional(readOnly = true)
    fun today(): OverviewResource {
        val day = calendar.today()
        return OverviewResource(
            environment = properties.environmentName,
            businessDate = day,
            payments = payments.countByStatusSince(calendar.startOf(day)).map { OverviewStatusTotal(it.status, it.total, Money.htg(it.amount)) },
            retryingDeliveries = deliveries.countByStatusAndAttemptsGreaterThan(PENDING, 0),
            disabledEndpoints = endpoints.countByDisabledAtIsNotNull(),
            activeKeys = keys.countActive(calendar.now()),
        )
    }

    private companion object {
        const val PENDING = "pending"
    }
}
