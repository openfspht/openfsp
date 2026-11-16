# Frontend

Bun workspace of React apps built with Vite and Turbo.

| App | |
|---|---|
| `apps/console` | Gateway console: payments, statements, keys, webhooks |
| `apps/simulator` | Simulator UI: scenarios, simulated payments, journal, statements |
| `apps/validator` | Validator page: run the conformance suite, read attestations |

Shared code is in `packages/`: the design system, UI primitives (shadcn/ui), API types and phone number formatting.

```sh
bun install
bun run dev
bun run typecheck && bun run lint && bun run test && bun run build
```

Add a shadcn/ui component with `bunx shadcn@latest add <name> -c apps/console`; it lands in `packages/ui`.
