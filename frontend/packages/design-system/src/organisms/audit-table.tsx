import type { AuditRecord } from "@workspace/types"
import { Table, TableBody, TableCell, TableHeader, TableRow } from "@workspace/ui/components/table"

import { Badge, Card, CardContent, CardDescription, CardHeader, CardTitle, Tabular, Time } from "../atoms"
import { useCopy } from "../copy"
import { SortableHead, type SortState } from "../molecules"

const FAILURE = /^[45]\d\d$/

export function AuditTable({ records, headless, sort, onSort }: { records: AuditRecord[]; headless?: boolean; sort?: SortState; onSort?: (key: string) => void }) {
  const copy = useCopy().audit
  return (
    <Card>
      {!headless && (
        <CardHeader>
          <CardTitle className="font-heading">{copy.title}</CardTitle>
          <CardDescription>{copy.description}</CardDescription>
        </CardHeader>
      )}
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow>
              <SortableHead label={copy.when} sortKey="recorded_at" sort={sort} onSort={onSort} />
              <SortableHead label={copy.kind} sortKey="kind" sort={sort} onSort={onSort} />
              <SortableHead label={copy.operation} sortKey="operation" sort={sort} onSort={onSort} />
              <SortableHead label={copy.principal} sortKey="principal" sort={sort} onSort={onSort} />
              <SortableHead label={copy.result} sortKey="result" sort={sort} onSort={onSort} align="right" />
            </TableRow>
          </TableHeader>
          <TableBody striped>
            {records.length === 0 && (
              <TableRow>
                <TableCell colSpan={5} className="py-10 text-center text-muted-foreground">
                  {copy.empty}
                </TableCell>
              </TableRow>
            )}
            {records.map((record, index) => (
              <TableRow key={`${record.recorded_at}-${index}`}>
                <TableCell className="text-muted-foreground">
                  <Time value={record.recorded_at} />
                </TableCell>
                <TableCell>
                  <Badge variant={record.kind.includes("failure") ? "destructive" : "secondary"}>{copy.kinds[record.kind] ?? record.kind.replaceAll("_", " ")}</Badge>
                </TableCell>
                <TableCell>
                  <Tabular className="normal-case">{record.operation}</Tabular>
                </TableCell>
                <TableCell>
                  <Tabular className="text-muted-foreground normal-case">{record.lookup_prefix ?? record.principal_id ?? ""}</Tabular>
                </TableCell>
                <TableCell className={FAILURE.test(record.result) ? "text-right text-destructive tabular-nums" : "text-right tabular-nums"}>{record.result}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  )
}
