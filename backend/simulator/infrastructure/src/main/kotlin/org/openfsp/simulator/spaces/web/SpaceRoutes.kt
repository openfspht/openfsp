package org.openfsp.simulator.spaces.web

import jakarta.servlet.http.HttpServletRequest
import org.openfsp.simulator.common.spi.SpaceAddresses
import org.openfsp.simulator.shared.configuration.SimulatorProperties
import org.openfsp.simulator.space.SpaceId
import org.springframework.stereotype.Component

@Component
class SpaceRoutes(
    private val properties: SimulatorProperties,
) : SpaceAddresses {
    override fun publicBaseUrl(space: SpaceId): String = properties.publicBaseUrl.trimEnd('/') + pathOf(space)

    fun pathOf(space: SpaceId): String = if (space.isDefault) "" else "$PREFIX/${space.value}"

    companion object {
        const val PREFIX = "/runs"
        val ATTRIBUTE: String = SpaceId::class.java.name

        fun of(request: HttpServletRequest): SpaceId = request.getAttribute(ATTRIBUTE) as? SpaceId ?: SpaceId.DEFAULT
    }
}
