export const HOME = "/activity"

const CONSOLE_ORIGIN = "https://console.invalid"

export function safeNext(value: string | null): string {
    if (!value?.startsWith("/")) {
        return HOME
    }
    const target = new URL(value, CONSOLE_ORIGIN)
    if (target.origin !== CONSOLE_ORIGIN) {
        return HOME
    }
    return `${target.pathname}${target.search}${target.hash}`
}
