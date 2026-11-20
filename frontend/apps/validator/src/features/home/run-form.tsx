import { useMutation } from "@tanstack/react-query"
import {
    Button,
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
    ErrorText,
    LabeledInput,
} from "@workspace/design-system"
import type { RunPrepared, RunRequest, SuiteDescription, ValidatorTarget } from "@workspace/types"
import { ChevronDown, Play } from "lucide-react"
import { useState } from "react"
import { useNavigate } from "react-router"

import { problemOf, validatorApi } from "@/api/validator-api"
import { useI18n } from "@/i18n/i18n-provider"
import { useSuiteText } from "@/i18n/suite-text"

const BUSY = 409
const NOT_FOUND = 404
const CREDENTIALS_REQUIRED = "credentials-required"
const ACCESS_CODE_REQUIRED = "access-code-required"

const isKind = (error: unknown, kind: string) =>
    problemOf(error)?.type?.endsWith(`/${kind}`) === true

interface RunFormProps {
    target: ValidatorTarget
    keyPrefix: string
    suite?: SuiteDescription
    prepared?: RunPrepared
    advanced: boolean
    onAdvanced: (open: boolean) => void
    onPrepared: (run: RunPrepared) => void
    onStarted: () => void
    onExpired: () => void
}

export function RunForm({
    target,
    keyPrefix,
    suite,
    prepared,
    advanced,
    onAdvanced,
    onPrepared,
    onStarted,
    onExpired,
}: RunFormProps) {
    const { t } = useI18n()
    const copy = t.form
    const navigate = useNavigate()
    const [baseUrl, setBaseUrl] = useState("")
    const [apiKey, setApiKey] = useState("")
    const [controlEndpoint, setControlEndpoint] = useState("")
    const [accessCode, setAccessCode] = useState("")
    const [restrictedKey, setRestrictedKey] = useState("")
    const [inputs, setInputs] = useState<Record<string, string>>({})
    const extraInputs = [
        ...new Set(suite?.assisted.flatMap((scenario) => scenario.inputs) ?? []),
    ].filter((name) => name !== suite?.restricted_key_input)
    const text = useSuiteText()
    const hint = (name: string) => {
        const scenario = suite?.assisted.find((assisted) => assisted.inputs.includes(name))
        return scenario && text.action(scenario.scenario, scenario.action)
    }

    const start = useMutation({
        mutationFn: async (request: (run: RunPrepared) => RunRequest) => {
            const run = prepared ?? (await validatorApi.prepare({ target }))
            onPrepared(run)
            return validatorApi.start(run.id, request(run))
        },
        onSuccess: (created) => {
            onStarted()
            navigate(`/runs/${created.id}`)
        },
        onError: (error) => {
            if (problemOf(error)?.status === NOT_FOUND) {
                onExpired()
            }
            if (isKind(error, CREDENTIALS_REQUIRED) || isKind(error, ACCESS_CODE_REQUIRED)) {
                onAdvanced(true)
            }
        },
    })

    const submit = (event: React.SubmitEvent<HTMLFormElement>) => {
        event.preventDefault()
        const provided = Object.fromEntries(
            Object.entries(inputs).filter(([, value]) => value.trim() !== "")
        )
        start.mutate(() => ({
            target,
            base_url: baseUrl.trim(),
            api_key: apiKey.trim() || undefined,
            control_endpoint: controlEndpoint.trim() || undefined,
            access_code: accessCode.trim() || undefined,
            restricted_key: restrictedKey.trim() || undefined,
            inputs: provided,
        }))
    }

    const problem = problemOf(start.error)
    const message = () => {
        if (problem?.status === BUSY) {
            return copy.busy
        }
        if (isKind(start.error, CREDENTIALS_REQUIRED)) {
            return copy.needKey
        }
        if (isKind(start.error, ACCESS_CODE_REQUIRED)) {
            return copy.needCode
        }
        return problem?.detail ?? problem?.title ?? copy.refused
    }
    return (
        <Card>
            <CardHeader>
                <CardTitle className="font-heading">{copy.title}</CardTitle>
                <CardDescription>{t.home.targetHints[target]}</CardDescription>
            </CardHeader>
            <CardContent>
                <form className="max-w-2xl space-y-5" onSubmit={submit}>
                    <LabeledInput
                        id="base-url"
                        label={copy.baseUrl}
                        hint={copy.baseUrlHint}
                        type="url"
                        required
                        placeholder={copy.baseUrlPlaceholder}
                        value={baseUrl}
                        onChange={(event) => setBaseUrl(event.target.value)}
                    />
                    {start.isError && (
                        <div className="space-y-1" role="alert">
                            <ErrorText>{message()}</ErrorText>
                            {problem?.errors?.map((error) => (
                                <ErrorText key={error.pointer} className="text-xs">
                                    {error.pointer}: {error.detail}
                                </ErrorText>
                            ))}
                        </div>
                    )}
                    <div>
                        <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            className="-ml-2"
                            aria-expanded={advanced}
                            aria-controls="credentials"
                            onClick={() => onAdvanced(!advanced)}
                        >
                            <ChevronDown
                                className={
                                    advanced
                                        ? "rotate-180 transition-transform"
                                        : "transition-transform"
                                }
                            />
                            {copy.advanced}
                        </Button>
                    </div>
                    <div id="credentials" hidden={!advanced} className="space-y-5">
                        <LabeledInput
                            id="api-key"
                            type="password"
                            label={copy.apiKey}
                            hint={copy.apiKeyHint(keyPrefix)}
                            autoComplete="off"
                            spellCheck={false}
                            value={apiKey}
                            onChange={(event) => setApiKey(event.target.value)}
                        />
                        <LabeledInput
                            id="control-endpoint"
                            type="url"
                            label={copy.controlEndpoint}
                            hint={copy.controlEndpointHint}
                            autoComplete="off"
                            spellCheck={false}
                            required={apiKey.trim() !== ""}
                            value={controlEndpoint}
                            onChange={(event) => setControlEndpoint(event.target.value)}
                        />
                        <LabeledInput
                            id="access-code"
                            type="password"
                            label={copy.accessCode}
                            hint={copy.accessCodeHint}
                            autoComplete="off"
                            spellCheck={false}
                            value={accessCode}
                            onChange={(event) => setAccessCode(event.target.value)}
                        />
                        <LabeledInput
                            id="restricted-key"
                            type="password"
                            label={copy.restrictedKey}
                            hint={copy.restrictedKeyHint}
                            autoComplete="off"
                            spellCheck={false}
                            value={restrictedKey}
                            onChange={(event) => setRestrictedKey(event.target.value)}
                        />
                        {extraInputs.map((name) => (
                            <LabeledInput
                                key={name}
                                id={`input-${name}`}
                                type="password"
                                label={copy.input(name)}
                                hint={hint(name)}
                                autoComplete="off"
                                value={inputs[name] ?? ""}
                                onChange={(event) =>
                                    setInputs({ ...inputs, [name]: event.target.value })
                                }
                            />
                        ))}
                    </div>
                    <div className="flex flex-wrap items-center gap-3">
                        <Button type="submit" disabled={start.isPending}>
                            <Play />
                            {start.isPending ? copy.submitting : copy.submit}
                        </Button>
                    </div>
                </form>
            </CardContent>
        </Card>
    )
}
