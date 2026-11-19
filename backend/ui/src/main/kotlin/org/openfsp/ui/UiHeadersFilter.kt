package org.openfsp.ui

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.web.filter.OncePerRequestFilter

class UiHeadersFilter(
    private val paths: UiPaths,
    private val userInterface: UserInterface,
) : OncePerRequestFilter(),
    Ordered {
    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = !paths.serves(request)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        response.setHeader(CONTENT_SECURITY_POLICY, userInterface.contentSecurityPolicy)
        response.setHeader(CONTENT_TYPE_OPTIONS, NOSNIFF)
        response.setHeader(FRAME_OPTIONS, DENY)
        response.setHeader(REFERRER_POLICY, NO_REFERRER)
        response.setHeader(PERMISSIONS_POLICY, NO_PERMISSIONS)
        response.setHeader(CROSS_ORIGIN_OPENER_POLICY, SAME_ORIGIN)
        if (request.isSecure) response.setHeader(STRICT_TRANSPORT_SECURITY, HSTS)
        chain.doFilter(request, response)
    }

    companion object {
        const val CONTENT_SECURITY_POLICY = "Content-Security-Policy"
        const val CONTENT_TYPE_OPTIONS = "X-Content-Type-Options"
        const val FRAME_OPTIONS = "X-Frame-Options"
        const val REFERRER_POLICY = "Referrer-Policy"
        const val PERMISSIONS_POLICY = "Permissions-Policy"
        const val CROSS_ORIGIN_OPENER_POLICY = "Cross-Origin-Opener-Policy"
        const val STRICT_TRANSPORT_SECURITY = "Strict-Transport-Security"
        const val NOSNIFF = "nosniff"
        const val DENY = "DENY"
        const val NO_REFERRER = "no-referrer"
        const val SAME_ORIGIN = "same-origin"
        const val NO_PERMISSIONS = "camera=(), microphone=(), geolocation=(), payment=(), usb=(), serial=(), bluetooth=(), hid=()"
        const val HSTS = "max-age=63072000; includeSubDomains"
    }
}
