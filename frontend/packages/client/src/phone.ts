const HAITI = "509"
const SEPARATORS = /[\s().-]/g
const LOCAL = /^\d{8}$/
const NATIONAL = /^509\d{8}$/
const E164 = /^\+[1-9]\d{1,14}$/

export function toE164(input: string, countryCode: string = HAITI): string {
  const compact = input.replace(SEPARATORS, "")
  if (E164.test(compact)) return compact
  if (LOCAL.test(compact)) return `+${countryCode}${compact}`
  if (countryCode === HAITI && NATIONAL.test(compact)) return `+${compact}`
  return compact
}
