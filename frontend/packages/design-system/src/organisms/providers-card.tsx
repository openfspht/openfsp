import type { ProviderSummary } from "@workspace/types"

import { Card, CardContent, CardDescription, CardHeader, CardTitle, EmptyText, Tabular } from "../atoms"
import { useCopy } from "../copy"

export function ProvidersCard({ providers, headless }: { providers: ProviderSummary[]; headless?: boolean }) {
  const copy = useCopy().providers
  const withLogos = providers.some((provider) => provider.logo_url)
  return (
    <Card>
      {!headless && (
        <CardHeader>
          <CardTitle className="font-heading">{copy.title}</CardTitle>
          <CardDescription>{copy.description}</CardDescription>
        </CardHeader>
      )}
      <CardContent className="space-y-3">
        {providers.length === 0 && <EmptyText>{copy.empty}</EmptyText>}
        {providers.map((provider) => (
          <div key={provider.id} className="flex items-start gap-3 border-b pb-3 last:border-b-0">
            {withLogos && (
              <span className="flex h-10 w-28 shrink-0 items-center">
                {provider.logo_url && <img src={provider.logo_url} alt="" className="max-h-10 max-w-full object-contain object-left" />}
              </span>
            )}
            <div className="min-w-0 space-y-1">
              <div className="flex items-baseline gap-2">
                <span className="text-sm font-medium">{provider.display_name}</span>
                <Tabular className="text-muted-foreground normal-case">{provider.id}</Tabular>
              </div>
              <Tabular className="block text-muted-foreground normal-case">{provider.capabilities.join(" · ")}</Tabular>
            </div>
          </div>
        ))}
      </CardContent>
    </Card>
  )
}
