package org.openfsp.simulator.spaces.web

import org.springframework.http.HttpStatus

object SpaceMissing {
    const val DETAIL = "This simulator space does not exist or expired after inactivity. Open a new one with POST /runs."

    fun problem(instance: String): Map<String, Any> = mapOf("type" to "about:blank", "title" to "Not Found", "status" to HttpStatus.NOT_FOUND.value(), "detail" to DETAIL, "instance" to instance)
}
