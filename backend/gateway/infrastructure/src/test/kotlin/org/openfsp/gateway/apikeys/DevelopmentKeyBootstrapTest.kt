package org.openfsp.gateway.apikeys

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.AfterEach
import org.openfsp.gateway.audit.spi.stubs.InMemoryAuditTrail
import org.openfsp.gateway.auth.ApiKeyService
import org.openfsp.gateway.auth.GatewayEnvironment
import org.openfsp.gateway.auth.spi.stubs.InMemoryApiKeys
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.spi.stubs.ImmediateTransactions
import org.openfsp.gateway.shared.configuration.GatewayProperties
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DevelopmentKeyBootstrapTest {
    private val keys = InMemoryApiKeys()
    private val service = ApiKeyService(keys, InMemoryAuditTrail(), BusinessCalendar(Clock.systemUTC()), GatewayEnvironment("test"), ImmediateTransactions())
    private val logger = LoggerFactory.getLogger(DevelopmentKeyBootstrap::class.java) as Logger
    private val logs = ListAppender<ILoggingEvent>().also { it.start() }.also(logger::addAppender)

    @AfterEach
    fun detach() {
        logger.detachAppender(logs)
    }

    private fun bootstrap(
        key: String?,
        developmentMode: Boolean = true,
    ) = DevelopmentKeyBootstrap(service, GatewayProperties(GatewayProperties.Environment.TEST, developmentMode, URI("http://localhost:8080"), "key", bootstrapKey = key)).bootstrap()

    private fun logged(): String = logs.list.joinToString("\n") { it.formattedMessage }

    @Test
    fun `a revoked bootstrap key is not registered again at restart`() {
        val secret = "ofsp_test_" + "B".repeat(40)
        bootstrap(secret)
        val key = assertNotNull(service.authenticate(secret))
        service.revoke(key.keyId)
        bootstrap(secret)
        assertNull(service.authenticate(secret))
    }

    @Test
    fun `a bootstrap key is registered in the test environment outside development mode`() {
        val secret = "ofsp_test_" + "D".repeat(40)
        bootstrap(secret, developmentMode = false)
        assertNotNull(service.authenticate(secret))
    }

    @Test
    fun `no key is issued without a bootstrap key and the way to get one is logged once`() {
        bootstrap(null)
        assertTrue(service.isEmpty())
        assertEquals(1, logs.list.size, logged())
        assertTrue("openfsp.bootstrap-key" in logged() && "admin console" in logged(), logged())
    }

    @Test
    fun `only the key prefix is logged`() {
        val secret = "ofsp_test_" + "C".repeat(40)
        bootstrap(secret)
        assertTrue(logs.list.isNotEmpty(), logged())
        assertTrue("C".repeat(12) in logged(), logged())
        assertTrue(!Regex("ofsp_test_[A-Za-z0-9]{34,}").containsMatchIn(logged()), logged())
    }
}
