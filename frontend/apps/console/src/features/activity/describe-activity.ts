import type { DesignSystemCopy } from "@workspace/design-system"
import type { ActivityItem, PaymentStatus, TransitionSource } from "@workspace/types"

import type { Messages } from "@/i18n/en"

import type { ActivityRow, ActivityTone } from "./activity-spine"

const SUCCESS_LIMIT = 300

const statusTone: Record<PaymentStatus, ActivityTone> = {
    pending: "pending",
    succeeded: "positive",
    failed: "critical",
    expired: "neutral",
    canceled: "neutral",
}

export function describeActivity(
    item: ActivityItem,
    t: Messages,
    copy: DesignSystemCopy,
    providerNames: Record<string, string>
): ActivityRow {
    const base = { id: item.id, at: item.at, reference: item.payment?.id }
    const payment = item.payment
    const provider = payment ? (providerNames[payment.provider] ?? payment.provider) : ""
    switch (item.kind) {
        case "transition": {
            const to = item.to_status ?? "pending"
            return {
                ...base,
                tag: t.source[item.source as TransitionSource] ?? item.source ?? "",
                tone: statusTone[to],
                title: t.activity.moved(
                    payment?.reference ?? "",
                    provider,
                    item.from_status && copy.status[item.from_status],
                    copy.status[to]
                ),
            }
        }
        case "conflict":
            return {
                ...base,
                tag: t.activity.tags.conflict,
                tone: "critical",
                title: t.activity.conflict(payment?.reference ?? "", item.subject ?? ""),
            }
        case "delivery": {
            const delivered = item.status_code !== undefined && item.status_code < SUCCESS_LIMIT
            return {
                ...base,
                tag: t.activity.tags.delivery,
                tone: delivered ? "positive" : "critical",
                title: t.activity.delivered(
                    payment?.reference ?? "",
                    item.event_type ?? "",
                    item.endpoint ?? ""
                ),
                detail: t.activity.attempt(
                    item.attempt ?? 1,
                    String(item.status_code ?? item.result ?? ""),
                    item.duration_ms ?? 0
                ),
            }
        }
        case "key":
            return {
                ...base,
                tag: t.activity.tags.access,
                tone: "neutral",
                title:
                    item.source === "key_revoked"
                        ? t.activity.keyRevoked(item.subject ?? "")
                        : t.activity.keyIssued(item.subject ?? ""),
            }
        case "admin": {
            if (item.source === "webhook_added" || item.source === "webhook_removed") {
                const name = item.subject ?? ""
                return {
                    ...base,
                    tag: t.activity.tags.access,
                    tone: "neutral",
                    title:
                        item.source === "webhook_added"
                            ? t.activity.webhookAdded(name)
                            : t.activity.webhookRemoved(name),
                }
            }
            const refused = item.source === "admin_sign_in_failure"
            return {
                ...base,
                tag: t.activity.tags.access,
                tone: refused ? "critical" : "neutral",
                title: refused ? t.activity.signInFailed : t.activity.signedIn,
            }
        }
    }
}
