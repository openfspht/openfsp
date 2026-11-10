import { useMutation, useQueryClient } from "@tanstack/react-query"
import {
  Button,
  ConfirmModal,
  ConsoleNavBar,
  ConsoleShell,
  LanguageSwitch,
  navLinkClass,
  ThemeSwitch,
  useCopy,
} from "@workspace/design-system"
import { RotateCcw } from "lucide-react"
import { useState } from "react"
import { NavLink, Outlet } from "react-router"

import { simulatorControl } from "@/api/simulator-control"
import { useI18n } from "@/i18n/i18n-provider"

const SECTIONS = ["activity", "requests"] as const

export function SimulatorFrame() {
  const { t } = useI18n()
  const copy = useCopy().simulator.reset
  const queryClient = useQueryClient()
  const [confirming, setConfirming] = useState(false)
  const reset = useMutation({
    mutationFn: simulatorControl.reset,
    onSuccess: () => {
      setConfirming(false)
      return queryClient.invalidateQueries()
    },
  })
  return (
    <ConsoleShell
      navigation={
        <ConsoleNavBar
          environment="test"
          product={t.product}
          tools={
            <>
              <LanguageSwitch />
              <ThemeSwitch />
              <Button
                size="sm"
                variant="outline"
                aria-label={copy.label}
                onClick={() => setConfirming(true)}
              >
                <RotateCcw />
                <span className="hidden sm:inline">{copy.label}</span>
              </Button>
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
      <ConfirmModal
        open={confirming}
        onClose={() => setConfirming(false)}
        onConfirm={() => reset.mutate()}
        title={copy.title}
        description={copy.description}
        confirmLabel={copy.confirm}
        destructive
        busy={reset.isPending}
      />
    </ConsoleShell>
  )
}
