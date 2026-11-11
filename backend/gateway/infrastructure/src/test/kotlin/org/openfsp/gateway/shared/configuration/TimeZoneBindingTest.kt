package org.openfsp.gateway.shared.configuration

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME as SYSTEM_ENVIRONMENT
import org.springframework.core.env.SystemEnvironmentPropertySource
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class TimeZoneBindingTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(GatewayProperties::class)
    class Properties

    private val required =
        mapOf<String, Any>(
            "OPENFSP_ENVIRONMENT" to "test",
            "OPENFSP_DEVELOPMENTMODE" to "true",
            "OPENFSP_PUBLICBASEURL" to "http://127.0.0.1:8080",
            "OPENFSP_ORDERIDDERIVATIONKEY" to "ZGV2ZWxvcG1lbnQtb25seS1kZXJpdmF0aW9uLWtleS0wMQ==",
        )

    private fun zoneWith(variables: Map<String, Any>): ZoneId {
        var zone: ZoneId? = null
        ApplicationContextRunner()
            .withInitializer { it.environment.propertySources.replace(SYSTEM_ENVIRONMENT, SystemEnvironmentPropertySource(SYSTEM_ENVIRONMENT, required + variables)) }
            .withUserConfiguration(Properties::class.java)
            .run { zone = it.getBean(GatewayProperties::class.java).timeZone }
        return zone!!
    }

    @Test
    fun `OPENFSP_TIMEZONE sets the business time zone`() {
        assertEquals(ZoneId.of("Europe/Paris"), zoneWith(mapOf("OPENFSP_TIMEZONE" to "Europe/Paris")))
    }

    @Test
    fun `the business time zone defaults to Haiti`() {
        assertEquals(ZoneId.of("America/Port-au-Prince"), zoneWith(emptyMap()))
    }
}
