package org.openfsp.validator.run

import java.net.URI

object ControlEndpoints {
    fun sameOrigin(
        first: String,
        second: String,
    ): Boolean {
        val one = runCatching { URI(first) }.getOrNull() ?: return false
        val other = runCatching { URI(second) }.getOrNull() ?: return false
        return one.scheme != null && one.scheme.equals(other.scheme, ignoreCase = true) && one.host != null && one.host.equals(other.host, ignoreCase = true) && one.port == other.port
    }
}
