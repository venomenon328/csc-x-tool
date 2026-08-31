import * as Flags from 'country-flag-icons/react/3x2'
import { isCscCountryFlagCode } from './cscCountryFlagCodes'

export function hasLocalCountryFlag(code?: string | null): boolean {
  return isCscCountryFlagCode(code) || (code !== undefined && code !== null && Object.hasOwn(Flags, code))
}
