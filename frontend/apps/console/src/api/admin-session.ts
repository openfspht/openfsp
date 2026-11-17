import { post, remove, request } from "./http"

const SESSION = "/gateway/admin/session"

export const adminSession = {
    current: () => request<void>(SESSION).then(() => true),
    signIn: (token: string) => request<void>(SESSION, post({ token })),
    signOut: () => request<void>(SESSION, remove()),
}
