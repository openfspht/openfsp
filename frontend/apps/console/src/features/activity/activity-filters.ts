import type { ActivityItem, ActivityKind, TransitionSource } from "@workspace/types"

export const ACTIVITY_FILTERS = ["all", "creations", "callbacks", "lookups", "statements", "attestations", "deliveries", "access"] as const

export type ActivityFilter = (typeof ACTIVITY_FILTERS)[number]

interface FilterRule {
  kinds: readonly ActivityKind[]
  sources?: readonly TransitionSource[]
}

const ALL_KINDS: readonly ActivityKind[] = ["transition", "conflict", "delivery", "statement", "key", "admin"]

const rules: Record<ActivityFilter, FilterRule> = {
  all: { kinds: ALL_KINDS },
  creations: { kinds: ["transition"], sources: ["creation"] },
  callbacks: { kinds: ["transition"], sources: ["signed_callback", "per_payment_url"] },
  lookups: { kinds: ["transition"], sources: ["lookup"] },
  statements: { kinds: ["transition", "statement"], sources: ["statement"] },
  attestations: { kinds: ["transition"], sources: ["attestation"] },
  deliveries: { kinds: ["delivery"] },
  access: { kinds: ["key", "admin"] },
}

export const kindsOf = (filter: ActivityFilter) => rules[filter].kinds

export function matches(filter: ActivityFilter, item: ActivityItem): boolean {
  const { sources } = rules[filter]
  if (!sources || item.kind !== "transition") return true
  return sources.includes(item.source as TransitionSource)
}

export const isActivityFilter = (value: string | null): value is ActivityFilter =>
  value !== null && (ACTIVITY_FILTERS as readonly string[]).includes(value)
