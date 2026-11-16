import { QueryCache, QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { ThemeProvider } from "@workspace/design-system"
import { useState } from "react"

import { UnauthenticatedError } from "@/api/http"
import { queryKeys } from "@/api/query-keys"
import { I18nProvider } from "@/i18n/i18n-provider"

export function Providers({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(() => {
    const client: QueryClient = new QueryClient({
      queryCache: new QueryCache({
        onError: (error, query) => {
          if (error instanceof UnauthenticatedError && query.queryHash !== JSON.stringify(queryKeys.session))
            void client.invalidateQueries({ queryKey: queryKeys.session })
        },
      }),
      defaultOptions: {
        queries: {
          retry: (count, error) => !(error instanceof UnauthenticatedError) && count < 3,
        },
      },
    })
    return client
  })
  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <I18nProvider>{children}</I18nProvider>
      </ThemeProvider>
    </QueryClientProvider>
  )
}
