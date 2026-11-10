import { useState } from "react"

import { Button, Card, CardContent, CardDescription, CardHeader, CardTitle, ErrorText, Logo } from "../atoms"
import { LabeledInput } from "../molecules"
import { useCopy } from "../copy"

interface SignInCardProps {
  onSubmit: (token: string) => void
  busy?: boolean
  rejected?: boolean
  failed?: boolean
}

export function SignInCard({ onSubmit, busy, rejected, failed }: SignInCardProps) {
  const [token, setToken] = useState("")
  const copy = useCopy().signIn
  return (
    <div className="flex min-h-svh items-center justify-center bg-background px-4">
      <Card className="w-full max-w-md">
        <CardHeader>
          <Logo className="size-16 mb-4" />
          <CardTitle className="font-heading">{copy.title}</CardTitle>
          <CardDescription>{copy.description}</CardDescription>
        </CardHeader>
        <CardContent>
          <form
            className="space-y-4"
            onSubmit={(event) => {
              event.preventDefault()
              onSubmit(token)
            }}
          >
            <LabeledInput id="admin-token" label={copy.token} type="password" autoComplete="current-password" value={token} onChange={(event) => setToken(event.target.value)} />
            {rejected && <ErrorText>{copy.rejected}</ErrorText>}
            {failed && <ErrorText>{copy.failed}</ErrorText>}
            <Button type="submit" className="w-full" disabled={busy || !token}>
              {copy.submit}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
