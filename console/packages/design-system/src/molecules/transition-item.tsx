import type { Transition } from "@workspace/types"

import { Muted, Time } from "../atoms"

export function TransitionItem({ transition }: { transition: Transition }) {
  return (
    <li className="flex items-center justify-between gap-2 text-sm">
      <span>
        {transition.from_status && `${transition.from_status} → `}
        <span className="font-medium">{transition.to_status}</span>
      </span>
      <Muted className="text-xs">
        {transition.source} · <Time value={transition.recorded_at} />
      </Muted>
    </li>
  )
}
