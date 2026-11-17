import type { SortState } from "../molecules"
import { useEffect, useState } from "react"
import { useSearchParams } from "react-router"

const SEARCH_DELAY_MS = 300
const NEWEST_FIRST: SortState = { key: "", ascending: false }

export const PAGE_SIZE = 12

export function useListParams<F extends string>(
    filters: readonly F[],
    defaultSort: SortState = NEWEST_FIRST
) {
    const [params, setParams] = useSearchParams()
    const page = Math.max(0, Number(params.get("page") ?? 0) || 0)
    const values = Object.fromEntries(
        filters.map((name) => [name, params.get(name) ?? ""])
    ) as Record<F, string>
    const sortKey = params.get("sort")
    const sort: SortState = sortKey
        ? { key: sortKey, ascending: params.get("order") === "asc" }
        : defaultSort
    type Changes = Partial<Record<F | "page" | "sort" | "order", string>>
    const set = (changes: Changes) =>
        setParams(
            (current) => {
                const next = new URLSearchParams(current)
                Object.entries(changes).forEach(([key, value]) =>
                    value ? next.set(key, value as string) : next.delete(key)
                )
                if (!("page" in changes)) {
                    next.delete("page")
                }
                return next
            },
            { replace: true }
        )
    const toggleSort = (key: string) => {
        const ascending = key === sort.key ? !sort.ascending : true
        set({ sort: key, order: ascending ? "asc" : "desc" } as Changes)
    }
    const setPage = (next: number) => set({ page: next > 0 ? String(next) : "" } as Changes)
    const sortParams: Record<string, string> = sort.key
        ? { sort: sort.key, order: sort.ascending ? "asc" : "desc" }
        : {}
    return { page, values, sort, set, toggleSort, setPage, sortParams }
}

export function useDebounced(value: string): string {
    const [debounced, setDebounced] = useState(value)
    useEffect(() => {
        const timer = setTimeout(() => setDebounced(value), SEARCH_DELAY_MS)
        return () => clearTimeout(timer)
    }, [value])
    return debounced
}
