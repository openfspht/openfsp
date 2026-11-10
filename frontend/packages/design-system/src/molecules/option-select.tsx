import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@workspace/ui/components/select"

interface OptionSelectProps<T extends string> {
  value: T
  options: readonly T[]
  onChange: (value: T) => void
  labelOf?: (value: T) => string
}

export function OptionSelect<T extends string>({ value, options, onChange, labelOf = (option) => option }: OptionSelectProps<T>) {
  return (
    <Select value={value} onValueChange={(next) => next && onChange(next as T)}>
      <SelectTrigger className="w-56">
        <SelectValue>{(selected: T) => labelOf(selected)}</SelectValue>
      </SelectTrigger>
      <SelectContent>
        {options.map((option) => (
          <SelectItem key={option} value={option}>
            {labelOf(option)}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
