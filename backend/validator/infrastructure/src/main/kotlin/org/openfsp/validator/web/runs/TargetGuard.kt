package org.openfsp.validator.web.runs

import org.openfsp.protocol.PublicAddresses
import org.openfsp.validator.problem.ProblemKind
import org.openfsp.validator.problem.ValidatorException
import org.openfsp.validator.run.spi.TargetConnection
import org.openfsp.validator.run.spi.TargetConnections
import org.openfsp.validator.web.configuration.ValidatorProperties
import org.springframework.stereotype.Component
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.time.Duration

fun interface HostLookup {
    fun resolve(host: String): List<InetAddress>
}

data class PinnedTarget(
    val host: String,
    val addresses: List<InetAddress>,
)

@Component
class TargetGuard(
    private val properties: ValidatorProperties,
    private val lookup: HostLookup,
) : TargetConnections {
    override fun open(
        baseUrl: URI,
        timeout: Duration,
    ): TargetConnection = PinnedTransport(pin(baseUrl), ::resolve, timeout)

    fun pin(url: URI): PinnedTarget {
        val host = url.host.removePrefix("[").removeSuffix("]")
        return PinnedTarget(host, checked(host) { ValidatorException(it.kind, it.detail) })
    }

    fun resolve(host: String): List<InetAddress> = checked(host) { UnknownHostException(it.detail) }

    private fun checked(
        host: String,
        refuse: (Refusal) -> Exception,
    ): List<InetAddress> {
        val addresses =
            try {
                lookup.resolve(host)
            } catch (_: UnknownHostException) {
                emptyList()
            }
        if (addresses.isEmpty()) throw refuse(Refusal(ProblemKind.UNRESOLVABLE_HOST, "$host does not resolve."))
        if (properties.developmentMode) return addresses
        addresses.firstOrNull(PublicAddresses::isForbidden)?.let { address ->
            throw refuse(Refusal(ProblemKind.PRIVATE_ADDRESS, "$host resolves to ${address.hostAddress}, which is not a public address."))
        }
        return addresses
    }

    private data class Refusal(
        val kind: ProblemKind,
        val detail: String,
    )
}
