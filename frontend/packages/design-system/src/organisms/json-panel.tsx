import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "../atoms"
import { CopyButton } from "../molecules"

const TOKEN = /("(?:\\.|[^"\\])*")(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?/g

const tones = {
  key: "text-accent",
  string: "text-emerald-300",
  number: "text-sky-300",
  literal: "text-violet-300",
}

function highlight(text: string): React.ReactNode[] {
  const parts: React.ReactNode[] = []
  let last = 0
  for (const match of text.matchAll(TOKEN)) {
    const index = match.index ?? 0
    if (index > last) parts.push(text.slice(last, index))
    const [token, quoted, colon, literal] = match
    const tone = quoted ? (colon ? tones.key : tones.string) : literal ? tones.literal : tones.number
    parts.push(
      <span key={index} className={tone}>
        {quoted ?? token}
      </span>
    )
    if (colon) parts.push(colon)
    last = index + token.length
  }
  if (last < text.length) parts.push(text.slice(last))
  return parts
}

interface JsonPanelProps {
  title: string
  description?: string
  value: unknown
}

export function JsonPanel({ title, description, value }: JsonPanelProps) {
  const text = value === undefined ? "" : JSON.stringify(value, null, 2)
  return (
    <Card>
      <CardHeader>
        <CardTitle className="font-heading">{title}</CardTitle>
        {description && <CardDescription>{description}</CardDescription>}
        <CardAction>
          <CopyButton text={text} size="xs" />
        </CardAction>
      </CardHeader>
      <CardContent>
        <pre className="max-h-[36rem] overflow-auto bg-black p-5 text-sm leading-relaxed whitespace-pre text-neutral-200 tabular-nums">{highlight(text)}</pre>
      </CardContent>
    </Card>
  )
}
