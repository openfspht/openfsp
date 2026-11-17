import {
    Button,
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
    ErrorText,
    LabeledInput,
    Logo,
} from "@workspace/design-system"
import { useState } from "react"

import { useI18n } from "@/i18n/i18n-provider"

interface SignInCardProps {
    onSubmit: (token: string) => void
    busy?: boolean
    rejected?: boolean
    failed?: boolean
}

export function SignInCard({ onSubmit, busy, rejected, failed }: SignInCardProps) {
    const [token, setToken] = useState("")
    const copy = useI18n().t.signIn
    return (
        <div className="flex min-h-svh items-center justify-center bg-background px-4">
            <Card className="w-full max-w-md">
                <CardHeader>
                    <Logo className="mb-4 size-16" />
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
                        <LabeledInput
                            id="admin-token"
                            label={copy.token}
                            type="password"
                            autoComplete="current-password"
                            value={token}
                            onChange={(event) => setToken(event.target.value)}
                        />
                        {rejected && <ErrorText role="alert">{copy.rejected}</ErrorText>}
                        {failed && <ErrorText role="alert">{copy.failed}</ErrorText>}
                        <Button type="submit" className="w-full" disabled={busy || !token}>
                            {copy.submit}
                        </Button>
                    </form>
                </CardContent>
            </Card>
        </div>
    )
}
