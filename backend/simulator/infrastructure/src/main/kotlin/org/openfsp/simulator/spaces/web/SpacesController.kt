package org.openfsp.simulator.spaces.web

import org.openfsp.simulator.shared.web.ControlProblem
import org.openfsp.simulator.shared.web.orNotFound
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

@RestController
@RequestMapping(SpaceRoutes.PREFIX)
class SpacesController(
    private val spaces: ManageSpaces,
    private val routes: SpaceRoutes,
) {
    @PostMapping
    fun open(): ResponseEntity<SpaceResource> {
        val space = spaces.open() ?: throw ControlProblem(HttpStatus.SERVICE_UNAVAILABLE, "Every simulator space is in use. Try again once one expires.")
        return ResponseEntity.created(URI.create(routes.pathOf(space.id))).body(resourceOf(space))
    }

    @GetMapping("/{space}")
    fun show(
        @PathVariable space: String,
    ): SpaceResource = SpaceId.parse(space)?.let(spaces::find).orNotFound(SpaceMissing.DETAIL).let(::resourceOf)

    @DeleteMapping("/{space}")
    fun close(
        @PathVariable space: String,
    ): ResponseEntity<Void> = if (SpaceId.parse(space)?.let(spaces::close) == true) ResponseEntity.noContent().build() else throw ControlProblem(HttpStatus.NOT_FOUND, SpaceMissing.DETAIL)

    private fun resourceOf(space: Space) =
        SpaceResource(space.id.value, routes.publicBaseUrl(space.id), space.openedAt, space.expiresAt(spaces.idleLifetime), spaces.idleLifetime.seconds)
}
