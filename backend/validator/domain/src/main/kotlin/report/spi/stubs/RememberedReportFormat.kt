package org.openfsp.validator.report.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.server.Digests
import org.openfsp.validator.report.Report
import org.openfsp.validator.report.spi.ReportFormat
import java.util.concurrent.ConcurrentHashMap

@Stub
class RememberedReportFormat : ReportFormat {
    private val written = ConcurrentHashMap<String, Report>()

    override fun write(report: Report): String = report.toString().also { written[it] = report }

    override fun read(body: String): Report = written[body] ?: throw IllegalArgumentException("the report was not written by this format")

    override fun digest(body: String): String = Digests.sha256Hex(body)
}
