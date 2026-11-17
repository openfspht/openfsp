"use client"

import { Dialog as DialogPrimitive } from "@base-ui/react/dialog"
import { cn } from "cn"

const Dialog = DialogPrimitive.Root

function DialogPopup({ className, children, ...props }: DialogPrimitive.Popup.Props) {
    return (
        <DialogPrimitive.Portal>
            <DialogPrimitive.Backdrop
                data-slot="dialog-backdrop"
                className="fixed inset-0 z-40 bg-black/40 transition-opacity duration-150 data-[ending-style]:opacity-0 data-[starting-style]:opacity-0"
            />
            <DialogPrimitive.Popup
                data-slot="dialog-popup"
                className={cn(
                    "fixed top-1/2 left-1/2 z-50 max-h-[calc(100svh-2rem)] w-[min(36rem,calc(100vw-2rem))] -translate-x-1/2 -translate-y-1/2 overflow-y-auto bg-background p-8 text-[0.9375rem] text-foreground shadow-lg ring-1 ring-foreground/10 transition-[opacity,scale] duration-150 outline-none data-[ending-style]:scale-[0.98] data-[ending-style]:opacity-0 data-[starting-style]:scale-[0.98] data-[starting-style]:opacity-0",
                    className
                )}
                {...props}
            >
                {children}
            </DialogPrimitive.Popup>
        </DialogPrimitive.Portal>
    )
}

function DialogTitle({ className, ...props }: DialogPrimitive.Title.Props) {
    return (
        <DialogPrimitive.Title
            data-slot="dialog-title"
            className={cn("font-heading text-lg font-semibold tracking-wider uppercase", className)}
            {...props}
        />
    )
}

function DialogDescription({ className, ...props }: DialogPrimitive.Description.Props) {
    return (
        <DialogPrimitive.Description
            data-slot="dialog-description"
            className={cn("leading-relaxed text-muted-foreground", className)}
            {...props}
        />
    )
}

const DialogClose = DialogPrimitive.Close

export { Dialog, DialogClose, DialogDescription, DialogPopup, DialogTitle }
