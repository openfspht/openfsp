package org.openfsp.sdk.http

import org.openfsp.sdk.ApiException
import org.openfsp.sdk.ConfigurationException
import org.openfsp.sdk.OpenFspException
import org.openfsp.sdk.TransportException
import java.time.Duration

data class RetryPolicy
    @JvmOverloads
    constructor(
        val maxAttempts: Int = 3,
        val initialBackoff: Duration = Duration.ofMillis(500),
        val maxBackoff: Duration = Duration.ofSeconds(8),
        val maxRetryAfter: Duration = Duration.ofSeconds(30),
    ) {
        init {
            if (maxAttempts < 1) throw ConfigurationException("maxAttempts must be at least 1")
            if (listOf(initialBackoff, maxBackoff, maxRetryAfter).any { it.isNegative }) throw ConfigurationException("retry durations must not be negative")
        }

        internal fun delay(
            failure: OpenFspException,
            attempt: Int,
            random: Double,
        ): Duration? {
            if (attempt >= maxAttempts || !isResendable(failure)) return null
            val ceiling = minOf(initialBackoff.toMillis() * (1L shl (attempt - 1).coerceIn(0, MAX_SHIFT)), maxBackoff.toMillis())
            val backoff = Duration.ofMillis((ceiling / 2 + random * ceiling / 2).toLong())
            val retryAfter = (failure as? ApiException)?.retryAfter ?: return backoff
            return if (retryAfter > maxRetryAfter) null else maxOf(backoff, retryAfter)
        }

        private fun isResendable(failure: OpenFspException): Boolean =
            failure is TransportException || (failure is ApiException && failure.retryable && (failure.status >= SERVER_ERROR || failure.code in RESENDABLE_CLIENT_ERRORS))

        companion object {
            private const val MAX_SHIFT = 20
            private const val SERVER_ERROR = 500
            private val RESENDABLE_CLIENT_ERRORS = setOf("rate-limited", "idempotency-request-in-progress")

            @JvmField
            val DEFAULT = RetryPolicy()

            @JvmField
            val NONE = RetryPolicy(maxAttempts = 1)
        }
    }
