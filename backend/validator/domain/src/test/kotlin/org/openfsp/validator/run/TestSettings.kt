package org.openfsp.validator.run

import java.time.Duration

object TestSettings {
    fun run(
        runsPerAddress: Int = 6,
        runsWindow: Duration = Duration.ofHours(1),
        concurrency: Int = 4,
        preparedRunLifetime: Duration = Duration.ofHours(1),
        webhookBytes: Long = 1L shl 20,
        maxPreparedRuns: Int = 20,
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
        webhookBytes = webhookBytes,
        maxPreparedRuns = maxPreparedRuns,
        environment = "test",
        keyPrefix = "ofsp_test_",
    )
}
