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
  return { page, values, setFilters: (changes: Partial<Record<F, string>>) => update(changes as Partial<Record<F | "page", string>>), setFilter: (name: F, value: string) => update({ [name]: value } as Partial<Record<F | "page", string>>), setPage: (next: number) => update({ page: next > 0 ? String(next) : "" } as Partial<Record<F | "page", string>>) }
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

export interface Sort {
  key: string
  ascending: boolean
}

export function sortOf(values: { sort?: string; order?: string }, defaultKey: string, defaultAscending = false): Sort {
  if (!values.sort) return { key: defaultKey, ascending: defaultAscending }
  return { key: values.sort, ascending: values.order === "asc" }
}

export function nextSort(current: Sort, key: string): { sort: string; order: string } {
  const ascending = key === current.key ? !current.ascending : true
  return { sort: key, order: ascending ? "asc" : "desc" }
}

const collator = new Intl.Collator(undefined, { numeric: true, sensitivity: "base" })

export function sortRows<T>(items: T[], sort: Sort, accessors: Record<string, (item: T) => string | number>): T[] {
  const accessor = accessors[sort.key]
  if (!accessor) return items
  const sign = sort.ascending ? 1 : -1
  return [...items].sort((left, right) => {
    const a = accessor(left)
    const b = accessor(right)
    return sign * (typeof a === "number" && typeof b === "number" ? a - b : collator.compare(String(a), String(b)))
  })
}
