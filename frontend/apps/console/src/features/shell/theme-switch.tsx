import { Button, useCopy } from "@workspace/design-system"
import { Moon, Sun } from "lucide-react"

import { useTheme } from "@/components/theme-provider"

const isDark = () => document.documentElement.classList.contains("dark")

export function ThemeSwitch() {
  const copy = useCopy().header
  const { theme, setTheme } = useTheme()
  const dark = theme === "dark" || (theme === "system" && isDark())
  const label = dark ? copy.light : copy.dark
  return (
    <Button size="icon-sm" variant="outline" aria-label={label} title={label} onClick={() => setTheme(dark ? "light" : "dark")}>
      {dark ? <Sun /> : <Moon />}
    </Button>
  )
}
