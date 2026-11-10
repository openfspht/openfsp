import { Button } from "@workspace/design-system"

import { useI18n } from "@/i18n/i18n-provider"

export function LanguageSwitch() {
  const { t, locale, setLocale } = useI18n()
  const next = locale === "en" ? "fr" : "en"
  return (
    <Button size="sm" variant="outline" aria-label={t.language.switchTo[next]} title={t.language.switchTo[next]} onClick={() => setLocale(next)}>
      {t.language[next]}
    </Button>
  )
}
