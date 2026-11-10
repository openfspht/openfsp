import { useEffect, useState } from "react"
import { useSearchParams } from "react-router"

const SEARCH_DELAY_MS = 300

export function useListParams<F extends string>(filters: readonly F[]) {
  const [params, setParams] = useSearchParams()
  const page = Math.max(0, Number(params.get("page") ?? 0) || 0)
  const values = Object.fromEntries(filters.map((name) => [name, params.get(name) ?? ""])) as Record<F, string>
  const update = (changes: Partial<Record<F | "page", string>>) =>
    setParams(
      (current) => {
        const next = new URLSearchParams(current)
        Object.entries(changes).forEach(([key, value]) => (value ? next.set(key, value as string) : next.delete(key)))
        if (!("page" in changes)) next.delete("page")
        return next
      },
      { replace: true }
    )
  return { page, values, setFilter: (name: F, value: string) => update({ [name]: value } as Partial<Record<F | "page", string>>), setPage: (next: number) => update({ page: next > 0 ? String(next) : "" } as Partial<Record<F | "page", string>>) }
}

export function useDebounced(value: string): string {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), SEARCH_DELAY_MS)
    return () => clearTimeout(timer)
  }, [value])
  return debounced
}

export const PAGE_SIZE = 12

export function paginate<T>(items: T[], page: number) {
  const totalPages = Math.max(1, Math.ceil(items.length / PAGE_SIZE))
  const current = Math.min(page, totalPages - 1)
  return { rows: items.slice(current * PAGE_SIZE, (current + 1) * PAGE_SIZE), page: current, totalPages, totalElements: items.length }
}
