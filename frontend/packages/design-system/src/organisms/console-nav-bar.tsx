import { LogOut } from "lucide-react"

import { Badge, Button, Logo } from "../atoms"
import { useCopy } from "../copy"

interface ConsoleNavBarProps {
  environment: string
  navigation: React.ReactNode
  tools?: React.ReactNode
  onSignOut: () => void
}

export function ConsoleNavBar({ environment, navigation, tools, onSignOut }: ConsoleNavBarProps) {
  const copy = useCopy().header
  return (
    <header className="sticky top-0 z-20 border-b bg-background">
      <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-x-6 px-4 sm:px-6">
        <div className="flex min-w-0 items-center gap-3 py-3">
          <Logo />
          <span className="font-heading text-lg font-bold tracking-tight">openfsp</span>
          <Badge variant={environment === LIVE ? "destructive" : "outline"}>
            <span className="sm:hidden">{environment}</span>
            <span className="hidden sm:inline">{copy.environment(environment)}</span>
          </Badge>
        </div>
        <nav className="-mb-px order-last -ml-3 flex w-full min-w-0 gap-1 overflow-x-auto">{navigation}</nav>
        <div className="ml-auto flex shrink-0 items-center gap-2 py-3">
          {tools}
          <Button size="sm" variant="outline" onClick={onSignOut} aria-label={copy.signOut}>
            <LogOut />
            <span className="hidden sm:inline">{copy.signOut}</span>
          </Button>
        </div>
      </div>
    </header>
  )
}

const LIVE = "live"

export const navLinkClass = (active: boolean) =>
  [
    "inline-flex items-center gap-2 border-b-2 px-3 py-4 text-sm font-medium whitespace-nowrap transition-colors",
    active ? "border-accent text-foreground" : "border-transparent text-muted-foreground hover:text-foreground",
  ].join(" ")
