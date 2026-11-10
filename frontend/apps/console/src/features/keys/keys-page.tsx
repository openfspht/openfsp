import { PageHeader } from "@workspace/design-system"

import { useI18n } from "@/i18n/i18n-provider"

import { KeysSection } from "./keys-section"

export function KeysPage() {
  const { t } = useI18n()
  return (
    <div className="max-w-3xl">
      <PageHeader title={t.keys.title} description={t.keys.description} />
      <KeysSection />
    </div>
  )
}
