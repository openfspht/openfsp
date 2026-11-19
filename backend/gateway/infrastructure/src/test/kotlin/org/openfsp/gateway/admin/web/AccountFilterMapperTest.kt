package org.openfsp.gateway.admin.web

import org.openfsp.gateway.account.AccountFilter
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountFilterMapperTest {
    private val mapper = AccountFilterMapper()

    @Test
    fun `a named account narrows the filter and a blank one covers every account`() {
        assertEquals(AccountFilter.Only("acct_1"), mapper.toFilter(" acct_1 "))
        assertEquals(AccountFilter.Every, mapper.toFilter(null))
        assertEquals(AccountFilter.Every, mapper.toFilter("  "))
    }
}
