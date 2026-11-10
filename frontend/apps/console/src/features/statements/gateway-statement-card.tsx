import {
  Card,
  CardContent,
  Day,
  EmptyText,
  Money,
  Muted,
  OptionSelect,
  StatusIndicator,
  Tabular,
  Time,
} from "@workspace/design-system"
import type { GatewayStatement, PaymentStatus } from "@workspace/types"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@workspace/ui/components/table"

import { useI18n } from "@/i18n/i18n-provider"

interface GatewayStatementCardProps<T extends string> {
  statement?: GatewayStatement
  provider: T
  providers: readonly T[]
  onProviderChange: (provider: T) => void
  labelOf?: (provider: T) => string
}

export function GatewayStatementCard<T extends string>({
  statement,
  provider,
  providers,
  onProviderChange,
  labelOf,
}: GatewayStatementCardProps<T>) {
  const copy = useI18n().t.gatewayStatement
  const totals = Object.entries(statement?.totals ?? {}) as [
    PaymentStatus,
    { count: number; amount: { amount: number; currency: "HTG" } },
  ][]
  return (
    <Card>
      <CardContent className="space-y-4">
        <div className="flex items-center justify-between gap-4">
          <Muted className="text-sm">
            {statement ? <Day value={statement.business_date} /> : copy.today}
          </Muted>
          <OptionSelect
            value={provider}
            options={providers}
            onChange={onProviderChange}
            labelOf={labelOf}
          />
        </div>
        {totals.length === 0 && <EmptyText>{copy.empty}</EmptyText>}
        <div className="space-y-2">
          {totals.map(([status, total]) => (
            <div
              key={status}
              className="flex items-center justify-between text-sm"
            >
              <StatusIndicator status={status} />
              <span className="flex gap-6 tabular-nums">
                <Muted>{copy.count(total.count)}</Muted>
                <Money value={total.amount} />
              </span>
            </div>
          ))}
        </div>
        {statement && statement.payments.length > 0 && (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{copy.reference}</TableHead>
                <TableHead className="text-right">{copy.amount}</TableHead>
                <TableHead className="text-right">{copy.settledAt}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody striped>
              {statement.payments.map((line) => (
                <TableRow key={line.payment}>
                  <TableCell>
                    <Tabular>{line.reference}</Tabular>
                  </TableCell>
                  <TableCell className="text-right">
                    <Money value={line.amount} />
                  </TableCell>
                  <TableCell className="text-right text-muted-foreground">
                    <Time value={line.completed_at} />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  )
}
