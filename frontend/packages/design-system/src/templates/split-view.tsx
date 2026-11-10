interface SplitViewProps {
  main: React.ReactNode
  aside?: React.ReactNode
}

export function SplitView({ main, aside }: SplitViewProps) {
  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_26rem]">
      <div className="min-w-0 space-y-6">{main}</div>
      {aside && <aside className="space-y-6 lg:sticky lg:top-24 lg:self-start">{aside}</aside>}
    </div>
  )
}
