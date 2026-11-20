package org.openfsp.ui

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("openfsp.ui")
data class UiProperties(
    val sentry: Sentry = Sentry(),
) {
    data class Sentry(
        val dsn: String = "",
        val environment: String = "",
        val release: String = "",
        val tracesSampleRate: String = "",
    )
}
