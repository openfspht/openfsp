package org.openfsp.gateway.shared.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.util.UriUtils

object RequestPaths {
    private val callbackToken = Regex("^(/callbacks/[^/]+)/[^/]+$")
    private val ambiguousEncodings = Regex("%(2[eEfF]|5[cC]|3[bB]|00)")
    private val dotSegment = Regex("(^|/)\\.{1,2}(/|$)")

    fun safe(path: String): String = path.replace(callbackToken, "$1/***")

    fun isAmbiguous(request: HttpServletRequest): Boolean {
        val raw = request.requestURI
        return ';' in raw || '\\' in raw || "//" in raw || ambiguousEncodings.containsMatchIn(raw) || dotSegment.containsMatchIn(raw)
    }

    fun canonical(request: HttpServletRequest): String = UriUtils.decode(request.requestURI.removePrefix(request.contextPath), Charsets.UTF_8)

    fun within(
        request: HttpServletRequest,
        prefix: String,
    ): Boolean {
        val path = canonical(request)
        return path == prefix || path.startsWith("$prefix/")
    }
}
