package org.openfsp.validator.run

import org.openfsp.ddd.DomainService
import org.openfsp.validator.run.api.RunState
import org.openfsp.validator.run.api.RunStates
import java.util.UUID

@DomainService
class RunStateQuery(
    private val prepared: PreparedRuns,
    private val sessions: RunSessions,
) : RunStates {
    override fun state(id: UUID): RunState =
        when {
            prepared.isWaiting(id) -> RunState.PREPARED
            sessions.find(id)?.finished == false -> RunState.RUNNING
            else -> RunState.ENDED
        }
}
