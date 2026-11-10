package org.openfsp.simulator.operator.journal.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.simulator.operator.journal.JournalEntry
import org.openfsp.simulator.operator.journal.spi.RequestJournal
import java.util.concurrent.CopyOnWriteArrayList

@Stub
class InMemoryRequestJournal : RequestJournal {
    private val entries = CopyOnWriteArrayList<JournalEntry>()

    override fun record(entry: JournalEntry) {
        entries += entry
    }

    override fun entries(operator: String?): List<JournalEntry> = entries.filter { operator == null || it.operator == operator }

    override fun clear() = entries.clear()
}
