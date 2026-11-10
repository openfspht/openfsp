package org.openfsp.gateway.admin.overview

import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.payments.persistence.PaymentJpaRepository
import org.openfsp.gateway.persistence.auth.ApiKeyJpaRepository
import org.openfsp.gateway.persistence.webhook.DeliveryJpaRepository
import org.openfsp.gateway.persistence.webhook.EndpointStateJpaRepository
import org.openfsp.gateway.shared.configuration.GatewayProperties
import org.openfsp.protocol.Money
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

data class StatusTotal(
    val status: String,
    val count: Long,
    val amount: Money,
)

data class OverviewResource(
    val environment: String,
    val businessDate: LocalDate,
    val payments: List<StatusTotal>,
    val retryingDeliveries: Long,
    val disabledEndpoints: Long,
    val activeKeys: Long,
)

@RestController
@RequestMapping("/admin/overview")
class AdminOverviewController(
    private val payments: PaymentJpaRepository,
    private val deliveries: DeliveryJpaRepository,
    private val endpoints: EndpointStateJpaRepository,
    private val keys: ApiKeyJpaRepository,
    private val calendar: BusinessCalendar,
    private val properties: GatewayProperties,
) {
    @GetMapping
    @Transactional(readOnly = true)
    fun today(): OverviewResource {
        val day = calendar.today()
        return OverviewResource(
            environment = properties.environmentName,
            businessDate = day,
            payments = payments.countByStatusSince(calendar.startOf(day)).map { StatusTotal(it.status, it.total, Money.htg(it.amount)) },
            retryingDeliveries = deliveries.countByStatusAndAttemptsGreaterThan(PENDING, 0),
            disabledEndpoints = endpoints.countByDisabledAtIsNotNull(),
            activeKeys = keys.countActive(calendar.now()),
        )
    }

    private companion object {
        const val PENDING = "pending"
    }
}
