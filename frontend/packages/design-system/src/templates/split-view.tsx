interface SplitViewProps {
  main: React.ReactNode
  aside?: React.ReactNode
}

export function SplitView({ main, aside }: SplitViewProps) {
  return (
    <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_27rem]">
      <div className="min-w-0 space-y-8">{main}</div>
      {aside && <aside className="space-y-8 lg:sticky lg:top-32 lg:self-start">{aside}</aside>}
    </div>
  )
}
