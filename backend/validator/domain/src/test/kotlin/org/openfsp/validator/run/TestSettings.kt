package org.openfsp.validator.run

import java.time.Duration

object TestSettings {
    fun run(
        runsPerAddress: Int = 6,
        runsWindow: Duration = Duration.ofHours(1),
        concurrency: Int = 4,
        preparedRunLifetime: Duration = Duration.ofHours(1),
    ) = RunSettings(
        requestTimeout = Duration.ofSeconds(5),
        finishedRunMemory = Duration.ofMinutes(15),
        runsPerAddress = runsPerAddress,
        runsWindow = runsWindow,
        concurrency = concurrency,
        preparedRunLifetime = preparedRunLifetime,
        publicUrl = "http://validator.test",
        runsPath = "/api/runs",
        webhookCapacity = 50,
        simulator =
            SimulatorSettings(
                publicUrl = "http://simulator.test",
                environment = "test",
                keyPrefix = "ofsp_test_",
                providers = mapOf("mock_openfsp" to SimulatedProvider("/mock_openfsp", mapOf("revoked-key" to "ofsp_test_revoked"), "ofsp_test_reference")),
            ),
    )
}
