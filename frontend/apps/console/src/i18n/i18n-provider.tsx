import { LocaleProvider, useLocale } from "@workspace/design-system"

import { en } from "./en"
import { fr } from "./fr"

const catalogs = { en, fr } as const

export function I18nProvider({ children }: { children: React.ReactNode }) {
  return (
    <LocaleProvider storageKey="openfsp.console.locale">
      {children}
    </LocaleProvider>
  )
}

export function useI18n() {
  const { locale, setLocale } = useLocale()
  return { locale, setLocale, t: catalogs[locale] }
}
