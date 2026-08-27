import * as Flags from 'country-flag-icons/react/3x2'

export function hasLocalCountryFlag(code?: string | null): boolean {
  return code !== undefined && code !== null && Object.hasOwn(Flags, code)
}
