import { Button } from "../atoms"

interface ToggleChipsProps<T extends string> {
  label: string
  options: readonly T[]
  value: readonly T[]
  onChange: (value: T[]) => void
  locked?: readonly T[]
}

export function ToggleChips<T extends string>({ label, options, value, onChange, locked = [] }: ToggleChipsProps<T>) {
  const toggle = (option: T) => onChange(value.includes(option) ? value.filter((it) => it !== option) : [...value, option])
  return (
    <fieldset className="space-y-2">
      <legend className="text-sm font-medium">{label}</legend>
      <div className="flex flex-wrap gap-2">
        {options.map((option) => (
          <Button
            key={option}
            type="button"
            size="xs"
            className="normal-case tracking-normal"
            variant={value.includes(option) ? "default" : "outline"}
            aria-pressed={value.includes(option)}
            disabled={locked.includes(option)}
            onClick={() => toggle(option)}
          >
            {option}
          </Button>
        ))}
      </div>
    </fieldset>
  )
}
