package org.openfsp.gateway.webhook

import org.openfsp.ddd.DomainService
import org.openfsp.gateway.audit.AuditKind
import org.openfsp.gateway.audit.AuditRecord
import org.openfsp.gateway.audit.spi.AuditTrail
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.common.spi.Transactions
import org.openfsp.gateway.webhook.api.ManageSubscriptions
import org.openfsp.gateway.webhook.spi.Deliveries
import org.openfsp.gateway.webhook.spi.EndpointHealth
import org.openfsp.gateway.webhook.spi.SubscriptionStore
import org.openfsp.gateway.webhook.spi.Subscriptions
import java.net.URI
import java.net.UnknownHostException

@DomainService
class SubscriptionRegistry(
    private val subscriptions: Subscriptions,
    private val store: SubscriptionStore,
    private val rules: EndpointRules,
    private val addresses: EndpointAddresses,
    private val audit: AuditTrail,
    private val calendar: BusinessCalendar,
    private val deliveries: Deliveries,
    private val health: EndpointHealth,
    private val transactions: Transactions,
) : ManageSubscriptions {
    override fun add(
        name: String,
        url: URI,
        events: Set<String>,
    ): Subscription {
        if (!NAME.matches(name)) throw OpenFspException.invalid("name", "lowercase letters, digits and hyphens, 64 characters at most")
        if (!url.isAbsolute || url.host.isNullOrBlank() || url.scheme !in allowedSchemes()) throw OpenFspException.invalid("url", "must be an absolute ${allowedSchemes().joinToString(" or ")} URL")
        if (events.isEmpty() || !Subscription.EVENT_TYPES.containsAll(events)) throw OpenFspException.invalid("events", "must name at least one known event type")
        checkAddress(url)
        val subscription = Subscription(name, url, events, enabled = true, managed = true)
        return transactions.inTransaction {
            if (subscriptions.all().any { it.name == name }) throw OpenFspException.invalid("name", "is already used by another webhook")
            store.save(subscription)
            trace(AuditKind.WEBHOOK_ADDED, name, url.toString())
            subscription
        }
    }

    override fun remove(name: String): Boolean =
        transactions.inTransaction {
            val removed = store.delete(name)
            if (removed) {
                val stopped = deliveries.stopPending(name, calendar.now())
                health.forget(name)
                trace(AuditKind.WEBHOOK_REMOVED, name, if (stopped > 0) "$stopped pending deliveries stopped" else null)
            }
            removed
        }

    private fun checkAddress(url: URI) {
        try {
            addresses.check(url)
        } catch (_: UnsafeEndpointAddressException) {
            throw OpenFspException.invalid("url", "must not resolve to a loopback, link-local, private or metadata address")
        } catch (_: UnknownHostException) {
            throw OpenFspException.invalid("url", "must name a host that resolves")
        }
    }

    private fun allowedSchemes(): List<String> = if (rules.plainHttpAllowed) listOf(HTTPS, HTTP) else listOf(HTTPS)

    private fun trace(
        kind: AuditKind,
        name: String,
        detail: String?,
    ) = audit.record(AuditRecord(calendar.now(), kind, null, name, kind.wire, null, name, detail))

    private companion object {
        const val HTTPS = "https"
        const val HTTP = "http"
        val NAME = Regex("^[a-z0-9-]{1,64}$")
    }
}
