package org.openfsp.simulator.memory

import org.openfsp.simulator.journal.JournalEntry
import org.openfsp.simulator.journal.spi.RequestJournal

class MemoryRequestJournal(
    private val capacity: Int,
) : RequestJournal {
    private val journal = ArrayDeque<JournalEntry>()

    override fun record(entry: JournalEntry) {
        synchronized(journal) {
            journal.addLast(entry)
            while (journal.size > capacity) journal.removeFirst()
        }
    }

    override fun entries(operator: String?): List<JournalEntry> = synchronized(journal) { journal.filter { operator == null || it.operator == operator } }

    override fun clear() {
        synchronized(journal) { journal.clear() }
    }
}
