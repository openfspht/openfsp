import type { JournalEntry } from "@workspace/types"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@workspace/ui/components/table"

import { Card, CardContent, Muted, Tabular, Time } from "../atoms"
import { useCopy } from "../copy"
import { TableEmptyRow } from "../molecules"

const OPERATOR_PREFIX = /^(\/mock_[^/?]+)(.*)$/

export function RequestLine({ method, path }: { method: string; path: string }) {
  const [, prefix, rest] = path.match(OPERATOR_PREFIX) ?? [undefined, "", path]
  return (
    <Tabular className="normal-case">
      {method} {prefix && <span className="font-semibold text-primary">{prefix}</span>}
      {rest}
    </Tabular>
  )
}

interface RequestJournalTableProps {
  entries: JournalEntry[]
  operatorNames?: Record<string, string>
  onSelect: (entry: JournalEntry) => void
}

export function RequestJournalTable({ entries, operatorNames = {}, onSelect }: RequestJournalTableProps) {
  const copy = useCopy().simulator
  return (
    <Card>
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{copy.journal.request}</TableHead>
              <TableHead>{copy.operator}</TableHead>
              <TableHead className="text-right">{copy.journal.status}</TableHead>
              <TableHead className="text-right">{copy.journal.received}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody striped>
            {entries.length === 0 && <TableEmptyRow columns={4}>{copy.journal.empty}</TableEmptyRow>}
            {entries.map((entry, index) => (
              <TableRow key={`${entry.received_at}-${index}`} onClick={() => onSelect(entry)} className="cursor-pointer">
                <TableCell>
                  <RequestLine method={entry.method} path={entry.path} />
                </TableCell>
                <TableCell>{operatorNames[entry.operator] ?? entry.operator}</TableCell>
                <TableCell className="text-right">
                  <Tabular>{entry.status}</Tabular>
                  <Muted className="block text-xs">{copy.journal.duration(entry.duration_ms)}</Muted>
                </TableCell>
                <TableCell className="text-right text-muted-foreground">
                  <Time value={entry.received_at} />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  )
}
