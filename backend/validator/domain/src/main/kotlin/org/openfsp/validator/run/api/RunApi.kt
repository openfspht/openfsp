package org.openfsp.validator.run.api

import org.openfsp.validator.run.PreparedRun
import org.openfsp.validator.run.RunOrder
import org.openfsp.validator.run.spi.RunEventStream
import java.util.UUID

interface PrepareRuns {
    fun prepare(
        target: String?,
        client: String,
    ): PreparedRun

    fun find(id: UUID): PreparedRun?

    fun webhookUrl(id: UUID): String
}

interface StartRuns {
    fun start(
        id: UUID,
        order: RunOrder,
    )
}

interface FollowRuns {
    fun follow(
        id: UUID,
        lastEventId: Long?,
        stream: RunEventStream,
    )

    fun report(id: UUID): String
}

interface SweepRuns {
    fun sweep()
}
