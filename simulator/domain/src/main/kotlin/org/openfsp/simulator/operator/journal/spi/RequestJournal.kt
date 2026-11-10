package org.openfsp.simulator.operator.journal.spi

import org.openfsp.simulator.operator.journal.JournalEntry

interface RequestJournal {
    fun record(entry: JournalEntry)

    fun entries(operator: String?): List<JournalEntry>

    fun clear()
}
