import { useQuery } from "@tanstack/react-query"
import { RequestJournal } from "@workspace/design-system"
import type { SimulatedOperator } from "@workspace/types"

import { LIVE, queryKeys } from "@/api/query-keys"
import { simulatorControl } from "@/api/simulator-control"

export function JournalSection({ operator }: { operator: SimulatedOperator }) {
  const journal = useQuery({
    queryKey: queryKeys.journal(operator),
    queryFn: () => simulatorControl.journal(operator),
    refetchInterval: LIVE,
  })
  return <RequestJournal operator={operator} entries={journal.data?.data ?? []} />
}
