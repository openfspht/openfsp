package org.openfsp.sdk

import org.openfsp.sdk.http.RetryPolicy
import java.net.URI
import java.net.URISyntaxException
import java.time.Duration

class OpenFspConfig
    @JvmOverloads
    constructor(
        baseUrl: String,
        apiKey: String,
        val timeout: Duration = DEFAULT_TIMEOUT,
        val retryPolicy: RetryPolicy = RetryPolicy.DEFAULT,
        webhookUrl: String? = null,
        /** Sends the API key in clear text to a non-loopback host: development networks only. */
        allowPlainHttp: Boolean = false,
        val recoverLostAnswers: Boolean = true,
        val pendingOnUnknownStatus: Boolean = false,
    ) {
        val baseUrl: URI = parseBaseUrl(baseUrl, allowPlainHttp)
        val webhookUrl: String? = webhookUrl?.let { parseUri(it, "webhookUrl").toString() }
        internal val apiKey: String = parseApiKey(apiKey)

        init {
            if (timeout.isNegative || timeout.isZero) throw ConfigurationException("timeout must be positive")
        }

        override fun toString(): String = "OpenFspConfig(baseUrl=$baseUrl, timeout=$timeout)"

        class Builder internal constructor() {
            private var baseUrl: String? = null
            private var apiKey: String? = null
            private var timeout: Duration = DEFAULT_TIMEOUT
            private var retryPolicy: RetryPolicy = RetryPolicy.DEFAULT
            private var webhookUrl: String? = null
            private var allowPlainHttp = false
            private var recoverLostAnswers = true
            private var pendingOnUnknownStatus = false

            fun baseUrl(value: String) = apply { baseUrl = value }

            fun apiKey(value: String) = apply { apiKey = value }

            fun timeout(value: Duration) = apply { timeout = value }

            fun retryPolicy(value: RetryPolicy) = apply { retryPolicy = value }

            fun webhookUrl(value: String) = apply { webhookUrl = value }

            fun allowPlainHttp() = apply { allowPlainHttp = true }

            fun recoverLostAnswers(value: Boolean) = apply { recoverLostAnswers = value }

            fun pendingOnUnknownStatus() = apply { pendingOnUnknownStatus = true }

            fun build(): OpenFspConfig =
                OpenFspConfig(
                    baseUrl ?: throw ConfigurationException("baseUrl is required"),
                    apiKey ?: throw ConfigurationException("apiKey is required"),
                    timeout,
                    retryPolicy,
                    webhookUrl,
                    allowPlainHttp,
                    recoverLostAnswers,
                    pendingOnUnknownStatus,
                )
        }

        companion object {
            private const val PREFIX = "OPENFSP_"
            private val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(30)
            private val LOOPBACK_V4 = Regex("^127(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$")

            @JvmStatic
            fun builder(): Builder = Builder()

            @JvmStatic
            @JvmOverloads
            fun fromEnv(
                environment: Map<String, String> = System.getenv(),
                prefix: String = PREFIX,
            ): OpenFspConfig {
                fun read(name: String) = environment["$prefix$name"]?.takeIf { it.isNotBlank() }

                fun required(name: String) = read(name) ?: throw ConfigurationException("$prefix$name is required")
                val timeout = read("TIMEOUT_MS")?.let { it.toLongOrNull() ?: throw ConfigurationException("${prefix}TIMEOUT_MS must be a number") }
                return OpenFspConfig(
                    baseUrl = required("BASE_URL"),
                    apiKey = required("API_KEY"),
                    timeout = timeout?.let(Duration::ofMillis) ?: DEFAULT_TIMEOUT,
                    webhookUrl = read("WEBHOOK_URL"),
                    allowPlainHttp = read("ALLOW_PLAIN_HTTP") == "true",
                )
            }

            private fun parseApiKey(value: String): String {
                if (value.isEmpty() || value.any { it.isWhitespace() || it.isISOControl() }) {
                    throw ConfigurationException("apiKey must be a non-empty token without whitespace")
                }
                return value
            }

            private fun parseBaseUrl(
                value: String,
                allowPlainHttp: Boolean,
            ): URI {
                val uri = parseUri(value, "baseUrl")
                val host = uri.host ?: throw ConfigurationException("baseUrl must have a host")
                if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) {
                    throw ConfigurationException("baseUrl must not carry credentials, a query or a fragment")
                }
                when (uri.scheme.lowercase()) {
                    "https" -> Unit
                    "http" ->
                        if (!allowPlainHttp && !isLoopback(host)) {
                            throw ConfigurationException("baseUrl must use https outside a loopback host; allow plain http for a development network")
                        }
                    else -> throw ConfigurationException("baseUrl must use https")
                }
                return URI(uri.scheme.lowercase(), null, host, uri.port, uri.rawPath.orEmpty().trimEnd('/') + "/", null, null)
            }

            private fun parseUri(
                value: String,
                name: String,
            ): URI {
                val uri =
                    try {
                        URI(value.trim())
                    } catch (invalid: URISyntaxException) {
                        throw ConfigurationException("$name is not a valid URL")
                    }
                if (!uri.isAbsolute) throw ConfigurationException("$name must be an absolute URL")
                return uri
            }

            private fun isLoopback(host: String): Boolean {
                val name = host.removeSurrounding("[", "]").lowercase()
                return name == "localhost" || name.endsWith(".localhost") || name == "::1" || name == "0:0:0:0:0:0:0:1" || LOOPBACK_V4.matches(name)
            }
        }
    }
