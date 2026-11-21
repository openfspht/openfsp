import { Heading, Muted, Tabular, Time } from "../atoms"
import { useCopy } from "../copy"
import { indentJson } from "../lib/indent-json"
import { highlight } from "./json-panel"

export interface ExchangeView {
    status: number | string
    receivedAt?: string
    durationMs?: number
    remoteAddress?: string
    requestHeaders: Record<string, string>
    requestBody: string
    responseHeaders: Record<string, string>
    responseBody: string
}

const readable = (body: string): React.ReactNode => {
    try {
        return highlight(indentJson(body))
    } catch {
        return body
    }
}

function Part({
    title,
    headers,
    body,
}: {
    title: string
    headers: Record<string, string>
    body: string
}) {
    const copy = useCopy().exchange
    const entries = Object.entries(headers)
    return (
        <section className="space-y-2">
            <Heading>{title}</Heading>
            {entries.length > 0 && (
                <dl className="grid grid-cols-[minmax(0,14rem)_minmax(0,1fr)] gap-x-4 gap-y-1 text-xs">
                    {entries.map(([name, value]) => (
                        <div key={name} className="contents">
                            <dt className="truncate text-muted-foreground">{name}</dt>
                            <dd className="break-all">
                                <Tabular className="normal-case">{value}</Tabular>
                            </dd>
                        </div>
                    ))}
                </dl>
            )}
            {body ? (
                <pre className="max-h-96 overflow-auto rounded-lg bg-code-bg p-5 font-mono text-xs leading-relaxed break-all whitespace-pre-wrap text-code-fg">
                    {readable(body)}
                </pre>
            ) : (
                <Muted className="block text-xs">{copy.noBody}</Muted>
            )}
        </section>
    )
}

export function ExchangeDetail({ exchange }: { exchange: ExchangeView }) {
    const copy = useCopy().exchange
    return (
        <div className="space-y-6">
            <dl className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-6 gap-y-1 text-sm">
                <dt className="text-muted-foreground">{copy.status}</dt>
                <dd>
                    <Tabular>{exchange.status}</Tabular>
                    {exchange.durationMs !== undefined && (
                        <Muted className="text-xs"> {copy.duration(exchange.durationMs)}</Muted>
                    )}
                </dd>
                {exchange.receivedAt && (
                    <>
                        <dt className="text-muted-foreground">{copy.received}</dt>
                        <dd>
                            <Time value={exchange.receivedAt} />
                        </dd>
                    </>
                )}
                {exchange.remoteAddress && (
                    <>
                        <dt className="text-muted-foreground">{copy.from}</dt>
                        <dd>
                            <Tabular>{exchange.remoteAddress}</Tabular>
                        </dd>
                    </>
                )}
            </dl>
            <Part
                title={copy.request}
                headers={exchange.requestHeaders}
                body={exchange.requestBody}
            />
            <Part
                title={copy.response}
                headers={exchange.responseHeaders}
                body={exchange.responseBody}
            />
        </div>
    )
}
