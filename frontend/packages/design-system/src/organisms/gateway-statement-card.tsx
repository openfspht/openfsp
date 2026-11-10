import type { GatewayStatement, PaymentStatus } from "@workspace/types"

import { Card, CardContent, CardDescription, CardHeader, CardTitle, EmptyText, Mono, Money, Muted, StatusIndicator, Time } from "../atoms"
import { OptionSelect } from "../molecules"
import { useCopy } from "../copy"

interface GatewayStatementCardProps<T extends string> {
  statement?: GatewayStatement
  provider: T
  providers: readonly T[]
  onProviderChange: (provider: T) => void
}

export function GatewayStatementCard<T extends string>({ statement, provider, providers, onProviderChange }: GatewayStatementCardProps<T>) {
  const copy = useCopy().gatewayStatement
  const totals = Object.entries(statement?.totals ?? {}) as [PaymentStatus, { count: number; amount: { amount: number; currency: "HTG" } }][]
  return (
    <Card>
      <CardHeader>
        <CardTitle className="font-heading">{copy.title}</CardTitle>
        <CardDescription>
          {copy.description(Boolean(statement && !statement.final))}
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex items-center justify-between gap-4">
          <Muted className="text-sm">{statement?.business_date ?? copy.today}</Muted>
          <OptionSelect value={provider} options={providers} onChange={onProviderChange} />
        </div>
        {totals.length === 0 && <EmptyText>{copy.empty}</EmptyText>}
        <div className="space-y-2">
          {totals.map(([status, total]) => (
            <div key={status} className="flex items-center justify-between text-sm">
              <StatusIndicator status={status} />
              <span className="tabular-nums">
                {total.count} · <Money value={total.amount} />
              </span>
            </div>
          ))}
        </div>
        <div className="space-y-1">
          {statement?.payments.map((line) => (
            <div key={line.payment} className="flex items-center justify-between gap-2 text-xs">
              <Mono className="truncate">{line.reference}</Mono>
              <Muted className="tabular-nums">
                <Money value={line.amount} /> · <Time value={line.completed_at} />
              </Muted>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  )
}
