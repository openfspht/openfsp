import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Button, ErrorText } from "@workspace/design-system"
import { BadgeCheck } from "lucide-react"
import { Link, useNavigate } from "react-router"

import { ProblemError, queryKeys, validatorApi } from "@/api/validator-api"
import { useI18n } from "@/i18n/i18n-provider"

export function AttestationAction({ runId }: { runId: string }) {
  const { t } = useI18n()
  const navigate = useNavigate()
  const client = useQueryClient()
  const existing = useQuery({
    queryKey: queryKeys.runAttestation(runId),
    queryFn: () => validatorApi.runAttestation(runId),
    retry: false,
  })
  const issue = useMutation({
    mutationFn: () => validatorApi.issueAttestation(runId),
    onSuccess: (resource) => {
      client.setQueryData(queryKeys.runAttestation(runId), resource)
      client.setQueryData(queryKeys.attestation(resource.id), resource)
      void navigate(`/attestations/${resource.id}`)
    },
  })
  if (existing.data)
    return (
      <Button
        variant="outline"
        render={<Link to={`/attestations/${existing.data.id}`} />}
      >
        <BadgeCheck />
        {t.attestation.open}
      </Button>
    )
  if (existing.isPending) return null
  const problem =
    issue.error instanceof ProblemError ? issue.error.problem : undefined
  return (
    <span className="flex flex-col items-end gap-1">
      <Button
        variant="outline"
        disabled={issue.isPending}
        onClick={() => issue.mutate()}
      >
        <BadgeCheck />
        {issue.isPending ? t.attestation.issuing : t.attestation.issue}
      </Button>
      {issue.isError && (
        <ErrorText>
          {problem ? (problem.detail ?? problem.title) : t.unreachable}
        </ErrorText>
      )}
    </span>
  )
}
