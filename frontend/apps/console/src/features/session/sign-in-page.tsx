import { LanguageSwitch, ThemeSwitch } from "@workspace/design-system"
import { Navigate, useSearchParams } from "react-router"

import { safeNext } from "./next-path"
import { SignInCard } from "./sign-in-card"
import { useSession } from "./use-session"

export function SignInPage() {
    const session = useSession()
    const [params] = useSearchParams()
    if (session.pending) {
        return null
    }
    if (session.signedIn) {
        return <Navigate replace to={safeNext(params.get("next"))} />
    }
    return (
        <div className="relative">
            <div className="absolute top-4 right-4 z-10 flex gap-2">
                <LanguageSwitch />
                <ThemeSwitch />
            </div>
            <SignInCard
                onSubmit={session.signIn.mutate}
                busy={session.signIn.isPending}
                rejected={session.rejected}
                failed={session.failed}
            />
        </div>
    )
}
