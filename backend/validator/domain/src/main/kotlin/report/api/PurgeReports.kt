package org.openfsp.validator.report.api

interface PurgeReports {
    fun purgeExpired(): Int
}
