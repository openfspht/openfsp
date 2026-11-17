package org.openfsp.validator.report.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.validator.report.Report
import org.openfsp.validator.report.spi.ReportFormat
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.ConcurrentHashMap

@Stub
class RememberedReportFormat : ReportFormat {
    private val written = ConcurrentHashMap<String, Report>()

    override fun write(report: Report): String = report.toString().also { written[it] = report }

    override fun read(body: String): Report = written[body] ?: throw IllegalArgumentException("the report was not written by this format")

    override fun digest(body: String): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.toByteArray(Charsets.UTF_8)))
}
