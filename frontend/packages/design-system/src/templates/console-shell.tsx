interface ConsoleShellProps {
    navigation: React.ReactNode
    children: React.ReactNode
}

export function ConsoleShell({ navigation, children }: ConsoleShellProps) {
    return (
        <div className="min-h-svh bg-background">
            {navigation}
            <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:py-10">{children}</main>
        </div>
    )
}
