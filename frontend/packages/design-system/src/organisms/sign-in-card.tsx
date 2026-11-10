import { useState } from "react"

import { Button, Card, CardContent, CardDescription, CardHeader, CardTitle, ErrorText, Logo } from "../atoms"
import { LabeledInput } from "../molecules"

interface SignInCardProps {
  onSubmit: (token: string) => void
  busy?: boolean
  rejected?: boolean
  failed?: boolean
}

export function SignInCard({ onSubmit, busy, rejected, failed }: SignInCardProps) {
  const [token, setToken] = useState("")
  return (
    <div className="flex min-h-svh items-center justify-center bg-background px-4">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <Logo />
          <CardTitle className="font-heading">Gateway console</CardTitle>
          <CardDescription>Enter the admin token whose hash the gateway holds in OPENFSP_ADMIN_TOKEN.</CardDescription>
        </CardHeader>
        <CardContent>
          <form
            className="space-y-4"
            onSubmit={(event) => {
              event.preventDefault()
              onSubmit(token)
            }}
          >
            <LabeledInput id="admin-token" label="Admin token" type="password" autoComplete="current-password" value={token} onChange={(event) => setToken(event.target.value)} />
            {rejected && <ErrorText>The token does not match.</ErrorText>}
            {failed && <ErrorText>The gateway is not reachable.</ErrorText>}
            <Button type="submit" className="w-full" disabled={busy || !token}>
              Sign in
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
