import { cn } from "@workspace/ui/lib/utils"

import { Muted } from "../atoms"

interface StatProps {
  value: React.ReactNode
  label: string
  tone?: "default" | "pending" | "critical"
}

const tones = {
  default: "text-foreground",
  pending: "text-accent-foreground",
  critical: "text-destructive",
}

export function Stat({ value, label, tone = "default" }: StatProps) {
  return (
    <div className="min-w-28">
      <span className={cn("block font-heading text-3xl font-bold tabular-nums", tones[tone])}>{value}</span>
      <Muted className="text-sm">{label}</Muted>
    </div>
  )
}
