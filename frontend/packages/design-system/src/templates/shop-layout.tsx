interface ShopLayoutProps {
  header: React.ReactNode
  children: React.ReactNode
}

export function ShopLayout({ header, children }: ShopLayoutProps) {
  return (
    <div className="min-h-svh bg-background">
      {header}
      <main className="mx-auto max-w-5xl space-y-8 px-6 py-8">{children}</main>
    </div>
  )
}
