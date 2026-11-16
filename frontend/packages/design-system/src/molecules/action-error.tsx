import { ErrorText } from "../atoms"
import { useCopy } from "../copy"

interface ActionErrorProps {
  error: Error | null | undefined
  className?: string
}

export function ActionError({ error, className }: ActionErrorProps) {
  const copy = useCopy().action
  if (!error) return null
  return (
    <ErrorText role="alert" className={className}>
      {copy.failed} {error.message}
    </ErrorText>
  )
}
