import { CopyProvider, en as designEn, fr as designFr } from "@workspace/design-system"
import { createContext, useContext, useEffect, useMemo, useState } from "react"

import { en, type Messages } from "./en"
import { fr } from "./fr"

export type Locale = "en" | "fr"

const STORAGE_KEY = "openfsp.console.locale"
const catalogs = { en: { messages: en, design: designEn }, fr: { messages: fr, design: designFr } } as const

interface I18n {
  locale: Locale
  setLocale: (locale: Locale) => void
  t: Messages
}

const I18nContext = createContext<I18n | undefined>(undefined)

function initialLocale(): Locale {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored === "en" || stored === "fr") return stored
  } catch {
    return "en"
  }
  return navigator.language.toLowerCase().startsWith("fr") ? "fr" : "en"
}

export function I18nProvider({ children }: { children: React.ReactNode }) {
  const [locale, setLocale] = useState<Locale>(initialLocale)
  useEffect(() => {
    document.documentElement.lang = locale
    try {
      localStorage.setItem(STORAGE_KEY, locale)
    } catch {
      return
    }
  }, [locale])
  const value = useMemo(() => ({ locale, setLocale, t: catalogs[locale].messages }), [locale])
  return (
    <I18nContext.Provider value={value}>
      <CopyProvider copy={catalogs[locale].design}>{children}</CopyProvider>
    </I18nContext.Provider>
  )
}

export function useI18n(): I18n {
  const context = useContext(I18nContext)
  if (!context) throw new Error("useI18n needs an I18nProvider")
  return context
}
