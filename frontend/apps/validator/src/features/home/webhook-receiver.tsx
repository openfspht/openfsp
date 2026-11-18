import { CopyButton, Muted, Tabular } from "@workspace/design-system"

import { useI18n } from "@/i18n/i18n-provider"

export function WebhookReceiver({ url }: { url: string }) {
    const { t } = useI18n()
    return (
        <div className="space-y-1 text-sm">
            <span className="block text-muted-foreground">{t.prepare.webhookUrl}</span>
            <span className="flex items-center gap-2">
                <Tabular className="min-w-0 break-all">{url}</Tabular>
                <CopyButton text={url} size="xs" />
            </span>
            <Muted className="block text-xs">{t.prepare.webhookHint}</Muted>
        </div>
    )
}
