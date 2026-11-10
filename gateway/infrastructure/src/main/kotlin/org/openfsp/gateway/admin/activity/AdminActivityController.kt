package org.openfsp.gateway.admin.activity

import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.controllers.payments.Collection
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
    fun latest(
        @RequestParam(required = false) kind: List<String>?,
        @RequestParam(defaultValue = "100") limit: Int,
    ): Collection<ActivityItem> {
        val kinds =
            kind?.map { wire -> ActivityKind.entries.firstOrNull { it.wire == wire } ?: throw OpenFspException.invalid("kind", "is not an activity kind") }?.toSet()
                ?: ActivityKind.entries.toSet()
        return Collection(feed.latest(kinds, limit.coerceIn(1, MAX)))
    }

    private companion object {
        const val MAX = 300
    }
}
