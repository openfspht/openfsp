import { useQuery } from "@tanstack/react-query"
import { Button, Muted, PageHeader } from "@workspace/design-system"
import { VALIDATOR_TARGETS, type ValidatorTarget } from "@workspace/types"
import { useState } from "react"

import { queryKeys, validatorApi } from "@/api/validator-api"
import { useI18n } from "@/i18n/i18n-provider"

import { RunForm } from "./run-form"
import { SimulatorConfigCard } from "./simulator-config-card"

const DEFAULT_PREFIX = "ofsp_test_"

export function HomePage() {
  const { t } = useI18n()
  const [target, setTarget] = useState<ValidatorTarget>("gateway")
  const suite = useQuery({
    queryKey: queryKeys.suite,
    queryFn: validatorApi.suite,
  })
  const simulator = useQuery({
    queryKey: queryKeys.simulatorConfig,
    queryFn: validatorApi.simulatorConfig,
  })
  const targets = suite.data?.targets ?? VALIDATOR_TARGETS
  return (
    <>
      <PageHeader title={t.home.title} description={t.home.description} />
      <div className="space-y-6">
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
          <Muted className="block text-sm">{t.home.targetHints[target]}</Muted>
        </fieldset>
        {target === "gateway" && (
          <SimulatorConfigCard
            config={simulator.data}
            failed={simulator.isError}
          />
        )}
        <RunForm
          target={target}
          keyPrefix={simulator.data?.key_prefix ?? DEFAULT_PREFIX}
          suite={suite.data}
        />
      </div>
    </>
  )
}
