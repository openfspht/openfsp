import {
  Card,
  CardContent,
  EmptyText,
  ProviderLogo,
  Tabular,
} from "@workspace/design-system"
import type { ProviderSummary } from "@workspace/types"

import { useI18n } from "@/i18n/i18n-provider"

export function ProvidersCard({ providers }: { providers: ProviderSummary[] }) {
  const copy = useI18n().t.providers
  const withLogos = providers.some((provider) => provider.logo_url)
  return (
    <Card>
      <CardContent className="space-y-3">
        {providers.length === 0 && <EmptyText>{copy.empty}</EmptyText>}
        {providers.map((provider) => (
          <div
            key={provider.id}
            className="flex items-center gap-5 border-b pb-4 last:border-b-0"
          >
            {withLogos && <ProviderLogo src={provider.logo_url} />}
            <div className="min-w-0 space-y-1">
              <div className="flex items-baseline gap-2">
                <span className="text-sm font-medium">
                  {provider.display_name}
                </span>
                <Tabular className="text-muted-foreground normal-case">
                  {provider.id}
                </Tabular>
              </div>
              <Tabular className="block text-muted-foreground normal-case">
                {provider.capabilities.join(", ")}
              </Tabular>
            </div>
          </div>
        ))}
      </CardContent>
    </Card>
  )
}
