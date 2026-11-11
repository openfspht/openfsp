package org.openfsp.validator.web.runs

import org.openfsp.protocol.PublicAddresses
import org.openfsp.validator.web.configuration.ValidatorProperties
import org.openfsp.validator.web.problems.ProblemKind
import org.openfsp.validator.web.problems.ValidatorException
import org.springframework.stereotype.Component
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException

fun interface HostLookup {
    fun resolve(host: String): List<InetAddress>
}

@Component
class TargetGuard(
    private val properties: ValidatorProperties,
    private val lookup: HostLookup,
) {
    fun check(url: URI) {
        if (properties.developmentMode) return
        val host = url.host.removePrefix("[").removeSuffix("]")
        val addresses =
            try {
                lookup.resolve(host)
            } catch (_: UnknownHostException) {
                emptyList()
            }
        if (addresses.isEmpty()) throw ValidatorException(ProblemKind.UNRESOLVABLE_HOST, "$host does not resolve.")
        addresses.firstOrNull(PublicAddresses::isForbidden)?.let { address ->
            throw ValidatorException(ProblemKind.PRIVATE_ADDRESS, "$host resolves to ${address.hostAddress}, which is not a public address.")
        }
    }
}
