import { Badge, Logo, Muted } from "../atoms"

export function AppHeader({ title, environment }: { title: string; environment: string }) {
  return (
    <header className="border-b">
      <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-4">
        <div className="flex items-center gap-3">
          <Logo />
          <span className="font-heading text-lg font-bold tracking-tight">openfsp</span>
          <Muted className="text-sm">{title}</Muted>
        </div>
        <Badge variant="outline">{environment} environment</Badge>
      </div>
    </header>
  )
}
