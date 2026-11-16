package org.openfsp.simulator.operator.journal.spi

import org.openfsp.simulator.operator.journal.JournalEntry
import org.openfsp.simulator.operator.space.SpaceId

interface RequestJournal {
    fun record(
        space: SpaceId,
        entry: JournalEntry,
    )

    fun entries(
        space: SpaceId,
        operator: String?,
    ): List<JournalEntry>

    fun clear(space: SpaceId)
}
