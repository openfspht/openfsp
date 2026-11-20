package org.openfsp.gateway.admin.web

import org.openfsp.gateway.console.api.ReadOverview
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/overview")
class AdminOverviewController(
    private val overview: ReadOverview,
    private val console: ConsoleResourceMapper,
) {
    @GetMapping
    fun today(): OverviewResource = console.toResource(overview.today())
}
