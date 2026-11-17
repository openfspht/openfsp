package org.openfsp.simulator.memory

import org.openfsp.simulator.journal.JournalEntry
import org.openfsp.simulator.journal.spi.RequestJournal
import org.openfsp.simulator.space.SpaceId
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Component
class MemoryRequestJournal : RequestJournal {
    private val journals = ConcurrentHashMap<SpaceId, CopyOnWriteArrayList<JournalEntry>>()

    override fun record(
        space: SpaceId,
        entry: JournalEntry,
    ) {
        journals.computeIfAbsent(space) { CopyOnWriteArrayList() } += entry
    }

    override fun entries(
        space: SpaceId,
        operator: String?,
    ): List<JournalEntry> = journals[space].orEmpty().filter { operator == null || it.operator == operator }

    override fun clear(space: SpaceId) {
        journals.remove(space)
    }
}
