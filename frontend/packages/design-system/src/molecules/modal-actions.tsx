import { Button } from "../atoms"
import { useCopy } from "../copy"

interface ModalActionsProps {
    onCancel: () => void
    submitLabel: React.ReactNode
    disabled?: boolean
    destructive?: boolean
}

export function ModalActions({ onCancel, submitLabel, disabled, destructive }: ModalActionsProps) {
    const copy = useCopy().modal
    return (
        <div className="flex justify-end gap-2">
            <Button type="button" variant="ghost" onClick={onCancel}>
                {copy.cancel}
            </Button>
            <Button
                type="submit"
                variant={destructive ? "destructive" : "default"}
                disabled={disabled}
            >
                {submitLabel}
            </Button>
        </div>
    )
}
