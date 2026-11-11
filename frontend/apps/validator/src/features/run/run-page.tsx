import { useQuery } from "@tanstack/react-query"
import { Button, ErrorText, Muted, PageHeader } from "@workspace/design-system"
import { useParams } from "react-router"
import { Link } from "react-router"

import { queryKeys, validatorApi } from "@/api/validator-api"
import { useI18n } from "@/i18n/i18n-provider"

import { ResultsTable } from "./results-table"
import { doneCount, isTerminal, limitsOf } from "./run-state"
import { LimitsCard, RunSummary } from "./run-summary"
import { useRunEvents } from "./use-run-events"

export function RunPage() {
  const { id = "" } = useParams()
  return <RunView key={id} id={id} />
}

function RunView({ id }: { id: string }) {
  const { t } = useI18n()
  const { state, interrupted } = useRunEvents(id)
  const suite = useQuery({
    queryKey: queryKeys.suite,
    queryFn: validatorApi.suite,
  })
  const total = state.run?.total ?? 0
  const finished = isTerminal(state)
  return (
    <>
      <PageHeader
        title={t.run.title}
        description={state.run?.base_url}
        actions={
          state.summary && (
            <Button
              render={<Link to={`/reports/${state.summary.report_id}`} />}
            >
              {t.run.report}
            </Button>
          )
        }
      />
      <div className="space-y-6">
        <RunSummary
          core={state.summary?.core}
          profiles={state.summary?.profiles}
          claim={state.summary?.claim}
        >
          {state.run
            ? t.run.progress(doneCount(state), total)
            : t.run.connecting}
        </RunSummary>
        {state.failure && (
          <ErrorText>
            {t.run.failed}: {state.failure.detail ?? state.failure.title}
          </ErrorText>
        )}
        {interrupted && !finished && (
          <Muted className="block text-sm">{t.run.lost}</Muted>
        )}
        <ResultsTable
          rows={Object.values(state.rows)}
          specUrl={suite.data?.spec_url}
        />
        <LimitsCard limits={limitsOf(state)} specUrl={suite.data?.spec_url} />
      </div>
    </>
  )
}
