import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '../../app/App'

const contest = { id: 1, name: 'CSC X', displayOrder: 1, current: true, participantCount: 0, showCount: 12, createdAt: '', updatedAt: '' }
const countries = [{ code: 'DE', name: 'Deutschland' }, { code: 'AT', name: 'Österreich' }]

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

  it('creates the permanent identity first and then its contest-specific participation', async () => {
    const user = userEvent.setup()
    let participations: Array<Record<string, unknown>> = []
    fetchMock.mockImplementation(async (input, init) => {
      const path = String(input)
      if (path === '/api/contests') return jsonResponse([contest])
      if (path === '/api/countries') return jsonResponse(countries)
      if (path === '/api/contests/1/participants' && init?.method === 'POST') {
        participations = [{ participationId: 7, id: 5, displayName: 'Neu', countryCode: 'DE', countryName: 'Deutschland', active: true, identityActive: true, aliases: ['Alias'], createdAt: '', updatedAt: '' }]
        return jsonResponse(participations[0], 201)
      }
      if (path === '/api/participants' && init?.method === 'POST') return jsonResponse({ id: 5, displayName: 'Neu', active: true, aliases: ['Alias'] }, 201)
      if (path === '/api/participants/5' && init?.method === 'PATCH') return jsonResponse({ id: 5, displayName: 'Bearbeitet', active: true, aliases: ['Neu'] })
      if (path === '/api/contests/1/participants/5' && init?.method === 'PATCH') {
        participations = [{ ...participations[0], displayName: 'Bearbeitet', countryCode: 'AT', countryName: 'Österreich', aliases: ['Neu'] }]
        return jsonResponse(participations[0])
      }
      if (path === '/api/contests/1/participants') return jsonResponse(participations)
      throw new Error(`Unexpected request ${path}`)
    })
    render(<App />)

    expect(await screen.findByText('Noch keine passenden Teilnehmer vorhanden.')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Teilnehmer anlegen' }))
    await user.type(screen.getByRole('textbox', { name: 'Anzeigename' }), 'Neu')
    const countryInput = screen.getByRole('combobox', { name: 'Land' })
    await user.click(countryInput)
    await user.type(countryInput, 'Deutschland{arrowdown}{enter}')
    await user.click(screen.getByRole('button', { name: 'Alias hinzufügen' }))
    await user.type(screen.getByRole('textbox', { name: 'Alias 1' }), 'Alias')
    await user.click(screen.getByRole('button', { name: 'Speichern' }))

    expect(await screen.findByText('Neu')).toBeVisible()
    expect(fetchMock).toHaveBeenCalledWith('/api/participants', expect.objectContaining({
      method: 'POST', body: JSON.stringify({ displayName: 'Neu', active: true, aliases: ['Alias'] }),
    }))
    expect(fetchMock).toHaveBeenCalledWith('/api/contests/1/participants', expect.objectContaining({
      method: 'POST', body: JSON.stringify({ participantId: 5, countryCode: 'DE', active: true }),
    }))

    await user.click(screen.getByRole('button', { name: 'Bearbeiten' }))
    const nameInput = screen.getByRole('textbox', { name: 'Anzeigename' })
    await user.clear(nameInput)
    await user.type(nameInput, 'Bearbeitet')
    const editedCountryInput = screen.getByRole('combobox', { name: 'Land' })
    await user.clear(editedCountryInput)
    await user.type(editedCountryInput, 'Österreich{arrowdown}{enter}')
    const aliasInput = screen.getByRole('textbox', { name: 'Alias 1' })
    await user.clear(aliasInput)
    await user.type(aliasInput, 'Neu')
    await user.click(screen.getByRole('button', { name: 'Speichern' }))

    expect(await screen.findByText('Bearbeitet')).toBeVisible()
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/contests/1/participants/5', expect.objectContaining({
      method: 'PATCH', body: JSON.stringify({ countryCode: 'AT', active: true }),
    })))
  })

  it('keeps the editor open when the contest participation is rejected', async () => {
    const user = userEvent.setup()
    fetchMock.mockImplementation(async (input, init) => {
      const path = String(input)
      if (path === '/api/contests') return jsonResponse([contest])
      if (path === '/api/countries') return jsonResponse(countries)
      if (path === '/api/contests/1/participants' && init?.method === 'POST') return jsonResponse({
        timestamp: '2026-08-27T00:00:00Z', status: 400, code: 'INVALID_COUNTRY_CODE', message: 'Der gewählte Ländercode wird nicht unterstützt.', path,
      }, 400)
      if (path === '/api/participants' && init?.method === 'POST') return jsonResponse({ id: 5, displayName: 'Neu', active: true, aliases: [] }, 201)
      if (path === '/api/contests/1/participants') return jsonResponse([])
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
