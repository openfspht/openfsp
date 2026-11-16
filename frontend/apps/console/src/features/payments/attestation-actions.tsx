import {
  ActionError,
  Button,
  LabeledInput,
  useCopy,
} from "@workspace/design-system"
import type { AttestationRequest } from "@workspace/types"
import { useState } from "react"

import { useI18n } from "@/i18n/i18n-provider"

export type AttestableStatus = "succeeded" | "failed" | "expired"

const actions: {
  status: AttestableStatus
  variant: "default" | "outline" | "destructive"
}[] = [
  { status: "succeeded", variant: "default" },
  { status: "failed", variant: "destructive" },
  { status: "expired", variant: "outline" },
]

export function AttestationActions({
  disabled,
  onAttest,
}: {
  disabled?: boolean
  onAttest: (status: AttestableStatus) => void
}) {
  const copy = useI18n().t
  return (
    <div className="flex flex-wrap gap-2">
      {actions.map(({ status, variant }) => (
        <Button
          key={status}
          size="sm"
          variant={variant}
          disabled={disabled}
          onClick={() => onAttest(status)}
        >
          {copy.attestation[status]}
        </Button>
      ))}
    </div>
  )
}

interface AttestationFormProps {
  status: AttestableStatus
  onAttest: (attestation: AttestationRequest) => void
  onCancel: () => void
  busy?: boolean
  error?: Error | null
}

export function AttestationForm({
  status,
  onAttest,
  onCancel,
  busy,
  error,
}: AttestationFormProps) {
  const copy = useI18n().t.attestation
  const cancel = useCopy().modal.cancel
  const [person, setPerson] = useState("")
  const [evidence, setEvidence] = useState("")
  const complete = person.trim() !== "" && evidence.trim() !== ""
  return (
    <form
      className="space-y-5"
      onSubmit={(event) => {
        event.preventDefault()
        if (complete)
          onAttest({ status, person: person.trim(), evidence: evidence.trim() })
      }}
    >
      <LabeledInput
        id="attestation-person"
        label={copy.person}
        hint={copy.personHint}
        autoComplete="name"
        required
        value={person}
        onChange={(event) => setPerson(event.target.value)}
      />
      <LabeledInput
        id="attestation-evidence"
        label={copy.evidence}
        hint={copy.evidenceHint}
        required
        value={evidence}
        onChange={(event) => setEvidence(event.target.value)}
      />
      <ActionError error={error} />
      <div className="flex justify-end gap-2">
        <Button type="button" variant="ghost" onClick={onCancel}>
          {cancel}
        </Button>
        <Button
          type="submit"
          variant={status === "succeeded" ? "default" : "destructive"}
          disabled={busy || !complete}
        >
          {copy[status]}
        </Button>
      </div>
    </form>
  )
}
