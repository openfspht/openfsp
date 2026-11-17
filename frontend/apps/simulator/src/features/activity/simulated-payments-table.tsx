import {
    Card,
    CardContent,
    Money,
    Muted,
    SortableHead,
    type SortState,
    StatusIndicator,
    TableEmptyRow,
    Tabular,
    Time,
} from "@workspace/design-system"
import type { PaymentStatus, SimulatedPayment } from "@workspace/types"
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@workspace/ui/components/table"

import { useI18n } from "@/i18n/i18n-provider"

const STATUS_OF: Record<NonNullable<SimulatedPayment["outcome"]>, PaymentStatus> = {
    SUCCESSFUL: "succeeded",
    FAILED: "failed",
    EXPIRED: "expired",
}

const MINOR_UNITS = 100

export const simulatedStatus = (payment: SimulatedPayment): PaymentStatus =>
    payment.outcome ? STATUS_OF[payment.outcome] : "pending"

interface SimulatedPaymentsTableProps {
    payments: SimulatedPayment[]
    operatorNames?: Record<string, string>
    onSelect: (payment: SimulatedPayment, trigger: HTMLElement) => void
    sort?: SortState
    onSort?: (key: string) => void
}

export function SimulatedPaymentsTable({
    payments,
    operatorNames = {},
    onSelect,
    sort,
    onSort,
}: SimulatedPaymentsTableProps) {
    const copy = useI18n().t
    const labels = copy.payments
    return (
        <Card>
            <CardContent>
                <Table>
                    <TableHeader>
                        <TableRow>
                            <TableHead>{labels.payment}</TableHead>
                            <SortableHead
                                label={copy.operator}
                                sortKey="operator"
                                sort={sort}
                                onSort={onSort}
                            />
                            <SortableHead
                                label={labels.amount}
                                sortKey="amount"
                                sort={sort}
                                onSort={onSort}
                                align="right"
                            />
                            <SortableHead
                                label={labels.state}
                                sortKey="state"
                                sort={sort}
                                onSort={onSort}
                            />
                        </TableRow>
                    </TableHeader>
                    <TableBody striped>
                        {payments.length === 0 && (
                            <TableEmptyRow columns={4}>{labels.empty}</TableEmptyRow>
                        )}
                        {payments.map((payment) => (
                            <TableRow
                                key={payment.payment_id}
                                onClick={(event) => {
                                    const trigger = event.currentTarget.querySelector("button")
                                    if (trigger && !trigger.contains(event.target as Node)) {
                                        onSelect(payment, trigger)
                                    }
                                }}
                                className="cursor-pointer"
                            >
                                <TableCell>
                                    <button
                                        type="button"
                                        onClick={(event) => onSelect(payment, event.currentTarget)}
                                        className="block text-left outline-none hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
                                    >
                                        <Tabular>{payment.payment_id}</Tabular>
                                    </button>
                                    <Muted className="text-xs">
                                        {payment.order_id} · {payment.scenario}
                                    </Muted>
                                </TableCell>
                                <TableCell>
                                    <span className="block">
                                        {operatorNames[payment.operator] ?? payment.operator}
                                    </span>
                                    <Muted className="text-xs">
                                        <Time value={payment.created_at} />
                                    </Muted>
                                </TableCell>
                                <TableCell className="text-right">
                                    <Money
                                        value={{
                                            amount: Math.round(
                                                Number(payment.amount) * MINOR_UNITS
                                            ),
                                            currency: "HTG",
                                        }}
                                    />
                                    {payment.reported_amount &&
                                        payment.reported_amount !== payment.amount && (
                                            <Muted className="block text-xs">
                                                {labels.reported(payment.reported_amount)}
                                            </Muted>
                                        )}
                                </TableCell>
                                <TableCell>
                                    <StatusIndicator status={simulatedStatus(payment)} />
                                    <Muted className="block text-xs">
                                        {labels.exchanges(payment.callbacks_sent, payment.lookups)}
                                    </Muted>
                                </TableCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            </CardContent>
        </Card>
    )
}
