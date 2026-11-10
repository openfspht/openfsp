import type { ProviderSummary } from "@workspace/types"

import { Card, CardContent, CardDescription, CardHeader, CardTitle, EmptyText, Mono } from "../atoms"
import { useCopy } from "../copy"

export function ProvidersCard({ providers, headless }: { providers: ProviderSummary[]; headless?: boolean }) {
  const copy = useCopy().providers
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
            {provider.logo_url && <img src={provider.logo_url} alt="" className="size-8 shrink-0 object-contain" />}
            <div className="min-w-0 space-y-1">
              <div className="flex items-baseline gap-2">
                <span className="text-sm font-medium">{provider.display_name}</span>
                <Mono className="text-muted-foreground normal-case">{provider.id}</Mono>
              </div>
              <Mono className="block text-muted-foreground normal-case">{provider.capabilities.join(" · ")}</Mono>
            </div>
          </div>
        ))}
      </CardContent>
    </Card>
  )
}
