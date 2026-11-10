import type { AuditRecord } from "@workspace/types"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@workspace/ui/components/table"

import { Badge, Card, CardContent, CardDescription, CardHeader, CardTitle, Mono, Time } from "../atoms"
import { useCopy } from "../copy"

const FAILURE = /^[45]\d\d$/

export function AuditTable({ records, headless }: { records: AuditRecord[]; headless?: boolean }) {
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
              <TableHead>{copy.when}</TableHead>
              <TableHead>{copy.kind}</TableHead>
              <TableHead>{copy.operation}</TableHead>
              <TableHead>{copy.principal}</TableHead>
              <TableHead className="text-right">{copy.result}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
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
                  <Time value={record.recorded_at} withDate />
                </TableCell>
                <TableCell>
                  <Badge variant={record.kind.includes("failure") ? "destructive" : "secondary"}>{copy.kinds[record.kind] ?? record.kind.replaceAll("_", " ")}</Badge>
                </TableCell>
                <TableCell>
                  <Mono className="normal-case">{record.operation}</Mono>
                </TableCell>
                <TableCell>
                  <Mono className="text-muted-foreground normal-case">{record.lookup_prefix ?? record.principal_id ?? ""}</Mono>
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
