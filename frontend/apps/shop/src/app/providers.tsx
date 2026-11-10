import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { ThemeProvider } from "@workspace/design-system"
import { useState } from "react"

import { I18nProvider } from "@/i18n/i18n-provider"

export function Providers({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(() => new QueryClient())
  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider storageKey="mache-lakou.theme">
        <I18nProvider>{children}</I18nProvider>
      </ThemeProvider>
    </QueryClientProvider>
  )
}
