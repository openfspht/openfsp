package org.openfsp.ui

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.server.PathContainer
import org.springframework.web.util.pattern.PathPattern
import org.springframework.web.util.pattern.PathPatternParser

class UiPaths(
    userInterface: UserInterface,
) {
    private val pages: List<PathPattern> = userInterface.routes.map(PathPatternParser.defaultInstance::parse)
    private val files: List<PathPattern> = listOf(ASSETS_PATTERN, SETTINGS).map(PathPatternParser.defaultInstance::parse)

    fun isPage(request: HttpServletRequest): Boolean = pathOf(request).let { path -> pages.any { it.matches(path) } }

    fun serves(request: HttpServletRequest): Boolean = isPage(request) || pathOf(request).let { path -> files.any { it.matches(path) } }

    private fun pathOf(request: HttpServletRequest): PathContainer = PathContainer.parsePath(request.requestURI.removePrefix(request.contextPath))

    companion object {
        const val ASSETS_PATTERN = "/assets/**"
        const val SETTINGS = "/monitoring.json"
    }
}
