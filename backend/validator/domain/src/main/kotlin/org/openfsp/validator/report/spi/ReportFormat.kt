package org.openfsp.validator.report.spi

import org.openfsp.validator.report.Report

interface ReportFormat {
    fun write(report: Report): String

    fun read(body: String): Report

    fun digest(body: String): String?
}
