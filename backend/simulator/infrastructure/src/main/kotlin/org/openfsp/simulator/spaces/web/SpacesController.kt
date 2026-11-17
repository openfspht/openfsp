package org.openfsp.simulator.spaces.web

import org.openfsp.simulator.space.Space
import org.openfsp.simulator.space.SpaceId
import org.openfsp.simulator.space.api.ManageSpaces
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.Instant

data class SpaceResource(
    val space: String,
    val baseUrl: String,
    val openedAt: Instant,
    val expiresAt: Instant,
    val idleLifetimeSeconds: Long,
)

data class SpacesFull(
    val error: String = "no_space_left",
    val message: String = "Every simulator space is in use. Try again once one expires.",
)

@RestController
@RequestMapping(SpaceRoutes.PREFIX)
class SpacesController(
    private val spaces: ManageSpaces,
    private val routes: SpaceRoutes,
) {
    @PostMapping
    fun open(): ResponseEntity<Any> {
        val space = spaces.open() ?: return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(SpacesFull())
        return ResponseEntity.created(URI.create(routes.pathOf(space.id))).body(resourceOf(space))
    }

    @GetMapping("/{space}")
    fun show(
        @PathVariable space: String,
    ): ResponseEntity<SpaceResource> = ResponseEntity.ofNullable(SpaceId.parse(space)?.let(spaces::find)?.let(::resourceOf))

    @DeleteMapping("/{space}")
    fun close(
        @PathVariable space: String,
    ): ResponseEntity<Void> = if (SpaceId.parse(space)?.let(spaces::close) == true) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()

    private fun resourceOf(space: Space) =
        SpaceResource(space.id.value, routes.publicBaseUrl(space.id), space.openedAt, space.expiresAt(spaces.idleLifetime), spaces.idleLifetime.seconds)
}
