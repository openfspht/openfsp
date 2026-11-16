package org.openfsp.simulator.operator.journal.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.simulator.operator.journal.JournalEntry
import org.openfsp.simulator.operator.journal.spi.RequestJournal
import org.openfsp.simulator.operator.space.SpaceId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Stub
class InMemoryRequestJournal : RequestJournal {
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
