# Frontend

The frontend is a Bun workspace containing React applications built with Vite and Turbo.

| App              | Purpose                                                                  |
| ---------------- | ------------------------------------------------------------------------ |
| `apps/console`   | Gateway console: payments, statements, keys, webhooks                    |
| `apps/simulator` | Simulator UI: scenarios, simulated payments, journal, statements         |
| `apps/validator` | Validator interface: execute the conformance suite and view attestations |

Shared code in `packages/` provides the design system, UI primitives (shadcn/ui), API types and phone number formatting.

```sh
bun install
bun run dev
bun run typecheck && bun run lint && bun run test && bun run build
```

To add a shadcn/ui component, run `bunx shadcn@latest add <name> -c apps/console`. The component is added to `packages/ui`.
