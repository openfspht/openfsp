import { Muted, Time, useCopy } from "@workspace/design-system"
import type { Transition } from "@workspace/types"

import { useI18n } from "@/i18n/i18n-provider"

export function TransitionItem({ transition }: { transition: Transition }) {
  const copy = useCopy()
  const { t } = useI18n()
  return (
    <li className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-0.5 text-[0.9375rem]">
      <span>
        {transition.from_status && `${copy.status[transition.from_status]} → `}
        <span className="font-medium">{copy.status[transition.to_status]}</span>
      </span>
      <Muted className="text-sm whitespace-nowrap">
        <span>{t.source[transition.source]}</span>{" "}
        <Time value={transition.recorded_at} className="ml-3" />
      </Muted>
    </li>
  )
}
