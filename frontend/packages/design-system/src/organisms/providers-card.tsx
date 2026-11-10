import type { ProviderSummary } from "@workspace/types"

import { Badge, Card, CardContent, CardDescription, CardHeader, CardTitle, EmptyText, Mono } from "../atoms"

export function ProvidersCard({ providers }: { providers: ProviderSummary[] }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="font-heading">Providers</CardTitle>
        <CardDescription>The operators this gateway serves and the capabilities each one announces.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        {providers.length === 0 && <EmptyText>No provider is configured.</EmptyText>}
        {providers.map((provider) => (
          <div key={provider.id} className="flex items-start gap-3 border-b pb-3 last:border-b-0">
            {provider.logo_url ? (
              <img src={provider.logo_url} alt="" className="size-8 shrink-0 object-contain" />
            ) : (
              <div className="size-8 shrink-0 bg-muted" />
            )}
            <div className="min-w-0 space-y-1">
              <div className="flex items-baseline gap-2">
                <span className="text-sm font-medium">{provider.display_name}</span>
                <Mono className="text-muted-foreground normal-case">{provider.id}</Mono>
              </div>
              <div className="flex flex-wrap gap-1">
                {provider.capabilities.map((capability) => (
                  <Badge key={capability} variant="outline">
                    {capability}
                  </Badge>
                ))}
              </div>
            </div>
          </div>
        ))}
      </CardContent>
    </Card>
  )
}
