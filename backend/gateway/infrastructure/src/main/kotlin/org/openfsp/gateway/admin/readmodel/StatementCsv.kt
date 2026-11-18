package org.openfsp.gateway.admin.readmodel

import org.openfsp.gateway.statement.StatementEntry
import org.openfsp.protocol.Money
import org.springframework.stereotype.Component
import java.io.OutputStream
import java.math.BigDecimal

@Component
class StatementCsv {
    fun write(
        entries: Sequence<StatementEntry>,
        output: OutputStream,
    ) {
        val writer = output.bufferedWriter(Charsets.UTF_8)
        writer.write(HEADER.joinToString(SEPARATOR) + LINE_END)
        entries.forEach { writer.write(rowOf(it).joinToString(SEPARATOR, transform = ::quoted) + LINE_END) }
        writer.flush()
    }

    private fun rowOf(entry: StatementEntry): List<String> =
        listOf(
            entry.businessDate.toString(),
            entry.bookedAt.toString(),
            entry.provider,
            entry.paymentId,
            inert(entry.reference),
            decimal(entry.amount),
            entry.amount.currency.name,
            entry.fee?.amount?.let(::decimal).orEmpty(),
            entry.fee?.bearer?.wire.orEmpty(),
        )

    private fun decimal(money: Money): String = BigDecimal.valueOf(money.amount, CENT_DIGITS).toPlainString()

    private fun inert(value: String): String = if (value.firstOrNull() in FORMULA_TRIGGERS) "'$value" else value

    private fun quoted(field: String): String = if (field.any { it in SPECIAL }) "\"${field.replace("\"", "\"\"")}\"" else field

    private companion object {
        val HEADER = listOf("business_date", "booked_at", "provider", "payment", "reference", "amount", "currency", "fee_amount", "fee_bearer")
        const val SEPARATOR = ","
        const val LINE_END = "\r\n"
        const val CENT_DIGITS = 2
        val FORMULA_TRIGGERS = setOf('=', '+', '-', '@')
        val SPECIAL = setOf(',', '"', '\r', '\n')
    }
}
