import { Muted } from "../atoms"

interface PageHeaderProps {
    title: string
    description?: string
    actions?: React.ReactNode
}

export function PageHeader({ title, description, actions }: PageHeaderProps) {
    return (
        <header className="mb-8 flex flex-wrap items-end justify-between gap-4">
            <div className="space-y-2">
                <h1 className="font-heading text-3xl font-bold tracking-tight text-balance">
                    {title}
                </h1>
                {description && <Muted className="block text-base">{description}</Muted>}
            </div>
            {actions && <div className="flex flex-wrap items-center gap-3">{actions}</div>}
        </header>
    )
}
