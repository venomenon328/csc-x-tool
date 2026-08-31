import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { CountryFlag } from './CountryFlag'
import { hasLocalCountryFlag } from './countryFlagUtils'

describe('CountryFlag', () => {
  it('renders bundled regular and all CSC-specific flags and a neutral fallback without an image request', () => {
    const { rerender } = render(<CountryFlag code="DE" countryName="Deutschland" />)
    expect(screen.getByRole('img', { name: 'Flagge von Deutschland' }).tagName).toBe('svg')

    const cscCountries = [
      ['XE', 'England'],
      ['XN', 'Nordirland'],
      ['XL', 'Saarland'],
      ['XS', 'Schottland'],
      ['XW', 'Wales'],
    ] as const

    for (const [code, name] of cscCountries) {
      rerender(<CountryFlag code={code} countryName={name} />)
      const flag = screen.getByRole('img', { name: `Flagge von ${name}` })
      expect(flag.tagName).toBe('svg')
      expect(flag).toHaveAttribute('data-csc-country', code)
      expect(flag.getAttribute('src')).toBeNull()
      expect(hasLocalCountryFlag(code)).toBe(true)
    }

    rerender(<CountryFlag code="XX" countryName="Unbekannt" />)
    expect(screen.getByRole('img', { name: 'Flagge unbekannt' }).tagName).toBe('SPAN')
    expect(screen.queryByRole('img', { name: 'Flagge unbekannt' })?.getAttribute('src')).toBeNull()
  })

  it('has a local flag asset for every regular ISO code in the backend base catalog', () => {
    const catalog = JSON.parse(readFileSync(
      resolve(process.cwd(), '../backend/src/main/resources/countries/countries-de.json'), 'utf8',
    )) as { code: string }[]

    expect(catalog).toHaveLength(249)
    expect(catalog.every((country) => hasLocalCountryFlag(country.code))).toBe(true)
  })
})
