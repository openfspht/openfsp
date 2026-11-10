import type { WebhookEndpoint } from "@workspace/types"

import { Badge, Button, Card, CardContent, CardDescription, CardHeader, CardTitle, EmptyText, ErrorText, Tabular, Muted, Time } from "../atoms"
import { useCopy } from "../copy"

interface WebhookEndpointsCardProps {
  endpoints: WebhookEndpoint[]
  onEnable: (name: string) => void
  busy?: boolean
}

export function WebhookEndpointsCard({ endpoints, onEnable, busy }: WebhookEndpointsCardProps) {
  const copy = useCopy().webhooks
  return (
    <Card>
      <CardHeader>
        <CardTitle className="font-heading">{copy.title}</CardTitle>
        <CardDescription>{copy.description}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {endpoints.length === 0 && <EmptyText>{copy.empty}</EmptyText>}
        {endpoints.map((endpoint) => (
          <div key={endpoint.name} className="space-y-2 border-b pb-4 last:border-b-0">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <span className="font-medium">{endpoint.name}</span>
              <div className="flex items-center gap-2">
                {endpoint.pending > 0 && <Badge variant="outline">{copy.pending(endpoint.pending)}</Badge>}
                {endpoint.disabled_at ? (
                  <Badge variant="destructive">{copy.disabled}</Badge>
                ) : (
                  <Badge variant="secondary">{endpoint.enabled ? copy.enabled : copy.off}</Badge>
                )}
              </div>
            </div>
            <Tabular className="block break-all text-muted-foreground normal-case">{endpoint.url}</Tabular>
            <Muted className="block text-xs">
              {copy.events}: {endpoint.events.join(", ")}
            </Muted>
            {endpoint.disabled_at && (
              <div className="flex flex-wrap items-center justify-between gap-2">
                <ErrorText>
                  <Time value={endpoint.disabled_at} withDate />
                  {endpoint.reason && ` · ${endpoint.reason}`}
                </ErrorText>
                <Button size="xs" variant="outline" disabled={busy} onClick={() => onEnable(endpoint.name)}>
                  {copy.enable}
                </Button>
              </div>
            )}
          </div>
        ))}
      </CardContent>
    </Card>
  )
}

interface SigningKeyCardProps {
  onRotate: () => void
  rotatedKid?: string
  busy?: boolean
}

export function SigningKeyCard({ onRotate, rotatedKid, busy }: SigningKeyCardProps) {
  const copy = useCopy().webhooks
  return (
    <Card>
      <CardHeader>
        <CardTitle className="font-heading">{copy.rotateTitle}</CardTitle>
        <CardDescription>{copy.rotateDescription}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        <Button variant="outline" disabled={busy} onClick={onRotate}>
          {copy.rotate}
        </Button>
        {rotatedKid && <Muted className="block text-sm">{copy.rotated(rotatedKid)}</Muted>}
      </CardContent>
    </Card>
  )
}
