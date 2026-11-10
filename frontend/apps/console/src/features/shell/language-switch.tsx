import { FilterChips } from "@workspace/design-system"

import { useI18n, type Locale } from "@/i18n/i18n-provider"

const LOCALES: readonly Locale[] = ["en", "fr"]

export function LanguageSwitch() {
  const { t, locale, setLocale } = useI18n()
  return <FilterChips label={t.language.label} value={locale} onChange={setLocale} chips={LOCALES.map((value) => ({ value, label: t.language[value] }))} />
}
