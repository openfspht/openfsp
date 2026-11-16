import { useCopy, useTheme } from "@workspace/design-system"
import { Moon, ShoppingBasket, Sun } from "lucide-react"
import { NavLink, Outlet, ScrollRestoration } from "react-router"

import { useCart } from "@/features/cart/cart"
import { useI18n } from "@/i18n/i18n-provider"

import { TornEdge } from "@/ui/paper"

const isDark = () => document.documentElement.classList.contains("dark")

export function ShopFrame() {
  const { t, locale, setLocale } = useI18n()
  const header = useCopy().header
  const language = useCopy().language
  const { theme, setTheme } = useTheme()
  const { count } = useCart()
  const dark = theme === "dark" || (theme === "system" && isDark())
  const next = locale === "en" ? "fr" : "en"
  return (
    <div className="shop">
      <header className="masthead">
        <div className="shop-wrap masthead__row">
          <NavLink to="/" className="masthead__brand display">
            {t.shop}
          </NavLink>
          <nav className="masthead__nav" aria-label={t.nav.shop}>
            <NavLink to="/" end>
              {t.nav.shop}
            </NavLink>
            <NavLink to="/orders">{t.nav.orders}</NavLink>
          </nav>
          <div className="masthead__tools">
            <button type="button" className="chip" aria-label={language.switchTo[next]} title={language.switchTo[next]} onClick={() => setLocale(next)}>
              {language[next]}
            </button>
            <button type="button" className="chip" aria-label={dark ? header.light : header.dark} title={dark ? header.light : header.dark} onClick={() => setTheme(dark ? "light" : "dark")}>
              {dark ? <Sun size={16} /> : <Moon size={16} />}
            </button>
            <NavLink to="/cart" className="btn btn--small masthead__cart" aria-label={`${t.nav.cart}, ${t.cart.count(count)}`}>
              <ShoppingBasket size={18} aria-hidden="true" />
              <span>{t.nav.cart}</span>
              {count > 0 && <span className="masthead__count">{count}</span>}
            </NavLink>
          </div>
        </div>
        <TornEdge className="masthead__edge" />
      </header>
      <main>
        <Outlet />
      </main>
      <footer className="shop-wrap colophon">
        <p className="display colophon__name">{t.shop}</p>
        <p className="soft">{t.tagline}</p>
        <p className="soft colophon__demo">{t.demo}</p>
      </footer>
      <ScrollRestoration />
    </div>
  )
}
