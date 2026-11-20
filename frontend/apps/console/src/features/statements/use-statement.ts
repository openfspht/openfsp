import { useInfiniteQuery, useMutation } from "@tanstack/react-query"
import type { QueryParams } from "@workspace/http"

import { gatewayAdmin } from "@/api/gateway-admin"
import { queryKeys } from "@/api/query-keys"

const PAGE_LIMIT = 50

export function useStatement(filters: QueryParams | undefined) {
    const params = { ...filters, limit: PAGE_LIMIT }
    const pages = useInfiniteQuery({
        queryKey: queryKeys.statements(params),
        queryFn: ({ pageParam }) => gatewayAdmin.statements({ ...params, cursor: pageParam }),
        initialPageParam: "",
        getNextPageParam: (last) => last.next_cursor,
        enabled: filters !== undefined,
    })
    const exportCsv = useMutation({
        mutationFn: ({ filters, filename }: { filters: QueryParams; filename: string }) =>
            gatewayAdmin.exportStatements(filters, filename),
    })
    return {
        entries: filters ? (pages.data?.pages.flatMap((page) => page.data) ?? []) : [],
        loaded: filters !== undefined && pages.isSuccess,
        error: filters ? pages.error : null,
        hasMore: filters !== undefined && pages.hasNextPage,
        loadingMore: pages.isFetchingNextPage,
        loadMore: () => void pages.fetchNextPage(),
        exportCsv,
    }
}
