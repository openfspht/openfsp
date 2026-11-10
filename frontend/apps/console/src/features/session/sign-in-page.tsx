import { SignInCard } from "@workspace/design-system"
import { Navigate, useSearchParams } from "react-router"

import { LanguageSwitch } from "@/features/shell/language-switch"

import { useSession } from "./use-session"

const HOME = "/activity"

const safeNext = (value: string | null) => (value && value.startsWith("/") && !value.startsWith("//") ? value : HOME)

export function SignInPage() {
  const session = useSession()
  const [params] = useSearchParams()
  if (session.pending) return null
  if (session.signedIn) return <Navigate replace to={safeNext(params.get("next"))} />
  return (
    <div className="relative">
      <div className="absolute top-4 right-4 z-10">
        <LanguageSwitch />
      </div>
      <SignInCard onSubmit={session.signIn.mutate} busy={session.signIn.isPending} rejected={session.rejected} failed={session.failed} />
    </div>
  )
}
