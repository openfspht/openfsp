import { useQuery } from "@tanstack/react-query"
import { ErrorText, Muted, PageHeader } from "@workspace/design-system"
import { useEffect } from "react"
import { useNavigate, useParams } from "react-router"

import { queryKeys } from "@/api/query-keys"
import { validatorApi } from "@/api/validator-api"
import { useI18n } from "@/i18n/i18n-provider"

import { ReportLayout } from "./report-layout"
import { doneCount, isTerminal, limitsOf } from "./run-state"
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
    const navigate = useNavigate()
    const reportId = state.summary?.report_id
    useEffect(() => {
        if (reportId) {
            navigate(`/reports/${reportId}`, { replace: true })
        }
    }, [reportId, navigate])
    return (
        <>
            <PageHeader title={t.run.title} description={state.run?.base_url} />
            <div className="space-y-6">
                {state.failure && (
                    <ErrorText>
                        {t.run.failed}: {state.failure.detail ?? state.failure.title}
                    </ErrorText>
                )}
                {interrupted && !finished && <Muted className="block text-sm">{t.run.lost}</Muted>}
                <ReportLayout
                    rows={Object.values(state.rows)}
                    core={state.summary?.core}
                    profiles={state.summary?.profiles}
                    claim={state.summary?.claim}
                    limits={limitsOf(state)}
                    specUrl={suite.data?.spec_url}
                    progress={
                        state.run ? t.run.progress(doneCount(state), total) : t.run.connecting
                    }
                />
            </div>
        </>
    )
}
