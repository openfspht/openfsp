import { post, remove, request } from "@workspace/http"

const SESSION = "/admin/session"
const PUBLISHED_TOKEN = `${SESSION}/published-token`

export const adminSession = {
    current: () => request<void>(SESSION).then(() => true),
    signIn: (token: string) => request<void>(SESSION, post({ token })),
    signOut: () => request<void>(SESSION, remove()),
    publishedToken: () =>
        request<{ token: string }>(PUBLISHED_TOKEN).then((published) => published.token),
}
