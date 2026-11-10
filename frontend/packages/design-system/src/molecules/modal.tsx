import { Dialog, DialogDescription, DialogPopup, DialogTitle } from "@workspace/ui/components/dialog"

import { Button } from "../atoms"
import { useCopy } from "../copy"

interface ModalProps {
  open: boolean
  onClose: () => void
  title: string
  description?: string
  children: React.ReactNode
  closable?: boolean
}

export function Modal({ open, onClose, title, description, children, closable = true }: ModalProps) {
  return (
    <Dialog open={open} onOpenChange={(next) => !next && closable && onClose()}>
      <DialogPopup>
        <div className="mb-6 space-y-2">
          <DialogTitle>{title}</DialogTitle>
          {description && <DialogDescription>{description}</DialogDescription>}
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
}

export function ConfirmModal({ open, onClose, onConfirm, title, description, confirmLabel, destructive, busy }: ConfirmModalProps) {
  const copy = useCopy().modal
  return (
    <Modal open={open} onClose={onClose} title={title} description={description}>
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
