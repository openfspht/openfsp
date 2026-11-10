import { formatMoney, useCopy } from "@workspace/design-system"
import type { Money } from "@workspace/types"

const EDGES = [
  "M0 0H400V6L388 9 371 5 352 11 330 6 309 10 291 4 270 9 248 5 229 12 205 6 186 10 163 4 142 9 120 6 99 12 77 5 58 10 36 6 17 11 0 6Z",
  "M0 0H400V8L383 4 366 10 344 6 327 12 303 5 285 9 262 4 244 11 221 6 200 10 178 5 157 12 136 6 113 9 95 4 72 11 51 6 30 10 12 5 0 9Z",
]

export function TornEdge({ flip, variant = 0, className = "" }: { flip?: boolean; variant?: number; className?: string }) {
  return (
    <svg viewBox="0 0 400 14" preserveAspectRatio="none" className={`torn ${className}`} style={flip ? { transform: "scaleY(-1)" } : undefined} aria-hidden="true">
      <path d={EDGES[variant % EDGES.length]} fill="currentColor" />
    </svg>
  )
}

export function Price({ value, className = "" }: { value: Money; className?: string }) {
  const { locale } = useCopy()
  return <span className={`price ${className}`}>{formatMoney(value, locale)}</span>
}

export const STATIONS = ["cart", "pay", "approve", "done"] as const
export type Station = (typeof STATIONS)[number]

export function Stations({ current, labels }: { current: Station; labels: Record<Station, string> }) {
  const reached = STATIONS.indexOf(current)
  return (
    <ol className="stations" aria-label={labels[current]}>
      {STATIONS.map((station, index) => (
        <li key={station} data-state={index < reached ? "done" : index === reached ? "current" : "next"} aria-current={index === reached ? "step" : undefined}>
          <span className="stations__dot" aria-hidden="true">
            {index + 1}
          </span>
          {labels[station]}
        </li>
      ))}
    </ol>
  )
}
