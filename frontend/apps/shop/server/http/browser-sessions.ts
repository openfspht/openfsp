const COOKIE = "shop_session"

export class BrowserSessions {
  private readonly references = new Map<string, Set<string>>()

  remember(request: Bun.BunRequest, reference: string) {
    const session = request.cookies.get(COOKIE) ?? this.open(request)
    const owned = this.references.get(session) ?? new Set<string>()
    owned.add(reference)
    this.references.set(session, owned)
  }

  owns(request: Bun.BunRequest, reference: string): boolean {
    const session = request.cookies.get(COOKIE)
    return (
      session !== null && this.references.get(session)?.has(reference) === true
    )
  }

  private open(request: Bun.BunRequest): string {
    const session = crypto.randomUUID()
    request.cookies.set(COOKIE, session, {
      httpOnly: true,
      sameSite: "lax",
      path: "/",
    })
    return session
  }
}
