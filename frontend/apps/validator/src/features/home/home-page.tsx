import { useMutation, useQuery } from "@tanstack/react-query"
import {
    Button,
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
    ErrorText,
    Muted,
    PageHeader,
} from "@workspace/design-system"
import { VALIDATOR_TARGETS, type RunPrepared, type ValidatorTarget } from "@workspace/types"
import { RefreshCw, Server } from "lucide-react"
import { useState } from "react"

import { ProblemError, queryKeys, validatorApi } from "@/api/validator-api"
import { useI18n } from "@/i18n/i18n-provider"

import { loadPreparedRun, savePreparedRun, usablePreparedRun } from "./prepared-run"
import { RunForm } from "./run-form"
import { SimulatorConfigCard } from "./simulator-config-card"

const DEFAULT_PREFIX = "ofsp_test_"
const NOT_FOUND = 404

const isGone = (error: unknown) =>
    error instanceof ProblemError && error.problem.status === NOT_FOUND

export function HomePage() {
    const { t, locale } = useI18n()
    const copy = t.prepare
    const [target, setTarget] = useState<ValidatorTarget>("server")
    const [stored, setStored] = useState<RunPrepared | undefined>(loadPreparedRun)
    const [expired, setExpired] = useState(false)
    const suite = useQuery({
        queryKey: queryKeys.suite,
        queryFn: validatorApi.suite,
    })
    const usable = usablePreparedRun(stored, target)
    const remember = (run: RunPrepared | undefined) => {
        savePreparedRun(run)
        setStored(run)
    }
    const check = useQuery({
        queryKey: queryKeys.simulatorConfig(usable?.id ?? ""),
        queryFn: async () => {
            try {
                return await validatorApi.simulatorConfig(usable!.id)
            } catch (error) {
                if (isGone(error)) {
                    savePreparedRun(undefined)
                }
                throw error
            }
        },
        enabled: usable !== undefined,
        retry: false,
    })
    const gone = isGone(check.error)
    const prepared = gone ? undefined : usable

    const prepare = useMutation({
        mutationFn: () => validatorApi.prepare({ target }),
        onSuccess: (run) => {
            setExpired(false)
            remember(run)
        },
    })
    const prepareProblem = prepare.error instanceof ProblemError ? prepare.error.problem : undefined
    const targets = suite.data?.targets ?? VALIDATOR_TARGETS
    const deadline = prepared
        ? new Date(prepared.simulator.start_before).toLocaleTimeString(locale, {
              hour: "2-digit",
              minute: "2-digit",
          })
        : undefined
    return (
        <>
            <PageHeader
                title={t.home.title}
                description={t.home.description(suite.data?.concurrency)}
            />
            <div className="space-y-6">
                {targets.length > 1 && (
                    <fieldset className="space-y-2">
                        <legend className="text-sm font-medium">{t.home.target}</legend>
                        <div className="flex flex-wrap gap-2" role="radiogroup">
                            {targets.map((option) => (
                                <Button
                                    key={option}
                                    type="button"
                                    variant={option === target ? "default" : "outline"}
                                    role="radio"
                                    aria-checked={option === target}
                                    onClick={() => setTarget(option)}
                                >
                                    {t.home.targets[option]}
                                </Button>
                            ))}
                        </div>
                    </fieldset>
                )}
                <Card>
                    <CardHeader>
                        <CardTitle className="font-heading">{copy.title}</CardTitle>
                        <CardDescription>{copy.description}</CardDescription>
                    </CardHeader>
                    <CardContent className="space-y-3">
                        {(expired || gone) && !prepared && <ErrorText>{copy.expired}</ErrorText>}
                        {prepare.isError && (
                            <ErrorText>
                                {prepareProblem?.detail ?? prepareProblem?.title ?? t.unreachable}
                            </ErrorText>
                        )}
                        {deadline && (
                            <Muted className="block text-sm">{copy.startBefore(deadline)}</Muted>
                        )}
                        <Button
                            type="button"
                            variant="outline"
                            disabled={prepare.isPending}
                            onClick={() => prepare.mutate()}
                        >
                            {prepared ? <RefreshCw /> : <Server />}
                            {prepare.isPending
                                ? copy.preparing
                                : prepared
                                  ? copy.again
                                  : copy.action}
                        </Button>
                    </CardContent>
                </Card>
                {prepared && <SimulatorConfigCard config={prepared.simulator} />}
                <RunForm
                    target={target}
                    keyPrefix={suite.data?.key_prefix ?? DEFAULT_PREFIX}
                    suite={suite.data}
                    prepared={prepared}
                    onStarted={() => remember(undefined)}
                    onExpired={() => {
                        remember(undefined)
                        setExpired(true)
                    }}
                />
            </div>
        </>
    )
}
