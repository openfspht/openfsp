import { Input } from "../atoms"

export function TableToolbar({ children }: { children: React.ReactNode }) {
  return <div className="mb-4 flex flex-wrap items-center gap-3">{children}</div>
}

interface SearchFieldProps {
  value: string
  onChange: (value: string) => void
  placeholder: string
}

export function SearchField({ value, onChange, placeholder }: SearchFieldProps) {
  return <Input type="search" aria-label={placeholder} placeholder={placeholder} value={value} onChange={(event) => onChange(event.target.value)} className="w-72" />
}
