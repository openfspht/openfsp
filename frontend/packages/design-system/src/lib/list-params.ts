import type { SortState } from "../molecules"
import { useEffect, useState } from "react"
import { useSearchParams } from "react-router"

const SEARCH_DELAY_MS = 300
const NEWEST_FIRST: SortState = { key: "", ascending: false }

export const PAGE_SIZE = 12

type ListChanges<F extends string> = Partial<Record<F | "page" | "sort" | "order", string>>

export function readListParams<F extends string>(
    params: URLSearchParams,
    filters: readonly F[],
    defaultSort: SortState = NEWEST_FIRST
) {
    const page = Math.max(0, Number(params.get("page") ?? 0) || 0)
    const values = Object.fromEntries(
        filters.map((name) => [name, params.get(name) ?? ""])
    ) as Record<F, string>
    const sortKey = params.get("sort")
    const sort: SortState = sortKey
        ? { key: sortKey, ascending: params.get("order") === "asc" }
        : defaultSort
    const sortParams: Record<string, string> = sort.key
        ? { sort: sort.key, order: sort.ascending ? "asc" : "desc" }
        : {}
    return { page, values, sort, sortParams }
}

export function applyListChanges<F extends string>(
    current: URLSearchParams,
    changes: ListChanges<F>
): URLSearchParams {
    const next = new URLSearchParams(current)
    Object.entries(changes).forEach(([key, value]) =>
        value ? next.set(key, value as string) : next.delete(key)
    )
    if (!("page" in changes)) {
        next.delete("page")
    }
    return next
}

export function useListParams<F extends string>(
    filters: readonly F[],
    defaultSort: SortState = NEWEST_FIRST
) {
    const [params, setParams] = useSearchParams()
    const { page, values, sort, sortParams } = readListParams(params, filters, defaultSort)
    const set = (changes: ListChanges<F>) =>
        setParams((current) => applyListChanges(current, changes), { replace: true })
    const toggleSort = (key: string) => {
        const ascending = key === sort.key ? !sort.ascending : true
        set({ sort: key, order: ascending ? "asc" : "desc" } as ListChanges<F>)
    }
    const setPage = (next: number) => set({ page: next > 0 ? String(next) : "" } as ListChanges<F>)
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
