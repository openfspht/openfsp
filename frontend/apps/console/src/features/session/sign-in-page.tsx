import { useQuery } from "@tanstack/react-query"
import { LanguageSwitch, ThemeSwitch } from "@workspace/design-system"
import { Navigate, useSearchParams } from "react-router"

import { adminSession } from "@/api/admin-session"
import { queryKeys } from "@/api/query-keys"

import { safeNext } from "./next-path"
import { SignInCard } from "./sign-in-card"
import { useSession } from "./use-session"

export function SignInPage() {
    const session = useSession()
    const [params] = useSearchParams()
    const published = useQuery({
        queryKey: queryKeys.publishedToken,
        queryFn: adminSession.publishedToken,
        retry: false,
        staleTime: Infinity,
    })
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
                publishedToken={published.data}
            />
        </div>
    )
}
