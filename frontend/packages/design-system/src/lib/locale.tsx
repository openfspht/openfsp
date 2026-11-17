import { createContext, useContext, useEffect, useMemo, useState } from "react"

import { CopyProvider, en, fr } from "../copy"

export type Locale = "en" | "fr"

const LOCALES: readonly Locale[] = ["en", "fr"]
const designCopy = { en, fr } as const

interface LocaleState {
    locale: Locale
    setLocale: (locale: Locale) => void
}

const LocaleContext = createContext<LocaleState | undefined>(undefined)

function initialLocale(storageKey: string): Locale {
    try {
        const stored = localStorage.getItem(storageKey)
        if (LOCALES.includes(stored as Locale)) {
            return stored as Locale
        }
    } catch {
        return "en"
    }
    return navigator.language.toLowerCase().startsWith("fr") ? "fr" : "en"
}

export function LocaleProvider({
    storageKey,
    children,
}: {
    storageKey: string
    children: React.ReactNode
}) {
    const [locale, setLocale] = useState<Locale>(() => initialLocale(storageKey))
    useEffect(() => {
        document.documentElement.lang = locale
        try {
            localStorage.setItem(storageKey, locale)
        } catch {
            return
        }
    }, [locale, storageKey])
    const value = useMemo(() => ({ locale, setLocale }), [locale])
    return (
        <LocaleContext.Provider value={value}>
            <CopyProvider copy={designCopy[locale]}>{children}</CopyProvider>
        </LocaleContext.Provider>
    )
}

export function useLocale(): LocaleState {
    const context = useContext(LocaleContext)
    if (!context) {
        throw new Error("useLocale needs a LocaleProvider")
    }
    return context
}
