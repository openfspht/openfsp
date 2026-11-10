import { useQuery } from "@tanstack/react-query"
import { ConsoleNavBar, ConsoleShell, FilterChips, navLinkClass } from "@workspace/design-system"
import { NavLink, Outlet, useNavigate } from "react-router"

import { gatewayAdmin } from "@/api/gateway-admin"
import { queryKeys } from "@/api/query-keys"
import { useSession } from "@/features/session/use-session"
import { useI18n, type Locale } from "@/i18n/i18n-provider"

const SECTIONS = ["activity", "payments", "providers", "statements", "keys", "webhooks", "audit"] as const
const LOCALES: readonly Locale[] = ["en", "fr"]

export function ConsoleFrame() {
  const { t, locale, setLocale } = useI18n()
  const navigate = useNavigate()
  const session = useSession()
  const overview = useQuery({ queryKey: queryKeys.overview, queryFn: gatewayAdmin.overview })
  const signOut = () => session.signOut.mutate(undefined, { onSettled: () => navigate("/sign-in", { replace: true }) })
  return (
    <ConsoleShell
      navigation={
        <ConsoleNavBar
          environment={overview.data?.environment ?? "test"}
          onSignOut={signOut}
          tools={
            <FilterChips label={t.language.label} value={locale} onChange={setLocale} chips={LOCALES.map((value) => ({ value, label: t.language[value] }))} />
          }
          navigation={SECTIONS.map((section) => (
            <NavLink key={section} to={`/${section}`} className={({ isActive }) => navLinkClass(isActive)}>
              {t.nav[section]}
            </NavLink>
          ))}
        />
      }
    >
      <Outlet />
    </ConsoleShell>
  )
}
