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
    <div className="min-w-24">
      <span className={cn("block font-heading text-2xl font-bold tabular-nums", tones[tone])}>{value}</span>
      <Muted className="text-xs">{label}</Muted>
    </div>
  )
}
