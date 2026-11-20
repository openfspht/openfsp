# Frontend

The frontend is a Bun workspace containing React applications built with Vite and Turbo.

| App              | Purpose                                                                  |
| ---------------- | ------------------------------------------------------------------------ |
| `apps/console`   | Gateway console: payments, statements, keys, webhooks                    |
| `apps/simulator` | Simulator UI: scenarios, simulated payments, journal                     |
| `apps/validator` | Validator interface: execute the conformance suite and view attestations |

Shared code in `packages/` provides the design system, UI primitives (shadcn/ui), API types, the HTTP client and monitoring.

The images of the gateway, the simulator and the validator build their application, precompress it
(`scripts/precompress.ts`) and serve it themselves: there is no image for the interfaces. In development, `bun run dev`
serves each application with Vite and proxies `/admin`, `/simulator` and `/api` to the local services.

```sh
bun install
bun run dev
bun run typecheck && bun run lint && bun run test && bun run build
```

To add a shadcn/ui component, run `bunx shadcn@latest add <name> -c apps/console`. The component is added to `packages/ui`.
