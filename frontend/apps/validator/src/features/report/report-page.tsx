import { useQuery } from "@tanstack/react-query"
import {
  Button,
  Card,
  CardContent,
  EmptyText,
  ErrorText,
  Field,
  PageHeader,
  Tabular,
  Time,
} from "@workspace/design-system"
import { VALIDATOR_TARGETS, type ValidatorTarget } from "@workspace/types"
import { Download } from "lucide-react"
import { useParams } from "react-router"

import {
  ProblemError,
  queryKeys,
  reportUrl,
  validatorApi,
} from "@/api/validator-api"
import { AttestationAction } from "@/features/attestation/attestation-action"
import { ResultsTable } from "@/features/run/results-table"
import { rowsOfReport } from "@/features/run/run-state"
import { LimitsCard, RunSummary } from "@/features/run/run-summary"
import { useI18n } from "@/i18n/i18n-provider"

const isTarget = (value: string): value is ValidatorTarget =>
  (VALIDATOR_TARGETS as readonly string[]).includes(value)

export function ReportPage() {
  const { id = "" } = useParams()
  const { t } = useI18n()
  const report = useQuery({
    queryKey: queryKeys.report(id),
    queryFn: () => validatorApi.report(id),
    retry: false,
  })
  const suite = useQuery({
    queryKey: queryKeys.suite,
    queryFn: validatorApi.suite,
  })
  const data = report.data
  const targetLabel = (target: string) =>
    isTarget(target) ? t.home.targets[target] : target
  const missing =
    report.error instanceof ProblemError && report.error.problem.status === 404
  return (
    <>
      <PageHeader
        title={t.report.title}
        description={
          data ? String(data.configuration.base_url ?? "") : undefined
        }
        actions={
          data && (
            <>
              <AttestationAction runId={id} />
              <Button
                variant="outline"
                render={
                  <a
                    href={reportUrl(id)}
                    download={`openfsp-conformance-report-${id}.json`}
                  />
                }
              >
                <Download />
                {t.report.download}
              </Button>
            </>
          )
        }
      />
      {report.isPending && <EmptyText>{t.report.loading}</EmptyText>}
      {missing && <ErrorText>{t.report.notFound}</ErrorText>}
      {report.isError && !missing && (
        <ErrorText>
          {report.error instanceof ProblemError
            ? (report.error.problem.detail ?? report.error.problem.title)
            : t.unreachable}
        </ErrorText>
      )}
      {data && (
        <div className="space-y-6">
          <RunSummary
            core={data.core}
            profiles={data.profiles}
            claim={data.claim}
          />
          <Card>
            <CardContent>
              <dl className="grid gap-2 md:grid-cols-2">
                <Field label={t.report.target}>
                  {targetLabel(data.claim.target)}
                </Field>
                <Field label={t.report.date}>
                  <Time value={data.date} />{" "}
                  <Tabular className="text-muted-foreground">
                    {t.report.duration(data.duration_ms)}
                  </Tabular>
                </Field>
                <Field label={t.report.suite}>
                  <Tabular>
                    {data.suite.version} / {data.suite.protocol_version}
                  </Tabular>
                </Field>
                <Field label={t.report.protocol}>
                  <Tabular>
                    {data.target_protocol_version ?? t.report.unknownProtocol}
                  </Tabular>
                </Field>
              </dl>
            </CardContent>
          </Card>
          <ResultsTable
            rows={rowsOfReport(data)}
            specUrl={suite.data?.spec_url}
          />
          <LimitsCard limits={data.limits} specUrl={suite.data?.spec_url} />
        </div>
      )}
    </>
  )
}
