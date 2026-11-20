import { createContext, useContext, useEffect, useMemo, useState } from "react"

type Theme = "system" | "light" | "dark"

interface ThemeState {
    theme: Theme
    setTheme: (theme: Theme) => void
}

const STORAGE_KEY = "theme"
const THEMES: readonly Theme[] = ["system", "light", "dark"]
const DARK_QUERY = "(prefers-color-scheme: dark)"

const ThemeContext = createContext<ThemeState | undefined>(undefined)

function storedTheme(): Theme {
    try {
        const stored = localStorage.getItem(STORAGE_KEY)
        return THEMES.includes(stored as Theme) ? (stored as Theme) : "system"
    } catch {
        return "system"
    }
}

function saveTheme(theme: Theme) {
    try {
        localStorage.setItem(STORAGE_KEY, theme)
    } catch {
        return
    }
}

function applyTheme(theme: Theme) {
    const dark = theme === "dark" || (theme === "system" && matchMedia(DARK_QUERY).matches)
    document.documentElement.classList.toggle("dark", dark)
    document.documentElement.classList.toggle("light", !dark)
}

export function ThemeProvider({ children }: { children: React.ReactNode }) {
    const [theme, setTheme] = useState<Theme>(storedTheme)

    useEffect(() => {
        applyTheme(theme)
        saveTheme(theme)
        if (theme !== "system") {
            return
        }
        const query = matchMedia(DARK_QUERY)
        const follow = () => applyTheme("system")
        query.addEventListener("change", follow)
        return () => query.removeEventListener("change", follow)
    }, [theme])

    const value = useMemo(() => ({ theme, setTheme }), [theme])
    return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

export function useTheme(): ThemeState {
    const context = useContext(ThemeContext)
    if (!context) {
        throw new Error("useTheme needs a ThemeProvider")
    }
    return context
}
