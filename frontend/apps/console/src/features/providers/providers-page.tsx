import { PageHeader } from "@workspace/design-system"

import { useProviders } from "@/features/providers/use-providers"
import { useI18n } from "@/i18n/i18n-provider"

import { ProvidersCard } from "./providers-card"

export function ProvidersPage() {
    const { t } = useI18n()
    const { list } = useProviders()
    return (
        <div className="max-w-3xl">
            <PageHeader title={t.providers.title} description={t.providers.description} />
            <ProvidersCard providers={list} />
        </div>
    )
}
