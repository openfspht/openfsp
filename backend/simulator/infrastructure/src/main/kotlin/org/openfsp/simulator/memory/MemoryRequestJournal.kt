package org.openfsp.simulator.memory

import org.openfsp.simulator.journal.JournalEntry
import org.openfsp.simulator.journal.spi.RequestJournal
import org.openfsp.simulator.space.SpaceId
import java.util.concurrent.ConcurrentHashMap

class MemoryRequestJournal(
    private val capacity: Int,
) : RequestJournal {
    private val journals = ConcurrentHashMap<SpaceId, ArrayDeque<JournalEntry>>()

    override fun record(
        space: SpaceId,
        entry: JournalEntry,
    ) {
        val journal = journals.computeIfAbsent(space) { ArrayDeque() }
        synchronized(journal) {
            journal.addLast(entry)
            while (journal.size > capacity) journal.removeFirst()
        }
    }

    override fun entries(
        space: SpaceId,
        operator: String?,
    ): List<JournalEntry> {
        val journal = journals[space] ?: return emptyList()
        return synchronized(journal) { journal.filter { operator == null || it.operator == operator } }
    }

    override fun clear(space: SpaceId) {
        journals.remove(space)
    }
}
