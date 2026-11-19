package org.openfsp.gateway.admin.web

import org.openfsp.gateway.common.PageQuery
import org.openfsp.gateway.common.SortQuery
import org.openfsp.gateway.console.api.SearchEndpoints
import org.openfsp.gateway.webhook.api.ManageSubscriptions
import org.openfsp.gateway.webhook.api.RotateSigningKeys
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class EndpointRequest(
    val account: String,
    val name: String,
    val url: String,
    val events: Set<String>,
)

data class RotatedKeyResource(
    val kid: String,
)

@RestController
@RequestMapping("/admin/webhooks")
class AdminWebhooksController(
    private val endpoints: SearchEndpoints,
    private val registry: ManageSubscriptions,
    private val rotation: RotateSigningKeys,
    private val mapper: EndpointRequestMapper,
    private val console: ConsoleResourceMapper,
) {
    @GetMapping("/endpoints")
    fun endpoints(
        @RequestParam(required = false) account: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "${PageQuery.DEFAULT_SIZE}") size: Int,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) order: String?,
    ): PageResource<EndpointResource> = console.toResource(endpoints.search(account.blankToNull(), q.blankToNull(), PageQuery(page, size), SortQuery.of(sort, order)), console::toResource)

    @PostMapping("/endpoints")
    fun add(
        @RequestBody request: EndpointRequest,
    ): ResponseEntity<Void> {
        registry.add(request.account.trim(), request.name.trim(), mapper.toUrl(request), request.events)
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @DeleteMapping("/endpoints/{name}")
    fun remove(
        @PathVariable name: String,
    ): ResponseEntity<Void> = if (registry.remove(name)) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()

    @PostMapping("/endpoints/{name}/enable")
    fun enable(
        @PathVariable name: String,
    ): ResponseEntity<Void> = if (registry.enable(name)) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()

    @PostMapping("/keys/rotate")
    fun rotate(
        @RequestParam(defaultValue = "false") immediate: Boolean,
    ): RotatedKeyResource = mapper.toResource(rotation.rotate(immediate))
}
