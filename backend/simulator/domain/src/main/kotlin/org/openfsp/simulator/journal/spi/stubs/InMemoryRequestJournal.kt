package org.openfsp.simulator.journal.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.simulator.journal.JournalEntry
import org.openfsp.simulator.journal.spi.RequestJournal
import java.util.concurrent.CopyOnWriteArrayList

@Stub
class InMemoryRequestJournal : RequestJournal {
    private val journal = CopyOnWriteArrayList<JournalEntry>()

    override fun record(entry: JournalEntry) {
        journal += entry
    }

    override fun entries(operator: String?): List<JournalEntry> = journal.filter { operator == null || it.operator == operator }

    override fun clear() {
        journal.clear()
    }
}
