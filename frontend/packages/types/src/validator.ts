export const VALIDATOR_TARGETS = ["gateway", "native-operator"] as const
export type ValidatorTarget = (typeof VALIDATOR_TARGETS)[number]

export const SCENARIO_VERDICTS = [
  "PASS",
  "FAIL",
  "NOT_APPLICABLE",
  "INCONCLUSIVE",
  "ASSISTED",
] as const
export type ScenarioVerdict = (typeof SCENARIO_VERDICTS)[number]

export const LEVEL_VERDICTS = [
  "PASS",
  "FAIL",
  "INCOMPLETE",
  "NOT_APPLICABLE",
] as const
export type LevelVerdict = (typeof LEVEL_VERDICTS)[number]

export interface ConformanceLimit {
  reference: string
  text: string
}

export interface RedactedExchange {
  request: {
    method: string
    url: string
    headers: Record<string, string>
    body?: string | null
  }
  response: { status: number; headers: Record<string, string>; body: string }
}

export interface ResultMismatch {
  expectation: string
  expected: string
  observed: string
}

export interface ResultEntry {
  scenario: string
  title: string
  requirement_id: string
  requirement: string
  level: string
  verdict: ScenarioVerdict
  assisted: boolean
  action?: string
  reason?: string
  failure?: {
    step: string
    mismatches: ResultMismatch[]
    exchange?: RedactedExchange
  }
}

export interface ProfileResult {
  name: string
  verdict: LevelVerdict
}

export interface ConformanceClaim {
  target: string
  level: string
  profiles: string[]
  protocol_version: string
}

export interface SuiteVersion {
  version: string
  protocol_version: string
}

export interface ConformanceReport {
  claim: ConformanceClaim
  suite: SuiteVersion
  target_protocol_version?: string | null
  date: string
  duration_ms: number
  configuration: Record<string, unknown>
  core: LevelVerdict
  profiles: ProfileResult[]
  results: ResultEntry[]
  limits: ConformanceLimit[]
}

export interface PlannedScenario {
  index: number
  scenario: string
  title: string
  area: string
  requirement_id: string
  requirement: string
  level: string
  assisted: boolean
}

export interface RunStartedEvent {
  id: string
  target: string
  base_url: string
  suite: SuiteVersion
  total: number
  scenarios: PlannedScenario[]
  limits: ConformanceLimit[]
}

export interface ScenarioStartedEvent {
  total: number
  scenario: PlannedScenario
}

export interface ScenarioFinishedEvent {
  index: number
  total: number
  result: ResultEntry
}

export interface RunSummaryEvent {
  report_id: string
  core: LevelVerdict
  profiles: ProfileResult[]
  claim: ConformanceClaim
  target_protocol_version?: string | null
  duration_ms: number
  limits: ConformanceLimit[]
}

export interface ValidatorProblem {
  type: string
  title: string
  status: number
  detail?: string
  instance?: string
  errors?: { pointer: string; detail: string }[]
}

export interface SimulatedOperatorConfig {
  id: string
  dialect: string
  display_name?: string
  base_url: string
  secondary_url?: string
  credentials: Record<string, string>
}

export interface SimulatorConfig {
  simulator_url: string
  environment: string
  key_prefix: string
  derivation_key?: string
  operators: SimulatedOperatorConfig[]
}

export interface AssistedScenario {
  scenario: string
  requirement_id: string
  action: string
  inputs: string[]
}

export interface SuiteDescription {
  version: string
  protocol_version: string
  spec_url: string
  targets: ValidatorTarget[]
  restricted_key_input: string
  assisted: AssistedScenario[]
  limits: ConformanceLimit[]
}

export interface RunRequest {
  target: ValidatorTarget
  base_url: string
  api_key: string
  restricted_key?: string
  derivation_key?: string
  inputs?: Record<string, string>
}

export interface RunCreated {
  id: string
  events: string
  report: string
}
