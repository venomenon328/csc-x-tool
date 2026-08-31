export const CSC_COUNTRY_FLAG_CODES = ['XE', 'XN', 'XL', 'XS', 'XW'] as const

export type CscCountryFlagCode = typeof CSC_COUNTRY_FLAG_CODES[number]

export function isCscCountryFlagCode(code?: string | null): code is CscCountryFlagCode {
  return code !== undefined && code !== null && (CSC_COUNTRY_FLAG_CODES as readonly string[]).includes(code)
}
