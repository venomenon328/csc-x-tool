import * as Flags from 'country-flag-icons/react/3x2'
import { CscCountryFlag, isCscCountryFlagCode } from './cscCountryFlags'
import { hasLocalCountryFlag } from './countryFlagUtils'

type CountryFlagProps = {
  code?: string | null
  countryName?: string | null
  size?: number
}

export function CountryFlag({ code, countryName, size = 24 }: CountryFlagProps) {
  const label = countryName === undefined || countryName === null ? 'Flagge unbekannt' : `Flagge von ${countryName}`

  if (isCscCountryFlagCode(code)) {
    return <CscCountryFlag code={code} label={label} size={size} />
  }

  const Flag = code !== undefined && code !== null && hasLocalCountryFlag(code)
    ? Flags[code as keyof typeof Flags]
    : undefined

  if (Flag === undefined) {
    return <span aria-label="Flagge unbekannt" role="img" style={{ display: 'inline-block', fontSize: size, lineHeight: 1 }}>⚐</span>
  }

  return <Flag aria-label={label} role="img" style={{ display: 'inline-block', height: Math.round(size * 2 / 3), verticalAlign: 'middle', width: size }} />
}
