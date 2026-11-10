import type { EventType, WebhookEndpoint } from "@workspace/types"
import { useState } from "react"

import { Badge, Button, Card, CardContent, CardDescription, CardHeader, CardTitle, EmptyText, ErrorText, Tabular, Muted, Time } from "../atoms"
import { useCopy } from "../copy"
import { LabeledInput } from "../molecules"

const EVENT_TYPES: readonly EventType[] = ["payment.created", "payment.succeeded", "payment.failed", "payment.expired", "payment.canceled"]

interface WebhookEndpointsCardProps {
  endpoints: WebhookEndpoint[]
  onEnable: (name: string) => void
  onRemove: (name: string) => void
  busy?: boolean
}

export function WebhookEndpointsCard({ endpoints, onEnable, onRemove, busy }: WebhookEndpointsCardProps) {
  const copy = useCopy().webhooks
  const [confirming, setConfirming] = useState<string>()
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
                {!endpoint.managed && <Badge variant="outline">{copy.configured}</Badge>}
                {endpoint.managed && confirming !== endpoint.name && (
                  <Button size="xs" variant="outline" disabled={busy} onClick={() => setConfirming(endpoint.name)}>
                    {copy.remove}
                  </Button>
                )}
                {endpoint.managed && confirming === endpoint.name && (
                  <>
                    <Button size="xs" variant="destructive" disabled={busy} onClick={() => onRemove(endpoint.name)}>
                      {copy.confirmRemove}
                    </Button>
                    <Button size="xs" variant="ghost" onClick={() => setConfirming(undefined)}>
                      {copy.cancel}
                    </Button>
                  </>
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
                  {endpoint.reason && <span className="block">{endpoint.reason}</span>}
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

interface AddWebhookCardProps {
  onAdd: (webhook: { name: string; url: string; events: EventType[] }) => void
  error?: string
  busy?: boolean
}

export function AddWebhookCard({ onAdd, error, busy }: AddWebhookCardProps) {
  const copy = useCopy().webhooks
  const [name, setName] = useState("")
  const [url, setUrl] = useState("")
  const [events, setEvents] = useState<EventType[]>(["payment.succeeded", "payment.failed"])
  const toggle = (event: EventType) => setEvents((current) => (current.includes(event) ? current.filter((it) => it !== event) : [...current, event]))
  return (
    <Card>
      <CardHeader>
        <CardTitle className="font-heading">{copy.addTitle}</CardTitle>
      </CardHeader>
      <CardContent>
        <form
          className="space-y-4"
          onSubmit={(event) => {
            event.preventDefault()
            onAdd({ name: name.trim(), url: url.trim(), events })
          }}
        >
          <LabeledInput id="webhook-name" label={copy.name} hint={copy.nameHint} placeholder={copy.namePlaceholder} value={name} onChange={(event) => setName(event.target.value)} />
          <LabeledInput id="webhook-url" label={copy.url} type="url" placeholder={copy.urlPlaceholder} value={url} onChange={(event) => setUrl(event.target.value)} />
          <fieldset className="space-y-2">
            <legend className="text-sm font-medium">{copy.events}</legend>
            <div className="flex flex-wrap gap-2">
              {EVENT_TYPES.map((type) => (
                <Button key={type} type="button" size="xs" variant={events.includes(type) ? "default" : "outline"} aria-pressed={events.includes(type)} onClick={() => toggle(type)}>
                  {type}
                </Button>
              ))}
            </div>
          </fieldset>
          {error && <ErrorText>{error}</ErrorText>}
          <Button type="submit" disabled={busy || !name.trim() || !url.trim() || events.length === 0}>
            {copy.add}
          </Button>
        </form>
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
