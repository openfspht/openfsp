import { Badge, Button, Logo, Muted } from "../atoms"
import { useCopy } from "../copy"

interface AppHeaderProps {
  title: string
  environment: string
  onSignOut?: () => void
}

export function AppHeader({ title, environment, onSignOut }: AppHeaderProps) {
  const copy = useCopy().header
  return (
    <header className="border-b">
      <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-4">
        <div className="flex items-center gap-3">
          <Logo />
          <span className="font-heading text-lg font-bold tracking-tight">openfsp</span>
          <Muted className="text-sm">{title}</Muted>
        </div>
        <div className="flex items-center gap-3">
          <Badge variant="outline">{copy.environment(environment)}</Badge>
          {onSignOut && (
            <Button size="sm" variant="outline" onClick={onSignOut}>
              {copy.signOut}
            </Button>
          )}
        </div>
      </div>
    </header>
  )
}
