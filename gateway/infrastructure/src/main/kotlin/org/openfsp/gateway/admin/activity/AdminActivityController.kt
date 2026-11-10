package org.openfsp.gateway.admin.activity

import org.openfsp.gateway.admin.search.PageQuery
import org.openfsp.gateway.admin.search.PageResource
import org.openfsp.gateway.common.OpenFspException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/activity")
class AdminActivityController(
    private val feed: ActivityFeed,
) {
    @GetMapping
    fun page(
        @RequestParam(required = false) kind: List<String>?,
        @RequestParam(required = false) source: List<String>?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "${PageQuery.DEFAULT_SIZE}") size: Int,
    ): PageResource<ActivityItem> {
        val kinds = kind?.map { ActivityKind.fromWire(it) ?: throw OpenFspException.invalid("kind", "is not an activity kind") }?.toSet() ?: ActivityKind.entries.toSet()
        return feed.page(kinds, source.orEmpty().toSet(), PageQuery(page, size))
    }
}
