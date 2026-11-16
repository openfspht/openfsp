import { describe, expect, test } from "bun:test"
import type { RunPrepared } from "@workspace/types"

import { usablePreparedRun } from "./prepared-run"

const prepared: RunPrepared = {
  id: "0b4f3c1e-8d7a-4f55-9a43-0d6f1b2c3a4d",
  target: "gateway",
  start: "/api/runs/0b4f3c1e-8d7a-4f55-9a43-0d6f1b2c3a4d/start",
  events: "/api/runs/0b4f3c1e-8d7a-4f55-9a43-0d6f1b2c3a4d/events",
  report: "/api/runs/0b4f3c1e-8d7a-4f55-9a43-0d6f1b2c3a4d/report",
  simulator: {
    simulator_url: "https://simulator.example/runs/5f0e",
    space: "5f0e",
    start_before: "2026-11-11T12:00:00Z",
    environment: "test",
    key_prefix: "ofsp_test_",
    webhook_url:
      "https://validator.example/api/runs/0b4f3c1e-8d7a-4f55-9a43-0d6f1b2c3a4d/webhooks",
    operators: [],
  },
}

describe("usablePreparedRun", () => {
  const before = Date.parse("2026-11-11T11:00:00Z")

  test("keeps a run prepared for the chosen target until its deadline", () => {
    expect(usablePreparedRun(prepared, "gateway", before)).toBe(prepared)
  })

  test("drops a run prepared for another target", () => {
    expect(
      usablePreparedRun(prepared, "native-operator", before)
    ).toBeUndefined()
  })

  test("drops a run whose start deadline passed", () => {
    expect(
      usablePreparedRun(prepared, "gateway", Date.parse("2026-11-11T12:00:01Z"))
    ).toBeUndefined()
  })

  test("drops nothing when nothing was prepared", () => {
    expect(usablePreparedRun(undefined, "gateway", before)).toBeUndefined()
  })
})
