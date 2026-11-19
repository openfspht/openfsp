import { post, remove, request } from "@workspace/http"

const SESSION = "/admin/session"

export const adminSession = {
    current: () => request<void>(SESSION).then(() => true),
    signIn: (token: string) => request<void>(SESSION, post({ token })),
    signOut: () => request<void>(SESSION, remove()),
}
