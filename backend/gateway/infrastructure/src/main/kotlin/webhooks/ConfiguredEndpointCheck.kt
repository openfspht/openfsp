package org.openfsp.gateway.webhooks

import org.openfsp.gateway.shared.configuration.GatewayProperties
import org.openfsp.gateway.webhook.EndpointAddresses
import org.openfsp.gateway.webhook.UnsafeEndpointAddressException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.UnknownHostException

@Component
class ConfiguredEndpointCheck(
    properties: GatewayProperties,
    webhooks: WebhookProperties,
    addresses: EndpointAddresses,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    init {
        webhooks.endpoints.forEach { (name, endpoint) ->
            check(endpoint.url.scheme == "https" || properties.developmentMode) { "webhook endpoint $name must use https" }
            try {
                addresses.check(endpoint.url)
            } catch (exception: UnsafeEndpointAddressException) {
                throw IllegalStateException("webhook endpoint $name: ${exception.message}")
            } catch (_: UnknownHostException) {
                log.warn("Webhook endpoint {} does not resolve yet: its address is checked again at each delivery", name)
            }
        }
    }
}
