import { useState } from "react"

import { Button, Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "../atoms"
import { useCopy } from "../copy"

const COPIED_FOR_MS = 2000

interface JsonPanelProps {
  title: string
  description?: string
  value: unknown
}

export function JsonPanel({ title, description, value }: JsonPanelProps) {
  const copy = useCopy().json
  const [copied, setCopied] = useState(false)
  const text = value === undefined ? "" : JSON.stringify(value, null, 2)
  return (
    <Card>
      <CardHeader>
        <CardTitle className="font-heading">{title}</CardTitle>
        {description && <CardDescription>{description}</CardDescription>}
        <CardAction>
          <Button
            size="xs"
            variant="outline"
            disabled={!text}
            onClick={() =>
              void navigator.clipboard.writeText(text).then(() => {
                setCopied(true)
                setTimeout(() => setCopied(false), COPIED_FOR_MS)
              })
            }
          >
            {copied ? copy.copied : copy.copy}
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent>
        <pre className="max-h-[32rem] overflow-auto bg-muted/50 p-4 text-sm leading-relaxed whitespace-pre-wrap break-all">{text}</pre>
      </CardContent>
    </Card>
  )
}
