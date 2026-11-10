import { Navigate, Outlet, useLocation } from "react-router"

import { useSession } from "./use-session"

export function RequireSession() {
  const session = useSession()
  const location = useLocation()
  if (session.pending) return null
  if (!session.signedIn) {
    const next = encodeURIComponent(location.pathname + location.search)
    return <Navigate replace to={`/sign-in?next=${next}`} />
  }
  return <Outlet />
}
