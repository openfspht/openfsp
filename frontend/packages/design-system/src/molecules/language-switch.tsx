import { Button } from "../atoms"
import { useCopy } from "../copy"
import { useLocale } from "../lib/locale"

export function LanguageSwitch() {
  const copy = useCopy().language
  const { locale, setLocale } = useLocale()
  const next = locale === "en" ? "fr" : "en"
  return (
    <Button size="sm" variant="outline" aria-label={copy.switchTo[next]} title={copy.switchTo[next]} onClick={() => setLocale(next)}>
      {copy[next]}
    </Button>
  )
}
