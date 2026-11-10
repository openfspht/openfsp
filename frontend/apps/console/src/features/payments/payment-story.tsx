import { PaymentDetail } from "@workspace/design-system"

import { usePayment } from "./use-payment"

export function PaymentStory({ id }: { id: string }) {
  const { detail, attest } = usePayment(id)
  if (!detail) return null
  return <PaymentDetail detail={detail} onAttest={attest.mutate} attesting={attest.isPending} />
}
