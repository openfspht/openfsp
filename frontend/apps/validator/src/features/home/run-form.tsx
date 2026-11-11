import { useMutation } from "@tanstack/react-query"
import {
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  ErrorText,
  LabeledInput,
} from "@workspace/design-system"
import type {
  RunRequest,
  SuiteDescription,
  ValidatorTarget,
} from "@workspace/types"
import { Play } from "lucide-react"
import { useState } from "react"
import { useNavigate } from "react-router"

import { ProblemError, validatorApi } from "@/api/validator-api"
import { useI18n } from "@/i18n/i18n-provider"

const BUSY = 409

interface RunFormProps {
  target: ValidatorTarget
  keyPrefix: string
  suite?: SuiteDescription
}

export function RunForm({ target, keyPrefix, suite }: RunFormProps) {
  const { t } = useI18n()
  const copy = t.form
  const navigate = useNavigate()
  const [baseUrl, setBaseUrl] = useState("")
  const [apiKey, setApiKey] = useState("")
  const [restrictedKey, setRestrictedKey] = useState("")
  const [derivationKey, setDerivationKey] = useState("")
  const [inputs, setInputs] = useState<Record<string, string>>({})
  const extraInputs = [
    ...new Set(suite?.assisted.flatMap((scenario) => scenario.inputs) ?? []),
  ].filter((name) => name !== suite?.restricted_key_input)

  const start = useMutation({
    mutationFn: (request: RunRequest) => validatorApi.start(request),
    onSuccess: (created) => navigate(`/runs/${created.id}`),
  })

  const submit = (event: React.FormEvent) => {
    event.preventDefault()
    const provided = Object.fromEntries(
      Object.entries(inputs).filter(([, value]) => value.trim() !== "")
    )
    start.mutate({
      target,
      base_url: baseUrl.trim(),
      api_key: apiKey.trim(),
      restricted_key: restrictedKey.trim() || undefined,
      derivation_key:
        target === "gateway" ? derivationKey.trim() || undefined : undefined,
      inputs: provided,
    })
  }

  const problem =
    start.error instanceof ProblemError ? start.error.problem : undefined
  return (
    <Card>
      <CardHeader>
        <CardTitle className="font-heading">{copy.title}</CardTitle>
      </CardHeader>
      <CardContent>
        <form className="max-w-2xl space-y-5" onSubmit={submit}>
          <LabeledInput
            id="base-url"
            label={copy.baseUrl}
            type="url"
            required
            placeholder={copy.baseUrlPlaceholder}
            value={baseUrl}
            onChange={(event) => setBaseUrl(event.target.value)}
          />
          <LabeledInput
            id="api-key"
            label={copy.apiKey}
            hint={copy.apiKeyHint(keyPrefix)}
            required
            autoComplete="off"
            spellCheck={false}
            value={apiKey}
            onChange={(event) => setApiKey(event.target.value)}
          />
          <LabeledInput
            id="restricted-key"
            label={copy.restrictedKey}
            hint={copy.restrictedKeyHint}
            autoComplete="off"
            spellCheck={false}
            value={restrictedKey}
            onChange={(event) => setRestrictedKey(event.target.value)}
          />
          {target === "gateway" && (
            <LabeledInput
              id="derivation-key"
              label={copy.derivationKey}
              hint={copy.derivationKeyHint}
              autoComplete="off"
              spellCheck={false}
              value={derivationKey}
              onChange={(event) => setDerivationKey(event.target.value)}
            />
          )}
          {extraInputs.map((name) => (
            <LabeledInput
              key={name}
              id={`input-${name}`}
              label={copy.input(name)}
              hint={
                suite?.assisted.find((scenario) =>
                  scenario.inputs.includes(name)
                )?.action
              }
              autoComplete="off"
              value={inputs[name] ?? ""}
              onChange={(event) =>
                setInputs({ ...inputs, [name]: event.target.value })
              }
            />
          ))}
          {start.isError && (
            <div className="space-y-1">
              <ErrorText>
                {problem?.status === BUSY
                  ? copy.busy
                  : (problem?.detail ?? problem?.title ?? copy.refused)}
              </ErrorText>
              {problem?.errors?.map((error) => (
                <ErrorText key={error.pointer} className="text-xs">
                  {error.pointer}: {error.detail}
                </ErrorText>
              ))}
            </div>
          )}
          <Button type="submit" disabled={start.isPending}>
            <Play />
            {start.isPending ? copy.submitting : copy.submit}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}
