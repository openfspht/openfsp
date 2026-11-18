package org.openfsp.validator.run

import java.time.Duration

data class RunSettings(
    val requestTimeout: Duration,
    val finishedRunMemory: Duration,
    val runsPerAddress: Int,
    val runsWindow: Duration,
    val concurrency: Int,
    val preparedRunLifetime: Duration,
    val publicUrl: String,
    val runsPath: String,
    val webhookCapacity: Int,
    val webhookBytes: Long,
    val maxPreparedRuns: Int,
    val environment: String,
    val keyPrefix: String,
)
