import { useEffect, useState } from "react"

import { Button } from "../atoms"
import { useCopy } from "../copy"

const COPIED_FOR_MS = 2000

export function CopyButton({ text, size = "sm" }: { text: string; size?: "xs" | "sm" }) {
  const copy = useCopy().clipboard
  const [copied, setCopied] = useState(false)
  useEffect(() => {
    if (!copied) return
    const timer = setTimeout(() => setCopied(false), COPIED_FOR_MS)
    return () => clearTimeout(timer)
  }, [copied])
  return (
    <Button size={size} variant="outline" disabled={!text} onClick={() => void navigator.clipboard.writeText(text).then(() => setCopied(true), () => setCopied(false))}>
      {copied ? copy.copied : copy.copy}
    </Button>
  )
}
