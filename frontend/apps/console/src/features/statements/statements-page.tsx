import { PageHeader } from "@workspace/design-system"

import { useI18n } from "@/i18n/i18n-provider"

import { GatewayStatementSection } from "./gateway-statement-section"

export function StatementsPage() {
  const { t } = useI18n()
  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader title={t.statements.title} description={t.statements.description} />
      <GatewayStatementSection />
    </div>
  )
}
