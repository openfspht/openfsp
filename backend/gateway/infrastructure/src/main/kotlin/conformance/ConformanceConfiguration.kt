package org.openfsp.gateway.conformance

import org.openfsp.gateway.conformance.api.PrepareConformance
import org.openfsp.gateway.conformance.spi.CallbackDelivery
import org.openfsp.gateway.conformance.spi.Deferrals
import org.openfsp.gateway.payment.api.SettlePayment
import org.openfsp.gateway.provider.CallbackRequest
import org.openfsp.gateway.provider.spi.Providers
import org.openfsp.gateway.shared.configuration.GatewayProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.net.URI
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

@Configuration(proxyBeanMethods = false)
class ConformanceConfiguration(
    private val properties: ConformanceProperties,
    private val gateway: GatewayProperties,
) {
    init {
        check(!properties.enabled || gateway.environment == GatewayProperties.Environment.TEST) {
            "openfsp.conformance.enabled issues test keys on request and is refused outside the test environment"
        }
    }

    @Bean
    fun conformanceSettings() =
        ConformanceSettings(
            maxActive = properties.maxActive,
            controlEndpoint = URI(gateway.publicBaseUrl.toString().trimEnd('/') + ConformanceControlController.PATH),
            settleAfter = properties.settleAfter,
            lateCallbackAfter = properties.lateCallbackAfter,
        )

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "openfsp.conformance", name = ["enabled"], havingValue = "true")
    fun scriptedProviders(
        @Qualifier("providers") configured: Providers,
        scripts: PaymentScripts,
    ): Providers = ScriptedProviders(configured, scripts)
}

@Component
class ConformanceSweep(
    private val conformance: PrepareConformance,
    private val properties: ConformanceProperties,
) {
    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    fun run() {
        if (properties.enabled) conformance.sweep()
    }
}

@Component
class WorkerDeferrals(
    private val workers: ExecutorService,
) : Deferrals {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun after(
        delay: Duration,
        action: () -> Unit,
    ) = workers.execute {
        try {
            Thread.sleep(delay)
            action()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (exception: RuntimeException) {
            log.warn("A scripted payment step failed: {}", exception.message)
        }
    }
}

// The settlement is resolved lazily: it depends on the providers, which the scripted payment service decorates.
@Component
class InProcessCallbackDelivery(
    private val settlement: ObjectProvider<SettlePayment>,
) : CallbackDelivery {
    override fun deliver(
        provider: String,
        token: String?,
        request: CallbackRequest,
    ) {
        if (token == null) settlement.getObject().onSignedCallback(provider, request) else settlement.getObject().onPaymentUrlCallback(provider, token, request)
    }
}
