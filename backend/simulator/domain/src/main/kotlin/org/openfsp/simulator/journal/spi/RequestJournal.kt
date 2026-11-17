package org.openfsp.simulator.journal.spi

import org.openfsp.simulator.journal.JournalEntry
import org.openfsp.simulator.space.SpaceId

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
