package org.openfsp.gateway.admin.web

import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.common.PageQuery
import org.openfsp.gateway.console.ActivityKind
import org.openfsp.gateway.console.api.ReadActivity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/activity")
class AdminActivityController(
    private val feed: ReadActivity,
    private val console: ConsoleResourceMapper,
) {
    @GetMapping
    fun page(
        @RequestParam(required = false) kind: List<String>?,
        @RequestParam(required = false) source: List<String>?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "${PageQuery.DEFAULT_SIZE}") size: Int,
    ): PageResource<ActivityItem> = console.toResource(feed.page(kindsOf(kind), source.orEmpty().toSet(), PageQuery(page, size)), console::toResource)

    @GetMapping("/export")
    fun export(
        @RequestParam(required = false) kind: List<String>?,
        @RequestParam(required = false) source: List<String>?,
    ): PageResource<ActivityItem> = console.toResource(feed.page(kindsOf(kind), source.orEmpty().toSet(), PageQuery.EXPORT), console::toResource)

    private fun kindsOf(wires: List<String>?): Set<ActivityKind> =
        wires?.map { ActivityKind.fromWire(it) ?: throw OpenFspException.invalid("kind", "is not an activity kind") }?.toSet() ?: ActivityKind.entries.toSet()
}
