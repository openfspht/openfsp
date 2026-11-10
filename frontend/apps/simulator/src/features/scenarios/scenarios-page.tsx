import { useMutation, useQueries, useQueryClient } from "@tanstack/react-query"
import { ErrorText, PageHeader, ScenariosCard } from "@workspace/design-system"
import type { Scenario } from "@workspace/types"

import { queryKeys } from "@/api/query-keys"
import { simulatorControl } from "@/api/simulator-control"
import { useOperators } from "@/features/shell/use-operators"
import { useI18n } from "@/i18n/i18n-provider"

export function ScenariosPage() {
  const { t } = useI18n()
  const queryClient = useQueryClient()
  const { operators, nameOf, pick } = useOperators()
  const scenarios = useQueries({
    queries: operators.map((operator) => ({
      queryKey: queryKeys.defaultScenario(operator),
      queryFn: () => simulatorControl.defaultScenario(operator),
    })),
  })
  const declare = useMutation({
    mutationFn: ({
      operator,
      scenario,
    }: {
      operator: string
      scenario: Scenario
    }) => simulatorControl.setDefaultScenario(pick(operator), scenario),
    onSuccess: (_, { operator }) =>
      queryClient.invalidateQueries({
        queryKey: queryKeys.defaultScenario(pick(operator)),
      }),
  })
  return (
    <>
      <PageHeader
        title={t.scenarios.title}
        description={t.scenarios.description}
      />
      {scenarios.some((query) => query.isError) && (
        <ErrorText className="mb-4">{t.unreachable}</ErrorText>
      )}
      <ScenariosCard
        operators={operators.map((operator, index) => ({
          operator,
          name: nameOf(operator),
          scenario: scenarios[index].data?.scenario,
        }))}
        onChange={(operator, scenario) =>
          declare.mutate({ operator, scenario })
        }
      />
    </>
  )
}
