import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '../../app/App'

const countries = [
  { code: 'DE', name: 'Deutschland' },
  { code: 'AT', name: 'Österreich' },
]

const alex = {
  id: 1, displayName: 'Alex', countryCode: 'DE', countryName: 'Deutschland', active: true, aliases: ['Lex'], createdAt: '', updatedAt: '',
}

const mira = {
  id: 2, displayName: 'Mira', countryCode: 'AT', countryName: 'Österreich', active: false, aliases: ['Maus'], createdAt: '', updatedAt: '',
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('ParticipantPage', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    window.history.pushState({}, '', '/participants')
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockReset()
  })

  afterEach(() => vi.unstubAllGlobals())

  it('shows loading and empty states, then creates and edits a participant with country and aliases', async () => {
    const user = userEvent.setup()
    let participants: typeof alex[] = []
    fetchMock.mockImplementation(async (input, init) => {
      const path = String(input)
      if (path === '/api/countries') return jsonResponse(countries)
      if (path === '/api/participants' && init?.method === 'POST') {
        participants = [{ ...alex, displayName: 'Neu', aliases: ['Früher'] }]
        return jsonResponse(participants[0], 201)
      }
      if (path === '/api/participants/1' && init?.method === 'PATCH') {
        participants = [{ ...alex, displayName: 'Bearbeitet', countryCode: 'AT', countryName: 'Österreich', aliases: ['Früher 2'] }]
        return jsonResponse(participants[0])
      }
      if (path === '/api/participants') return jsonResponse(participants)
      throw new Error(`Unexpected request ${path}`)
    })
    render(<App />)

    expect(screen.getByRole('heading', { name: 'Teilnehmer' })).toBeVisible()
    expect(await screen.findByText('Noch keine passenden Teilnehmer vorhanden.')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Teilnehmer anlegen' }))
    await user.type(screen.getByRole('textbox', { name: 'Anzeigename' }), 'Neu')
    const countryInput = screen.getByRole('combobox', { name: 'Land' })
    await user.click(countryInput)
    await user.type(countryInput, 'Deutschland{arrowdown}{enter}')
    await user.click(screen.getByRole('button', { name: 'Alias hinzufügen' }))
    await user.type(screen.getByRole('textbox', { name: 'Alias 1' }), 'Früher')
    await user.click(screen.getByRole('button', { name: 'Speichern' }))

    await screen.findByText('Neu')
    expect(fetchMock).toHaveBeenCalledWith('/api/participants', expect.objectContaining({
      method: 'POST', body: JSON.stringify({ displayName: 'Neu', countryCode: 'DE', active: true, aliases: ['Früher'] }),
    }))
    expect(screen.getByRole('img', { name: 'Flagge von Deutschland' })).toBeVisible()

    await user.click(screen.getByRole('button', { name: 'Bearbeiten' }))
    const displayNameInput = screen.getByRole('textbox', { name: 'Anzeigename' })
    await user.clear(displayNameInput)
    await user.type(displayNameInput, 'Bearbeitet')
    const editedCountryInput = screen.getByRole('combobox', { name: 'Land' })
    await user.clear(editedCountryInput)
    await user.type(editedCountryInput, 'Österreich{arrowdown}{enter}')
    const aliasInput = screen.getByRole('textbox', { name: 'Alias 1' })
    await user.clear(aliasInput)
    await user.type(aliasInput, 'Früher 2')
    await user.click(screen.getByRole('button', { name: 'Speichern' }))

    expect(await screen.findByText('Bearbeitet')).toBeVisible()
    expect(fetchMock).toHaveBeenCalledWith('/api/participants/1', expect.objectContaining({
      method: 'PATCH', body: JSON.stringify({ displayName: 'Bearbeitet', countryCode: 'AT', active: true, aliases: ['Früher 2'] }),
    }))
    expect(screen.getByRole('img', { name: 'Flagge von Österreich' })).toBeVisible()
  })

  it('searches aliases, explicitly reveals inactive rows, and confirms deletion', async () => {
    const user = userEvent.setup()
    fetchMock.mockImplementation(async (input, init) => {
      const path = String(input)
      if (path === '/api/countries') return jsonResponse(countries)
      if (path === '/api/participants') return jsonResponse([alex])
      if (path === '/api/participants?includeInactive=true') return jsonResponse([alex, mira])
      if (path === '/api/participants?q=maus&includeInactive=true') return jsonResponse([mira])
      if (path === '/api/participants/2' && init?.method === 'DELETE') return new Response(null, { status: 204 })
      throw new Error(`Unexpected request ${path}`)
    })
    render(<App />)

    await screen.findByText('Alex')
    expect(screen.queryByText('Mira')).not.toBeInTheDocument()
    await user.click(screen.getByLabelText('Inaktive anzeigen'))
    expect(await screen.findByText('Mira')).toBeVisible()
    expect(screen.getByText('Inaktiv')).toBeVisible()

    await user.type(screen.getByPlaceholderText('Name oder Alias suchen'), 'Maus')
    await user.click(screen.getByRole('button', { name: 'Suchen' }))
    expect(await screen.findByText('Maus')).toBeVisible()
    expect(fetchMock).toHaveBeenCalledWith('/api/participants?q=Maus&includeInactive=true')

    const row = screen.getByText('Mira').closest('tr')
    if (row === null) throw new Error('Teilnehmerzeile nicht gefunden')
    await user.click(within(row).getByRole('button', { name: 'Löschen' }))
    expect(screen.getByRole('heading', { name: 'Teilnehmer löschen?' })).toBeVisible()
    expect(fetchMock).not.toHaveBeenCalledWith('/api/participants/2', { method: 'DELETE' })
    await user.click(screen.getByRole('button', { name: 'Teilnehmer löschen' }))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/participants/2', { method: 'DELETE' }))
  })

  it('keeps the editor open and shows structured backend errors', async () => {
    const user = userEvent.setup()
    fetchMock.mockImplementation(async (input, init) => {
      const path = String(input)
      if (path === '/api/countries') return jsonResponse(countries)
      if (path === '/api/participants' && init?.method === 'POST') return jsonResponse({
        timestamp: '2026-08-27T00:00:00Z', status: 400, code: 'INVALID_COUNTRY_CODE', message: 'Der gewählte Ländercode wird nicht unterstützt.', path,
      }, 400)
      if (path === '/api/participants') return jsonResponse([])
      throw new Error(`Unexpected request ${path}`)
    })
    render(<App />)

    await screen.findByText('Noch keine passenden Teilnehmer vorhanden.')
    await user.click(screen.getByRole('button', { name: 'Teilnehmer anlegen' }))
    await user.type(screen.getByRole('textbox', { name: 'Anzeigename' }), 'Neu')
    const countryInput = screen.getByRole('combobox', { name: 'Land' })
    await user.click(countryInput)
    await user.keyboard('{arrowdown}{enter}')
    await user.click(screen.getByRole('button', { name: 'Speichern' }))

    expect(await screen.findByText('Der gewählte Ländercode wird nicht unterstützt.')).toBeVisible()
    expect(screen.getByRole('dialog')).toBeVisible()
  })
})
