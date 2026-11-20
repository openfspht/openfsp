package org.openfsp.simulator.journal.spi

import org.openfsp.simulator.journal.JournalEntry

interface RequestJournal {
    fun record(entry: JournalEntry)

    fun entries(operator: String?): List<JournalEntry>

    fun clear()

    fun clear(account: String)
}
