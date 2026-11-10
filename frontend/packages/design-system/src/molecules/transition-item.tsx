import type { Transition } from "@workspace/types"

import { Muted, Time } from "../atoms"
import { useCopy } from "../copy"

export function TransitionItem({ transition }: { transition: Transition }) {
  const copy = useCopy()
  return (
    <li className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-0.5 text-[0.9375rem]">
      <span>
        {transition.from_status && `${copy.status[transition.from_status]} → `}
        <span className="font-medium">{copy.status[transition.to_status]}</span>
      </span>
      <Muted className="text-sm whitespace-nowrap">
        {copy.source[transition.source]} · <Time value={transition.recorded_at} />
      </Muted>
    </li>
  )
}
