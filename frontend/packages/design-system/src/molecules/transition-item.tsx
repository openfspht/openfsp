import type { Transition } from "@workspace/types"

import { Muted, Time } from "../atoms"
import { useCopy } from "../copy"

export function TransitionItem({ transition }: { transition: Transition }) {
  const copy = useCopy()
  return (
    <li className="flex items-center justify-between gap-2 text-sm">
      <span>
        {transition.from_status && `${copy.status[transition.from_status]} → `}
        <span className="font-medium">{copy.status[transition.to_status]}</span>
      </span>
      <Muted className="text-xs">
        {copy.source[transition.source]} · <Time value={transition.recorded_at} />
      </Muted>
    </li>
  )
}
