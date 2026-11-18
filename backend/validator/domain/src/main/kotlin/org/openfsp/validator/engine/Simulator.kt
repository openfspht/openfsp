package org.openfsp.validator.engine

import org.openfsp.validator.engine.spi.SimulatorControl
import org.openfsp.validator.suite.SimulatorAction
import java.time.Duration
import java.time.Instant

class PreparationException(
    message: String,
) : RuntimeException(message)

class Simulator(
    private val control: SimulatorControl?,
) {
    fun perform(
        action: SimulatorAction,
        variables: Variables,
    ) {
        action.declare?.let {
            control().declareScenario(variables.resolve(it.provider), variables.resolve(it.reference), variables.resolve(it.scenario))
        }
        action.default?.let {
            control().defaultScenario(variables.resolve(it.provider), variables.resolve(it.scenario))
        }
        action.resolve?.let {
            val paymentId = paymentOf(variables.resolve(it.provider), variables.resolve(it.reference))
            control().resolve(paymentId, variables.resolve(it.outcome), it.reason?.let(variables::resolve))
        }
        action.callback?.let {
            control().callback(paymentOf(variables.resolve(it.provider), variables.resolve(it.reference)))
        }
        action.settled?.let { settled(variables.resolve(it.provider), variables.resolve(it.reference), it.callbacks, Durations.parse(it.within), Durations.parse(it.every)) }
        if (action.reset) control().reset()
    }

    private fun settled(
        provider: String,
        reference: String,
        callbacks: Int,
        within: Duration,
        every: Duration,
    ) {
        val deadline = Instant.now().plus(within)
        while (true) {
            val payment = control().payment(provider, reference)
            if (payment != null && payment.settled && payment.callbacksSent >= callbacks) return
            if (Instant.now().plus(every).isAfter(deadline)) {
                throw PreparationException("the simulator did not settle the $provider payment with reference $reference${if (callbacks > 0) " with $callbacks event(s)" else ""} within ${within.toSeconds()}s")
            }
            Thread.sleep(every)
        }
    }

    private fun paymentOf(
        provider: String,
        reference: String,
    ): String = control().payment(provider, reference)?.paymentId ?: throw PreparationException("the simulator holds no $provider payment with reference $reference")

    private fun control(): SimulatorControl = control ?: throw PreparationException("the scenario drives the mock server but no control URL is configured")
}
