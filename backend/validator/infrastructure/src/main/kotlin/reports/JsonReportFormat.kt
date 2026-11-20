package org.openfsp.validator.reports

import org.openfsp.validator.report.Report
import org.openfsp.validator.report.spi.ReportFormat
import org.openfsp.validator.shared.json.CanonicalJson
import org.openfsp.validator.shared.json.ValidatorJson
import org.springframework.stereotype.Component
import tools.jackson.module.kotlin.readValue

@Component
class JsonReportFormat : ReportFormat {
    override fun write(report: Report): String = ValidatorJson.reports.writeValueAsString(report)

    override fun read(body: String): Report = ValidatorJson.reports.readValue(body)

    override fun digest(body: String): String? = runCatching { CanonicalJson.sha256(ValidatorJson.reports.readTree(body)) }.getOrNull()
}
