package org.openfsp.simulator.memory

import org.openfsp.server.Digests
import org.openfsp.simulator.emission.ConformanceRun
import org.openfsp.simulator.emission.spi.ConformanceRuns
import java.time.Instant

class MemoryConformanceRuns : ConformanceRuns {
    private val held = HashMap<String, ConformanceRun>()
    private val byCredential = HashMap<CredentialKey, String>()

    @Synchronized
    override fun admit(
        run: ConformanceRun,
        capacity: Int,
        now: Instant,
    ): ConformanceRun? {
        if (held.values.count { it.isActiveAt(now) } >= capacity) return null
        held[run.id] = run
        byCredential[keyOf(run.operator, run.credential)] = run.id
        byCredential[keyOf(run.operator, run.restrictedCredential)] = run.id
        return run
    }

    @Synchronized
    override fun find(id: String): ConformanceRun? = held[id]

    @Synchronized
    override fun withCredential(
        operator: String,
        credential: String,
    ): ConformanceRun? = byCredential[keyOf(operator, credential)]?.let(held::get)

    @Synchronized
    override fun purge(before: Instant) {
        held.values.removeIf { it.expiresAt.isBefore(before) }
        byCredential.values.removeIf { it !in held }
    }

    @Synchronized
    override fun clear() {
        held.clear()
        byCredential.clear()
    }

    private fun keyOf(
        operator: String,
        credential: String,
    ) = CredentialKey(operator, Digests.sha256Hex(credential))

    private data class CredentialKey(
        val operator: String,
        val digest: String,
    )
}
