import { describe, expect, test } from "bun:test"
import type { PlannedScenario, ResultEntry } from "@workspace/types"

import {
  doneCount,
  groupByArea,
  groupByProfile,
  initialRunState,
  isTerminal,
  reduceRun,
  specLink,
  type RunEvent,
} from "./run-state"

const planned = (index: number, scenario: string): PlannedScenario => ({
  index,
  scenario,
  title: scenario,
  area: scenario.split("/")[0]!,
  requirement_id: `R-${index}`,
  requirement: `spec#${index}`,
  level: "core",
  assisted: false,
})

const result = (
  scenario: string,
  verdict: ResultEntry["verdict"]
): ResultEntry => ({
  scenario,
  title: scenario,
  requirement_id: "R",
  requirement: "spec#1",
  level: "core",
  verdict,
  assisted: false,
})

const scenarios = [
  planned(1, "authentication/missing-key"),
  planned(2, "payments/create"),
  planned(3, "authentication/missing-scope"),
]

const started: RunEvent = {
  name: "run",
  data: {
    id: "run-1",
    target: "gateway",
    base_url: "http://gateway.test",
    suite: { version: "0.1.0", protocol_version: "0.1.0" },
    total: 3,
    scenarios,
    limits: [{ reference: "conformance#5.6", text: "limit" }],
  },
}

describe("reduceRun", () => {
  test("plans every scenario as pending, then follows starts and ends", () => {
    let state = reduceRun(initialRunState, started)
    expect(Object.values(state.rows).map((row) => row.status)).toEqual([
      "pending",
      "pending",
      "pending",
    ])
    state = reduceRun(state, {
      name: "scenario-started",
      data: { total: 3, scenario: scenarios[0]! },
    })
    expect(state.rows[1]?.status).toBe("running")
    state = reduceRun(state, {
      name: "scenario-finished",
      data: {
        index: 1,
        total: 3,
        result: result("authentication/missing-key", "PASS"),
      },
    })
    expect(state.rows[1]?.status).toBe("done")
    expect(state.rows[1]?.result?.verdict).toBe("PASS")
    expect(doneCount(state)).toBe(1)
    expect(isTerminal(state)).toBe(false)
  })

  test("a late start never hides a finished scenario", () => {
    let state = reduceRun(initialRunState, started)
    state = reduceRun(state, {
      name: "scenario-finished",
      data: { index: 2, total: 3, result: result("payments/create", "FAIL") },
    })
    state = reduceRun(state, {
      name: "scenario-started",
      data: { total: 3, scenario: scenarios[1]! },
    })
    expect(state.rows[2]?.status).toBe("done")
  })

  test("a summary or a failure ends the run", () => {
    const state = reduceRun(initialRunState, started)
    expect(
      isTerminal(
        reduceRun(state, {
          name: "failed",
          data: { type: "t", title: "stopped", status: 500 },
        })
      )
    ).toBe(true)
  })
})

describe("groupByArea", () => {
  test("keeps the plan order and groups by the first segment of the scenario id", () => {
    const state = reduceRun(initialRunState, started)
    const groups = groupByArea(Object.values(state.rows))
    expect(groups.map((group) => group.area)).toEqual([
      "authentication",
      "payments",
    ])
    expect(groups[0]!.rows.map((row) => row.planned.index)).toEqual([1, 3])
  })
})

describe("groupByProfile", () => {
  test("lists each profile with its scenarios, a not applicable one included, and leaves Core out", () => {
    const row = (index: number, level: string) => ({
      planned: { ...planned(index, `area/s${index}`), level },
      status: "done" as const,
    })
    const groups = groupByProfile(
      [
        row(3, "webhooks.emit"),
        row(1, "core"),
        row(2, "webhooks.emit"),
        row(4, "payments.fee"),
      ],
      [
        { name: "payments.fee", verdict: "NOT_APPLICABLE" },
        { name: "webhooks.emit", verdict: "PASS" },
      ]
    )
    expect(groups.map((group) => [group.name, group.verdict])).toEqual([
      ["payments.fee", "NOT_APPLICABLE"],
      ["webhooks.emit", "PASS"],
    ])
    expect(groups[1]!.rows.map((r) => r.planned.index)).toEqual([2, 3])
  })
})

describe("specLink", () => {
  test("joins the spec base and the requirement reference", () => {
    expect(specLink("https://spec.test/spec", "authentication#6.4")).toBe(
      "https://spec.test/spec/authentication#6.4"
    )
    expect(specLink(undefined, "a#1")).toBeUndefined()
  })
})
