import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ParticipantSelect } from './ParticipantSelect'
import type { Participant } from './api'

const participants: Participant[] = [
  { id: 1, displayName: 'Alex', countryCode: 'DE', countryName: 'Deutschland', active: true, aliases: ['Lex'], createdAt: '', updatedAt: '' },
  { id: 2, displayName: 'Mira', countryCode: 'AT', countryName: 'Österreich', active: false, aliases: ['Maus'], createdAt: '', updatedAt: '' },
  { id: 3, displayName: 'Nora', countryCode: 'CH', countryName: 'Schweiz', active: false, aliases: ['Nori'], createdAt: '', updatedAt: '' },
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

  it('keeps an already assigned inactive participant visible without offering other inactive participants', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    render(<ParticipantSelect onChange={onChange} options={participants} value={participants[1]} />)

    expect(screen.getByRole('combobox', { name: 'Teilnehmer' })).toHaveValue('Mira – Österreich (inaktiv)')
    await user.click(screen.getByRole('combobox', { name: 'Teilnehmer' }))

    expect(await screen.findByRole('option', { name: /Mira/ })).toBeVisible()
    expect(screen.getByRole('option', { name: /Alex/ })).toBeVisible()
    expect(screen.queryByRole('option', { name: /Nora/ })).not.toBeInTheDocument()
    await user.click(screen.getByLabelText('Clear'))
    expect(onChange).toHaveBeenCalledWith(null)
  })
})
