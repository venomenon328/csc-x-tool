import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { ParticipantSelect } from './ParticipantSelect'
import type { Participant } from './api'

const participants: Participant[] = [
  { id: 1, displayName: 'Alex', countryCode: 'DE', countryName: 'Deutschland', active: true, aliases: ['Lex'], createdAt: '', updatedAt: '' },
  { id: 2, displayName: 'Mira', countryCode: 'AT', countryName: 'Österreich', active: false, aliases: ['Maus'], createdAt: '', updatedAt: '' },
]

describe('ParticipantSelect', () => {
  it('finds active participants by alias and renders their land and local flag', async () => {
    const user = userEvent.setup()
    render(<ParticipantSelect onChange={() => {}} options={participants} value={null} />)

    await user.click(screen.getByRole('combobox', { name: 'Teilnehmer' }))
    await user.type(screen.getByRole('combobox', { name: 'Teilnehmer' }), 'lex')

    expect(await screen.findByRole('option', { name: /Alex/ })).toHaveTextContent('Deutschland')
    expect(screen.getByRole('img', { name: 'Flagge von Deutschland' })).toBeVisible()
    expect(screen.queryByRole('option', { name: /Mira/ })).not.toBeInTheDocument()
  })

  it('only includes inactive participants when the caller explicitly opts in', async () => {
    const user = userEvent.setup()
    render(<ParticipantSelect includeInactive onChange={() => {}} options={participants} value={null} />)

    await user.click(screen.getByRole('combobox', { name: 'Teilnehmer' }))
    await user.type(screen.getByRole('combobox', { name: 'Teilnehmer' }), 'maus')

    expect(await screen.findByRole('option', { name: /Mira/ })).toHaveTextContent('Österreich')
    expect(screen.getByText('Mira (inaktiv)')).toBeVisible()
  })
})
