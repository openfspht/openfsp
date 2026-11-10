import { Button } from "../atoms"

export interface FilterChip<T extends string> {
  value: T
  label: string
}

interface FilterChipsProps<T extends string> {
  chips: readonly FilterChip<T>[]
  value: T
  onChange: (value: T) => void
  label: string
}

export function FilterChips<T extends string>({ chips, value, onChange, label }: FilterChipsProps<T>) {
  return (
    <div role="radiogroup" aria-label={label} className="flex flex-wrap gap-2">
      {chips.map((chip) => (
        <Button
          key={chip.value}
          role="radio"
          aria-checked={chip.value === value}
          size="xs"
          variant={chip.value === value ? "default" : "outline"}
          onClick={() => onChange(chip.value)}
        >
          {chip.label}
        </Button>
      ))}
    </div>
  )
}
