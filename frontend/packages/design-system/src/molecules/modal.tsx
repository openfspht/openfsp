import { Dialog, DialogDescription, DialogPopup, DialogTitle } from "@workspace/ui/components/dialog"

import { Button } from "../atoms"
import { useCopy } from "../copy"
import { ActionError } from "./action-error"

interface ModalProps {
  open: boolean
  onClose: () => void
  title: string
  description?: string
  children: React.ReactNode
  closable?: boolean
  wide?: boolean
  finalFocus?: React.RefObject<HTMLElement | null>
}

export function Modal({ open, onClose, title, description, children, closable = true, wide, finalFocus }: ModalProps) {
  return (
    <Dialog open={open} onOpenChange={(next) => !next && closable && onClose()}>
      <DialogPopup className={wide ? "w-[min(60rem,calc(100vw-2rem))]" : undefined} finalFocus={finalFocus}>
        <div className="mb-6 space-y-2">
          <DialogTitle className={wide ? "tracking-normal normal-case [overflow-wrap:anywhere]" : "[overflow-wrap:anywhere]"}>{title}</DialogTitle>
          {description && <DialogDescription className="[overflow-wrap:anywhere]">{description}</DialogDescription>}
        </div>
        {children}
      </DialogPopup>
    </Dialog>
  )
}

interface ConfirmModalProps {
  open: boolean
  onClose: () => void
  onConfirm: () => void
  title: string
  description: string
  confirmLabel: string
  destructive?: boolean
  busy?: boolean
  error?: Error | null
}

export function ConfirmModal({ open, onClose, onConfirm, title, description, confirmLabel, destructive, busy, error }: ConfirmModalProps) {
  const copy = useCopy().modal
  return (
    <Modal open={open} onClose={onClose} title={title} description={description}>
      <ActionError error={error} className="mb-4" />
      <div className="flex justify-end gap-2">
        <Button variant="ghost" onClick={onClose}>
          {copy.cancel}
        </Button>
        <Button variant={destructive ? "destructive" : "default"} disabled={busy} onClick={onConfirm}>
          {confirmLabel}
        </Button>
      </div>
    </Modal>
  )
}
