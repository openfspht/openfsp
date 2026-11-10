import { Input, Label } from "../atoms"

interface LabeledInputProps extends React.ComponentProps<"input"> {
  label: string
  hint?: string
}

export function LabeledInput({ label, hint, id, ...props }: LabeledInputProps) {
  return (
    <div className="space-y-2">
      <Label htmlFor={id}>{label}</Label>
      <Input id={id} {...props} />
      {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
    </div>
  )
}
