interface ConsoleLayoutProps {
  header: React.ReactNode
  main: React.ReactNode
  aside: React.ReactNode
}

export function ConsoleLayout({ header, main, aside }: ConsoleLayoutProps) {
  return (
    <div className="min-h-svh bg-background">
      {header}
      <main className="mx-auto grid max-w-7xl gap-6 px-6 py-6 lg:grid-cols-[minmax(0,1fr)_26rem]">
        <div className="min-w-0 space-y-6">{main}</div>
        <aside className="space-y-6">{aside}</aside>
      </main>
    </div>
  )
}
