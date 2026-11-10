package org.openfsp.gateway.controllers.capabilities

import org.openfsp.gateway.auth.Scope
import org.openfsp.gateway.capability.ProviderCapabilities
import org.openfsp.gateway.capability.api.DescribeCapabilities
import org.openfsp.gateway.configuration.GatewayProperties
import org.openfsp.gateway.security.RequiresScope
import org.openfsp.protocol.Money
import org.openfsp.protocol.NextAction
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
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

data class ProviderCapabilitiesResource(
    val id: String,
    val displayName: String,
    val capabilities: List<String>,
    val payments: PaymentsCapability,
)

data class Capabilities(
    val protocolVersion: String,
    val providers: List<ProviderCapabilitiesResource>,
)

@Component
class CapabilitiesMapper {
    fun toResource(provider: ProviderCapabilities): ProviderCapabilitiesResource =
        ProviderCapabilitiesResource(
            id = provider.id,
            displayName = provider.descriptor.displayName,
            capabilities = provider.capabilities,
            payments =
                PaymentsCapability(
                    nextActions = provider.descriptor.nextActions.sorted(),
                    payerRequired = provider.descriptor.payerRequired,
                    returnUrlRequired = provider.descriptor.returnUrlRequired,
                    expiryGuaranteed = provider.descriptor.expiryGuaranteed,
                    limits = provider.limits.takeIf { it.isKnown }?.let { Limits(it.minAmount, it.maxAmount, it.amountStep) },
                    locales = provider.descriptor.locales.sorted(),
                ),
        )
}

@RestController
class CapabilitiesController(
    private val capabilities: DescribeCapabilities,
    private val mapper: CapabilitiesMapper,
    private val properties: GatewayProperties,
) {
    @GetMapping("/.well-known/openfsp")
    fun descriptor(): ResponseEntity<ServiceDescriptor> {
        val base = properties.publicBaseUrl.resolve("/v1").toString()
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(CACHE)).body(ServiceDescriptor(PROTOCOL_VERSION, base, "$base/capabilities"))
    }

    @RequiresScope(Scope.CAPABILITIES_READ)
    @GetMapping("/v1/capabilities")
    fun capabilities(): ResponseEntity<Capabilities> =
        ResponseEntity
            .ok()
            .cacheControl(CacheControl.maxAge(CACHE))
            .body(Capabilities(PROTOCOL_VERSION, capabilities.describe().map(mapper::toResource)))

    private companion object {
        const val PROTOCOL_VERSION = "0.1.0"
        val CACHE: Duration = Duration.ofMinutes(5)
    }
}
