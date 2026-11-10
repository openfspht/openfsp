import { Badge, Button, Logo, Muted } from "../atoms"

interface AppHeaderProps {
  title: string
  environment: string
  onSignOut?: () => void
}

export function AppHeader({ title, environment, onSignOut }: AppHeaderProps) {
  return (
    <header className="border-b">
      <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-4">
        <div className="flex items-center gap-3">
          <Logo />
          <span className="font-heading text-lg font-bold tracking-tight">openfsp</span>
          <Muted className="text-sm">{title}</Muted>
        </div>
        <div className="flex items-center gap-3">
          <Badge variant="outline">{environment} environment</Badge>
          {onSignOut && (
            <Button size="sm" variant="outline" onClick={onSignOut}>
              Sign out
            </Button>
          )}
        </div>
      </div>
    </header>
  )
}
