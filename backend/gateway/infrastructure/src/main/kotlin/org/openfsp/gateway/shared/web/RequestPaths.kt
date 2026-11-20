package org.openfsp.gateway.shared.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component
import org.springframework.web.util.UriUtils

@Component
class RequestPaths {
    fun safe(path: String): String = path.replace(CALLBACK_TOKEN, "$1/***")

    fun isAmbiguous(request: HttpServletRequest): Boolean {
        val raw = request.requestURI
        return ';' in raw || '\\' in raw || "//" in raw || AMBIGUOUS_ENCODINGS.containsMatchIn(raw) || DOT_SEGMENT.containsMatchIn(raw)
    }

    fun canonical(request: HttpServletRequest): String = UriUtils.decode(request.requestURI.removePrefix(request.contextPath), Charsets.UTF_8)

    fun within(
        request: HttpServletRequest,
        prefix: String,
    ): Boolean {
        val path = canonical(request)
        return path == prefix || path.startsWith("$prefix/")
    }

    fun isProtected(request: HttpServletRequest): Boolean = within(request, PROTECTED_PATH)

    companion object {
        const val PROTECTED_PATH = "/v1"
        private val CALLBACK_TOKEN = Regex("^(/callbacks/[^/]+)/[^/]+$")
        private val AMBIGUOUS_ENCODINGS = Regex("%(2[eEfF]|5[cC]|3[bB]|00)")
        private val DOT_SEGMENT = Regex("(^|/)\\.{1,2}(/|$)")
    }
}
