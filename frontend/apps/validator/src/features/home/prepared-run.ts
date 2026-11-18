import type { RunPrepared, ValidatorTarget } from "@workspace/types"

const STORAGE_KEY = "openfsp.validator.prepared-run"

export function usablePreparedRun(
    prepared: RunPrepared | undefined,
    target: ValidatorTarget,
    now: number = Date.now()
): RunPrepared | undefined {
    if (!prepared || prepared.target !== target) {
        return undefined
    }
    const deadline = Date.parse(prepared.start_before)
    return Number.isNaN(deadline) || deadline <= now ? undefined : prepared
}

export function loadPreparedRun(): RunPrepared | undefined {
    try {
        const stored = sessionStorage.getItem(STORAGE_KEY)
        return stored ? (JSON.parse(stored) as RunPrepared) : undefined
    } catch {
        return undefined
    }
}

export function savePreparedRun(prepared: RunPrepared | undefined) {
    try {
        if (prepared) {
            sessionStorage.setItem(STORAGE_KEY, JSON.stringify(prepared))
        } else {
            sessionStorage.removeItem(STORAGE_KEY)
        }
    } catch {
        return
    }
}
