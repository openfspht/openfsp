import { useQuery } from "@tanstack/react-query"
import {
  ConsoleNavBar,
  ConsoleShell,
  LanguageSwitch,
  navLinkClass,
  ThemeSwitch,
} from "@workspace/design-system"
import { NavLink, Outlet, useNavigate } from "react-router"

import { gatewayAdmin } from "@/api/gateway-admin"
import { queryKeys } from "@/api/query-keys"
import { useSession } from "@/features/session/use-session"
import { useI18n } from "@/i18n/i18n-provider"

import { PaymentSearch } from "./payment-search"

const SECTIONS = [
  "activity",
  "payments",
  "statements",
  "keys",
  "webhooks",
  "audit",
  "providers",
  "configuration",
] as const

export function ConsoleFrame() {
  const { t } = useI18n()
  const navigate = useNavigate()
  const session = useSession()
  const environment = useQuery({
    queryKey: queryKeys.overview,
    queryFn: gatewayAdmin.overview,
    staleTime: Infinity,
    select: (overview) => overview.environment,
  })
  const signOut = () =>
    session.signOut.mutate(undefined, {
      onSettled: () => navigate("/sign-in", { replace: true }),
    })
  return (
    <ConsoleShell
      navigation={
        <ConsoleNavBar
          environment={environment.data ?? "test"}
          onSignOut={signOut}
          tools={
            <>
              <PaymentSearch />
              <LanguageSwitch />
              <ThemeSwitch />
            </>
          }
          navigation={SECTIONS.map((section) => (
            <NavLink
              key={section}
              to={`/${section}`}
              className={({ isActive }) => navLinkClass(isActive)}
            >
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
