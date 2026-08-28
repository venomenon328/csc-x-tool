import * as Flags from 'country-flag-icons/react/3x2'
import { hasLocalCountryFlag } from './countryFlagUtils'

type CountryFlagProps = {
  code?: string | null
  countryName?: string | null
  size?: number
}

export function CountryFlag({ code, countryName, size = 24 }: CountryFlagProps) {
  const label = countryName === undefined || countryName === null ? 'Flagge unbekannt' : `Flagge von ${countryName}`

  if (code === 'XS') {
    return (
      <svg aria-label={label} role="img" style={{ display: 'inline-block', height: Math.round(size * 2 / 3), verticalAlign: 'middle', width: size }} viewBox="0 0 3 2">
        <rect fill="#0065BD" height="2" width="3" />
        <path d="M0 0 L3 2 M3 0 L0 2" fill="none" stroke="#fff" strokeWidth="0.34" />
      </svg>
    )
  }

  const Flag = code !== undefined && code !== null && hasLocalCountryFlag(code)
    ? Flags[code as keyof typeof Flags]
    : undefined

  if (Flag === undefined) {
    return <span aria-label="Flagge unbekannt" role="img" style={{ display: 'inline-block', fontSize: size, lineHeight: 1 }}>⚐</span>
  }

  return <Flag aria-label={label} role="img" style={{ display: 'inline-block', height: Math.round(size * 2 / 3), verticalAlign: 'middle', width: size }} />
}
