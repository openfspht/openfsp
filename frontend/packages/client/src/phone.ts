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

const HAITIAN_E164 = /^\+509(\d{2})(\d{2})(\d{4})$/

export function formatPhoneNumber(input: string): string {
  const e164 = toE164(input)
  const haitian = e164.match(HAITIAN_E164)
  return haitian ? `+509 ${haitian[1]} ${haitian[2]} ${haitian[3]}` : e164
}
