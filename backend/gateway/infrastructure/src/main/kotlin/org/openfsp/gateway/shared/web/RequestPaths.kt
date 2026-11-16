package org.openfsp.gateway.shared.web

object RequestPaths {
    private val callbackToken = Regex("^(/callbacks/[^/]+)/[^/]+$")

    fun safe(path: String): String = path.replace(callbackToken, "$1/***")
}
