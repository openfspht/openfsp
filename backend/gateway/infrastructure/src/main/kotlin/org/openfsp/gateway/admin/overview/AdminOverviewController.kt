package org.openfsp.gateway.admin.overview

import org.openfsp.gateway.admin.readmodel.Overview
import org.openfsp.gateway.admin.readmodel.OverviewResource
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/overview")
class AdminOverviewController(
    private val overview: Overview,
) {
    @GetMapping
    fun today(): OverviewResource = overview.today()
}
