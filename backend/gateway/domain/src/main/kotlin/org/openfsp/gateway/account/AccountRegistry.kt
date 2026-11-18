package org.openfsp.gateway.account

import org.openfsp.ddd.DomainService
import org.openfsp.gateway.account.api.ManageAccounts
import org.openfsp.gateway.account.spi.MerchantAccounts
import org.openfsp.gateway.audit.AuditKind
import org.openfsp.gateway.audit.AuditRecord
import org.openfsp.gateway.audit.spi.AuditTrail
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.protocol.FieldErrorCode
import org.openfsp.protocol.ResourceIds
import java.time.Instant

@DomainService
class AccountRegistry(
    private val accounts: MerchantAccounts,
    private val audit: AuditTrail,
    private val calendar: BusinessCalendar,
) : ManageAccounts {
    override fun open(
        name: String,
        expiresAt: Instant?,
    ): MerchantAccount {
        if (!MerchantAccount.isValidName(name)) throw OpenFspException.invalid("name", "must be 1 to 63 lowercase letters, digits or hyphens", FieldErrorCode.MALFORMED)
        val account = MerchantAccount(ResourceIds.generate("acct"), name, calendar.now(), expiresAt)
        if (!accounts.insert(account)) throw OpenFspException.invalid("name", "is already used by another merchant account", FieldErrorCode.NOT_ALLOWED)
        audit.record(AuditRecord(account.createdAt, AuditKind.ACCOUNT_OPENED, null, null, AuditKind.ACCOUNT_OPENED.wire, null, account.id, account.name))
        return account
    }

    override fun find(id: String): MerchantAccount? = accounts.find(id)

    override fun all(): List<MerchantAccount> = accounts.all()
}
