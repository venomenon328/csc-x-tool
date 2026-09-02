import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '../../app/App'

const cscX = { id: 1, name: 'CSC X', displayOrder: 1, current: true, participantCount: 0, showCount: 12, createdAt: '', updatedAt: '' }
const cscIx = { id: 2, name: 'CSC IX', displayOrder: 2, current: false, participantCount: 0, showCount: 0, createdAt: '', updatedAt: '' }
const countries = [{ code: 'DE', name: 'Deutschland' }, { code: 'AT', name: 'Österreich' }]
const alex = { id: 5, displayName: 'Alex', active: true, aliases: ['Lex'] }

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

  it('assigns one existing identity to two contests with different countries through the user path', async () => {
    const user = userEvent.setup()
    const participations = new Map<number, Array<Record<string, unknown>>>([[1, []], [2, []]])
    fetchMock.mockImplementation(async (input, init) => {
      const path = String(input)
      if (path === '/api/contests') return jsonResponse([cscX, cscIx])
      if (path === '/api/countries') return jsonResponse(countries)
      if (path === '/api/participants?includeInactive=true') return jsonResponse([alex])
      const match = path.match(/^\/api\/contests\/(\d+)\/participants$/)
      if (match && init?.method === 'POST') {
        const contestId = Number(match[1])
        const request = JSON.parse(String(init.body)) as { participantId: number, countryCode: string, active: boolean }
        const country = countries.find((value) => value.code === request.countryCode)!
        const participation = {
          participationId: contestId * 10 + alex.id,
          id: request.participantId,
          displayName: alex.displayName,
          countryCode: country.code,
          countryName: country.name,
          active: request.active,
          identityActive: true,
          aliases: alex.aliases,
          createdAt: '',
          updatedAt: '',
        }
        participations.set(contestId, [...(participations.get(contestId) ?? []), participation])
        return jsonResponse(participation, 201)
      }
      if (match) return jsonResponse(participations.get(Number(match[1])) ?? [])
      throw new Error(`Unexpected request ${path}`)
    })
    render(<App />)

    await screen.findByText('Noch keine passenden Teilnehmer vorhanden.')
    await addExistingIdentity(user, 'Deutschland')
    expect(await screen.findByText('Alex')).toBeVisible()

    const contestInput = screen.getByRole('combobox', { name: 'CSC-Ausgabe' })
    await user.click(contestInput)
    await user.clear(contestInput)
    await user.type(contestInput, 'CSC IX')
    await user.keyboard('{arrowdown}{enter}')
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/contests/2/participants'))
    await screen.findByText('Noch keine passenden Teilnehmer vorhanden.')
    await addExistingIdentity(user, 'Österreich')

    const assignmentRequests = fetchMock.mock.calls
      .filter(([input, init]) => String(input).match(/^\/api\/contests\/[12]\/participants$/) && init?.method === 'POST')
      .map(([input, init]) => ({ path: String(input), body: JSON.parse(String(init?.body)) }))
    expect(assignmentRequests).toEqual([
      { path: '/api/contests/1/participants', body: { participantId: 5, countryCode: 'DE', active: true } },
      { path: '/api/contests/2/participants', body: { participantId: 5, countryCode: 'AT', active: true } },
    ])
    expect(fetchMock).not.toHaveBeenCalledWith('/api/participants', expect.objectContaining({ method: 'POST' }))
  })

  it('creates a new identity and its first participation with one atomic request', async () => {
    const user = userEvent.setup()
    let participations: Array<Record<string, unknown>> = []
    fetchMock.mockImplementation(async (input, init) => {
      const path = String(input)
      if (path === '/api/contests') return jsonResponse([cscX])
      if (path === '/api/countries') return jsonResponse(countries)
      if (path === '/api/contests/1/participants' && init?.method === 'POST') {
        participations = [{ participationId: 7, id: 5, displayName: 'Neu', countryCode: 'DE', countryName: 'Deutschland', active: true, identityActive: true, aliases: ['Alias'], createdAt: '', updatedAt: '' }]
        return jsonResponse(participations[0], 201)
      }
      if (path === '/api/contests/1/participants') return jsonResponse(participations)
      throw new Error(`Unexpected request ${path}`)
    })
    render(<App />)

    expect(await screen.findByText('Noch keine passenden Teilnehmer vorhanden.')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Teilnehmer anlegen' }))
    await user.type(screen.getByRole('textbox', { name: 'Anzeigename' }), 'Neu')
    await selectCountry(user, 'Deutschland')
    await user.click(screen.getByRole('button', { name: 'Alias hinzufügen' }))
    await user.type(screen.getByRole('textbox', { name: 'Alias 1' }), 'Alias')
    await user.click(screen.getByRole('button', { name: 'Speichern' }))

    expect(await screen.findByText('Neu')).toBeVisible()
    expect(fetchMock).toHaveBeenCalledWith('/api/contests/1/participants', expect.objectContaining({
      method: 'POST', body: JSON.stringify({ displayName: 'Neu', aliases: ['Alias'], countryCode: 'DE', active: true }),
    }))
    expect(fetchMock).not.toHaveBeenCalledWith('/api/participants', expect.objectContaining({ method: 'POST' }))
  })

  it('keeps the new participant editor open when the atomic request is rejected', async () => {
    const user = userEvent.setup()
    fetchMock.mockImplementation(async (input, init) => {
      const path = String(input)
      if (path === '/api/contests') return jsonResponse([cscX])
      if (path === '/api/countries') return jsonResponse(countries)
      if (path === '/api/contests/1/participants' && init?.method === 'POST') return jsonResponse({
        timestamp: '2026-08-27T00:00:00Z', status: 400, code: 'INVALID_COUNTRY_CODE', message: 'Der gewählte Ländercode wird nicht unterstützt.', path,
      }, 400)
      if (path === '/api/contests/1/participants') return jsonResponse([])
      throw new Error(`Unexpected request ${path}`)
    })
    render(<App />)

    await screen.findByText('Noch keine passenden Teilnehmer vorhanden.')
    await user.click(screen.getByRole('button', { name: 'Teilnehmer anlegen' }))
    await user.type(screen.getByRole('textbox', { name: 'Anzeigename' }), 'Neu')
    await selectCountry(user, 'Deutschland')
    await user.click(screen.getByRole('button', { name: 'Speichern' }))

    expect(await screen.findByText('Der gewählte Ländercode wird nicht unterstützt.')).toBeVisible()
    expect(screen.getByRole('dialog')).toBeVisible()
    expect(fetchMock).not.toHaveBeenCalledWith('/api/participants', expect.objectContaining({ method: 'POST' }))
  })

  it('marks the existing contest participation as mine from its row', async () => {
    const user = userEvent.setup()
    const participation = { ...alex, participationId: 15, countryCode: 'DE', countryName: 'Deutschland', identityActive: true, createdAt: '', updatedAt: '' }
    let contest = { ...cscX, ownParticipationId: null as number | null }
    fetchMock.mockImplementation(async (input, init) => {
      const path = String(input)
      if (path === '/api/contests/1/own-participation' && init?.method === 'PUT') {
        contest = { ...contest, ownParticipationId: 15 }
        return jsonResponse(contest)
      }
      if (path === '/api/contests') return jsonResponse([contest])
      if (path === '/api/countries') return jsonResponse(countries)
      if (path === '/api/contests/1/participants') return jsonResponse([participation])
      throw new Error(`Unexpected request ${path}`)
    })
    render(<App />)

    await screen.findByText('Alex')
    await user.click(screen.getByRole('button', { name: 'Als meine Teilnahme markieren' }))

    expect(await screen.findByText('Meine Teilnahme')).toBeVisible()
    expect(fetchMock).toHaveBeenCalledWith('/api/contests/1/own-participation', expect.objectContaining({
      method: 'PUT', body: JSON.stringify({ participationId: 15, confirmChange: false }),
    }))
  })

  it('edits the complete BOTB selection list without exposing artists in the participant table', async () => {
    const user = userEvent.setup()
    const participant = { ...alex, participationId: 15, countryCode: 'DE', countryName: 'Deutschland', identityActive: true, botbSelectionCount: 2, createdAt: '', updatedAt: '' }
    fetchMock.mockImplementation(async (input, init) => {
      const path = String(input)
      if (path === '/api/contests') return jsonResponse([cscX])
      if (path === '/api/countries') return jsonResponse(countries)
      if (path === '/api/contests/1/participants') return jsonResponse([participant])
      if (path === '/api/participants/5/botb-selections' && init?.method === 'PUT') return jsonResponse([])
      if (path === '/api/participants/5/botb-selections') return jsonResponse([
        { id: 51, participantId: 5, editionNumber: 9, artist: 'Archiv Act', knownSince: '2025-01-01', createdAt: '', updatedAt: '' },
        { id: 52, participantId: 5, editionNumber: 2, artist: 'Zu entfernen', knownSince: null, createdAt: '', updatedAt: '' },
      ])
      throw new Error(`Unexpected request ${path}`)
    })
    render(<App />)

    await screen.findByText('Alex')
    expect(screen.getByText('2')).toBeVisible()
    expect(screen.queryByText('Archiv Act')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'BOTB bearbeiten' }))
    expect(await screen.findByText('Fehlende Einträge bedeuten nur, dass keine BOTB-Auswahl erfasst wurde. Sie belegen keine Nichtteilnahme.')).toBeVisible()
    const artists = screen.getAllByRole('textbox', { name: 'Interpret' })
    await user.clear(artists[0])
    await user.type(artists[0], 'Bearbeiteter Act')
    await user.click(screen.getByRole('button', { name: 'BOTB-Auswahl 2 entfernen' }))
    await user.click(screen.getByRole('button', { name: 'Zeile hinzufügen' }))
    const editions = screen.getAllByRole('spinbutton', { name: 'BOTB-Ausgabe' })
    await user.type(editions[1], '12')
    const editedArtists = screen.getAllByRole('textbox', { name: 'Interpret' })
    await user.type(editedArtists[1], 'Neuer Act')
    await user.click(screen.getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/participants/5/botb-selections', expect.objectContaining({
      method: 'PUT', body: JSON.stringify([
        { id: 51, editionNumber: 9, artist: 'Bearbeiteter Act', knownSince: '2025-01-01' },
        { editionNumber: 12, artist: 'Neuer Act', knownSince: null },
      ]),
    })))
  })

  it('discards BOTB edits when the dialog is cancelled', async () => {
    const user = userEvent.setup()
    const participant = { ...alex, participationId: 15, countryCode: 'DE', countryName: 'Deutschland', identityActive: true, botbSelectionCount: 1, createdAt: '', updatedAt: '' }
    fetchMock.mockImplementation(async (input, init) => {
      const path = String(input)
      if (path === '/api/contests') return jsonResponse([cscX])
      if (path === '/api/countries') return jsonResponse(countries)
      if (path === '/api/contests/1/participants') return jsonResponse([participant])
      if (path === '/api/participants/5/botb-selections' && init?.method === 'PUT') return jsonResponse([])
      if (path === '/api/participants/5/botb-selections') return jsonResponse([
        { id: 51, participantId: 5, editionNumber: 9, artist: 'Archiv Act', knownSince: null, createdAt: '', updatedAt: '' },
      ])
      throw new Error(`Unexpected request ${path}`)
    })
    render(<App />)

    await screen.findByText('Alex')
    await user.click(screen.getByRole('button', { name: 'BOTB bearbeiten' }))
    await user.clear(await screen.findByRole('textbox', { name: 'Interpret' }))
    await user.type(screen.getByRole('textbox', { name: 'Interpret' }), 'Verwerfen')
    await user.click(screen.getByRole('button', { name: 'Abbrechen' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalledWith('/api/participants/5/botb-selections', expect.objectContaining({ method: 'PUT' }))
  })
})

async function addExistingIdentity(user: ReturnType<typeof userEvent.setup>, country: string) {
  await user.click(screen.getByRole('button', { name: 'Vorhandene Identität hinzufügen' }))
  const identityInput = await screen.findByRole('combobox', { name: 'Vorhandene Identität' })
  await user.click(identityInput)
  await user.type(identityInput, 'Alex')
  await user.keyboard('{arrowdown}{enter}')
  await selectCountry(user, country)
  await user.click(screen.getByRole('button', { name: 'Zuordnen' }))
}

async function selectCountry(user: ReturnType<typeof userEvent.setup>, country: string) {
  const countryInput = screen.getByRole('combobox', { name: 'Land' })
  await user.click(countryInput)
  await user.type(countryInput, `${country}{arrowdown}{enter}`)
}
