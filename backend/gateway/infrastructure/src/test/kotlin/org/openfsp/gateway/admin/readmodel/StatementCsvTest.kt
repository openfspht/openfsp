package org.openfsp.gateway.admin.readmodel

import org.openfsp.gateway.provider.FeeBearer
import org.openfsp.gateway.provider.ProviderFee
import org.openfsp.gateway.statement.StatementEntry
import org.openfsp.protocol.Money
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class StatementCsvTest {
    private fun entry(
        reference: String,
        amount: Long = 125_000,
        fee: ProviderFee? = null,
    ) = StatementEntry("natcash", LocalDate.parse("2026-09-28"), Instant.parse("2026-09-28T14:33:02Z"), Money.htg(amount), fee, "pay_1", reference)

    private fun lines(vararg entries: StatementEntry): List<String> {
        val output = ByteArrayOutputStream()
        StatementCsv().write(entries.asSequence(), output)
        return output.toString(Charsets.UTF_8).removeSuffix("\r\n").split("\r\n")
    }

    @Test
    fun `a row carries decimal gourdes and leaves an unknown fee empty, a zero fee being a known one`() {
        assertEquals(
            listOf(
                "business_date,booked_at,provider,payment,reference,amount,currency,fee_amount,fee_bearer",
                "2026-09-28,2026-09-28T14:33:02Z,natcash,pay_1,INV-184,1250.00,HTG,,",
                "2026-09-28,2026-09-28T14:33:02Z,natcash,pay_1,INV-185,0.05,HTG,12.50,merchant",
                "2026-09-28,2026-09-28T14:33:02Z,natcash,pay_1,INV-186,1250.00,HTG,0.00,payer",
            ),
            lines(
                entry("INV-184"),
                entry("INV-185", amount = 5, fee = ProviderFee(Money.htg(1250), FeeBearer.MERCHANT)),
                entry("INV-186", fee = ProviderFee(Money.htg(0), FeeBearer.PAYER)),
            ),
        )
    }

    @Test
    fun `an empty period is a header alone`() {
        assertEquals(listOf("business_date,booked_at,provider,payment,reference,amount,currency,fee_amount,fee_bearer"), lines())
    }

    @Test
    fun `a reference is quoted per RFC 4180 and cannot start a spreadsheet formula`() {
        val references = lines(entry("Order \"A\", line 2"), entry("=1+1"), entry("+1"), entry("-1"), entry("@SUM(A1)"), entry("a\nb"), entry("A=1")).drop(1)

        assertEquals(
            listOf("\"Order \"\"A\"\", line 2\"", "'=1+1", "'+1", "'-1", "'@SUM(A1)", "\"a\nb\"", "A=1"),
            references.map { it.removePrefix("2026-09-28,2026-09-28T14:33:02Z,natcash,pay_1,").removeSuffix(",1250.00,HTG,,") },
        )
    }
}
