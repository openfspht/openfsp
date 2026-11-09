package org.openfsp.gateway.capability

import org.openfsp.gateway.GatewayProperties
import org.openfsp.gateway.auth.RequiresScope
import org.openfsp.gateway.auth.Scope
import org.openfsp.gateway.provider.ProviderDescriptor
import org.openfsp.gateway.provider.Providers
import org.openfsp.protocol.Money
import org.openfsp.protocol.NextAction
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

data class ServiceDescriptor(
    val protocolVersion: String,
    val apiBase: String,
    val capabilitiesUrl: String,
)

data class Limits(
    val minAmount: Money?,
    val maxAmount: Money?,
    val amountStep: Money?,
)

data class PaymentsCapability(
    val nextActions: List<NextAction.Type>,
    val payerRequired: Boolean,
    val returnUrlRequired: Boolean,
    val expiryGuaranteed: Boolean,
    val limits: Limits?,
    val locales: List<String>,
)

data class ProviderCapabilities(
    val id: String,
    val displayName: String,
    val capabilities: List<String>,
    val payments: PaymentsCapability,
)

data class Capabilities(
    val protocolVersion: String,
    val providers: List<ProviderCapabilities>,
)

@RestController
class CapabilitiesController(
    private val providers: Providers,
    private val properties: GatewayProperties,
) {
    @GetMapping("/.well-known/openfsp")
    fun descriptor(): ResponseEntity<ServiceDescriptor> {
        val base = properties.publicBaseUrl.resolve("/v1").toString()
        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.maxAge(CACHE))
            .body(ServiceDescriptor(PROTOCOL_VERSION, base, "$base/capabilities"))
    }

    @RequiresScope(Scope.CAPABILITIES_READ)
    @GetMapping("/v1/capabilities")
    fun capabilities(): ResponseEntity<Capabilities> {
        val emitsWebhooks = properties.webhooks.endpoints.values.any { it.enabled }
        val announced =
            providers.ids.sorted().map { id ->
                val descriptor = providers[id]!!.descriptor
                val limits = properties.providers.getValue(id).limits
                ProviderCapabilities(
                    id = id,
                    displayName = descriptor.displayName,
                    capabilities = (descriptor.capabilities + listOfNotNull(ProviderDescriptor.WEBHOOKS_EMIT.takeIf { emitsWebhooks })).sorted(),
                    payments =
                        PaymentsCapability(
                            nextActions = descriptor.nextActions.sorted(),
                            payerRequired = descriptor.payerRequired,
                            returnUrlRequired = descriptor.returnUrlRequired,
                            expiryGuaranteed = descriptor.expiryGuaranteed,
                            limits =
                                Limits(limits.minAmount?.let(Money::htg), limits.maxAmount?.let(Money::htg), limits.amountStep?.let(Money::htg))
                                    .takeIf { it.minAmount != null || it.maxAmount != null || it.amountStep != null },
                            locales = descriptor.locales.sorted(),
                        ),
                )
            }
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(CACHE)).body(Capabilities(PROTOCOL_VERSION, announced))
    }

    private companion object {
        const val PROTOCOL_VERSION = "0.1.0"
        val CACHE: Duration = Duration.ofMinutes(5)
    }
}
