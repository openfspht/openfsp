package org.openfsp.gateway.account

import org.junit.jupiter.api.Test
import org.openfsp.gateway.TestClock
import org.openfsp.gateway.account.spi.stubs.InMemoryMerchantAccounts
import org.openfsp.gateway.audit.AuditKind
import org.openfsp.gateway.audit.spi.stubs.InMemoryAuditTrail
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.OpenFspException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountRegistryTest {
    private val clock = TestClock()
    private val audit = InMemoryAuditTrail()
    private val registry = AccountRegistry(InMemoryMerchantAccounts(), audit, BusinessCalendar(clock))

    @Test
    fun `an opened account is found, listed and audited`() {
        val shop = registry.open("shop")
        assertTrue(shop.id.startsWith("acct_"))
        assertEquals(shop, registry.find(shop.id))
        assertTrue(shop in registry.all())
        assertEquals(AuditKind.ACCOUNT_OPENED, audit.records.single().kind)
    }

    @Test
    fun `a name is unique and follows its syntax`() {
        registry.open("shop")
        listOf("shop", "Shop", "", "-shop", "shop_1", "a".repeat(64)).forEach { name ->
            assertEquals("/name", assertFailsWith<OpenFspException>(name) { registry.open(name) }.errors.single().field)
        }
    }

    @Test
    fun `a temporary account stops being active at its expiry`() {
        val temporary = registry.open("conformance", clock.instant().plusSeconds(60))
        assertTrue(temporary.isActiveAt(clock.instant()))
        assertFalse(temporary.isActiveAt(clock.instant().plusSeconds(60)))
    }
}
