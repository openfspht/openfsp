package org.openfsp.simulator.emission

import org.openfsp.ddd.DomainService
import org.openfsp.simulator.common.SimulationClock
import org.openfsp.simulator.emission.api.EmitEvents
import org.openfsp.simulator.emission.api.OpenRuns
import org.openfsp.simulator.emission.spi.ConformanceRuns
import org.openfsp.simulator.emission.spi.EventEncoder
import org.openfsp.simulator.emission.spi.EventTransport
import org.openfsp.simulator.operator.Caller
import org.openfsp.simulator.operator.Principal
import org.openfsp.simulator.operator.spi.OperatorProfiles
import org.openfsp.simulator.payment.SimulatedPayment
import org.openfsp.simulator.settlement.spi.Timers
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.UUID

@DomainService
class EventEmission(
    private val profiles: OperatorProfiles,
    private val runs: ConformanceRuns,
    private val encoder: EventEncoder,
    private val transport: EventTransport,
    private val timers: Timers,
    private val clock: SimulationClock,
) : EmitEvents,
    OpenRuns {
    private val random = SecureRandom()

    override fun emit(payment: SimulatedPayment) {
        if (profiles[payment.operator]?.emitsEvents != true) return
        val run = payment.account
        endpointOf(run) ?: return
        val event = encoder.encode(payment)
        timers.after(Duration.ZERO) { attempt(payment.operator, run, event, FIRST_ATTEMPT) }
    }

    override fun open(
        operator: String,
        credential: String,
        restrictedCredential: String,
        webhookUrl: String?,
        lifetime: Duration,
    ): ConformanceRun = runs.save(ConformanceRun("$RUN_PREFIX${UUID.randomUUID()}", operator, credential, restrictedCredential, webhookUrl, clock.now().plus(lifetime)))

    override fun authenticate(
        operator: String,
        credential: String,
    ): Caller? {
        val now = clock.now()
        val given = credential.toByteArray()
        return runs.all().filter { it.operator == operator && it.isActiveAt(now) }.firstNotNullOfOrNull { run ->
            when {
                MessageDigest.isEqual(run.credential.toByteArray(), given) -> Principal.PRIMARY
                MessageDigest.isEqual(run.restrictedCredential.toByteArray(), given) -> Principal.SECONDARY
                else -> null
            }?.let { Caller(authenticated = true, credential = credential, principal = it, account = run.id) }
        }
    }

    private fun attempt(
        operator: String,
        run: String,
        event: EncodedEvent,
        number: Int,
    ) {
        val url = endpointOf(run) ?: return
        val result = transport.deliver(operator, url, event, number)
        val delay = RetrySchedule.delayAfter(number, result, random.nextDouble())
        when {
            delay != null -> timers.after(delay) { attempt(operator, run, event, number + 1) }
            !result.succeeded && !result.gone -> transport.abandoned(url, event, number)
        }
    }

    private fun endpointOf(run: String): String? = runs.find(run)?.takeIf { it.isActiveAt(clock.now()) }?.webhookUrl

    private companion object {
        const val FIRST_ATTEMPT = 1
        const val RUN_PREFIX = "run_"
    }
}
