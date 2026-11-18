import {
    Card,
    CardContent,
    Day,
    Money,
    TableEmptyRow,
    Tabular,
    Time,
} from "@workspace/design-system"
import type { StatementEntry } from "@workspace/types"
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@workspace/ui/components/table"
import { Link } from "react-router"

import { useI18n } from "@/i18n/i18n-provider"

const COLUMNS = 6

export function StatementTable({ entries, empty }: { entries: StatementEntry[]; empty?: string }) {
    const copy = useI18n().t.statements
    return (
        <Card>
            <CardContent>
                <Table>
                    <TableHeader>
                        <TableRow>
                            <TableHead>{copy.bookedAt}</TableHead>
                            <TableHead>{copy.businessDay}</TableHead>
                            <TableHead>{copy.payment}</TableHead>
                            <TableHead>{copy.reference}</TableHead>
                            <TableHead className="text-right">{copy.amount}</TableHead>
                            <TableHead className="text-right">{copy.fee}</TableHead>
                        </TableRow>
                    </TableHeader>
                    <TableBody striped>
                        {entries.length === 0 && empty && (
                            <TableEmptyRow columns={COLUMNS}>{empty}</TableEmptyRow>
                        )}
                        {entries.map((entry, index) => (
                            <TableRow key={`${entry.booked_at}-${entry.payment ?? index}`}>
                                <TableCell>
                                    <Time value={entry.booked_at} />
                                </TableCell>
                                <TableCell className="text-muted-foreground">
                                    <Day value={entry.business_date} />
                                </TableCell>
                                <TableCell>
                                    {entry.payment && (
                                        <Link
                                            to={`/payments/${entry.payment}`}
                                            className="outline-none hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
                                        >
                                            <Tabular>{entry.payment}</Tabular>
                                        </Link>
                                    )}
                                </TableCell>
                                <TableCell>
                                    <Tabular>{entry.reference}</Tabular>
                                </TableCell>
                                <TableCell className="text-right">
                                    <Money value={entry.amount} />
                                </TableCell>
                                <TableCell className="text-right text-muted-foreground">
                                    {entry.fee_amount && <Money value={entry.fee_amount} />}
                                </TableCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            </CardContent>
        </Card>
    )
}
