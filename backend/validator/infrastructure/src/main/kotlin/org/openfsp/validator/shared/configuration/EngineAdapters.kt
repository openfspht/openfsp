package org.openfsp.validator.shared.configuration

import org.openfsp.validator.report.ConformanceRunner
import org.openfsp.validator.engine.spi.SchemaCheckers
import org.openfsp.validator.json.spi.JsonCodec
import org.openfsp.validator.report.spi.ReportFormat
import org.openfsp.validator.reports.json.JsonReportFormat
import org.openfsp.validator.shared.json.JacksonJsonCodec
import org.openfsp.validator.suites.NetworkntSchemaCheckers

object EngineAdapters {
    val json: JsonCodec = JacksonJsonCodec
    val schemas: SchemaCheckers = NetworkntSchemaCheckers
    val reports: ReportFormat = JsonReportFormat

    fun runner(): ConformanceRunner = ConformanceRunner(schemas, json)
}
