import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from './App'

const shows = [
  {
    id: 1, showNumber: 1, name: 'Super Men', candidateCount: 2,
    selectedCandidate: { id: 101, artist: 'Artist', title: 'Titel', youtubeUrl: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ' },
    contestEntryCount: 18, listenedEntryCount: 16, rankedEntryCount: 15,
    assignedEntryCount: 0, activeParticipantCount: 0, knownActiveResultCount: 0,
    ballotClosedAt: null, resultsClosedAt: null, calculatedTotalPoints: 0,
    officialTotalPoints: null, officialTotalDifference: null, finalPlace: null, finalPlaceTied: false,
  },
  {
    id: 9, showNumber: 9, name: 'TBA', candidateCount: 0, selectedCandidate: null,
    contestEntryCount: 0, listenedEntryCount: 0, rankedEntryCount: 0,
    assignedEntryCount: 0, activeParticipantCount: 0, knownActiveResultCount: 0,
    ballotClosedAt: null, resultsClosedAt: null, calculatedTotalPoints: 0,
    officialTotalPoints: null, officialTotalDifference: null, finalPlace: null, finalPlaceTied: false,
  },
]

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('App', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    window.history.pushState({}, '', '/')
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockReset()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('loads the persistent show overview and offers the prepared work-area navigation', async () => {
    fetchMock.mockResolvedValue(jsonResponse(shows))
    render(<App />)

    expect(screen.getByLabelText('Mottoshows werden geladen')).toBeVisible()
    const superMenCard = (await screen.findByRole('heading', { name: 'Super Men' })).closest('section')
    expect(superMenCard).not.toBeNull()
    const card = within(superMenCard!)

    expect(card.getByText('Dein Beitrag')).toBeVisible()
    expect(card.getByText('Artist')).toBeVisible()
    expect(card.getByText('Titel')).toBeVisible()
    expect(card.getByText('2 Kandidaten')).toBeVisible()
    expect(card.getByText('18 / 18')).toBeVisible()
    expect(card.getByText('16 / 18')).toBeVisible()
    expect(card.getByText('15 / 18')).toBeVisible()
    expect(card.getByText('Noch nicht abgeschlossen')).toBeVisible()
    expect(card.getByText('Nach Abschluss der Top 15 verfügbar')).toBeVisible()
    expect(card.getByRole('link', { name: 'Kandidaten' })).toHaveAttribute('href', '/shows/1/candidates')
    expect(card.getByRole('link', { name: 'Abstimmung' })).toHaveAttribute('href', '/shows/1/voting')
    expect(card.getByRole('link', { name: 'Ergebnis' })).toHaveAttribute('href', '/shows/1/result')

    const tbaCard = screen.getByRole('heading', { name: 'TBA' }).closest('section')
    expect(tbaCard).not.toBeNull()
    expect(within(tbaCard!).getByText('Noch nicht festgelegt')).toBeVisible()
    expect(within(tbaCard!).getByText('0 Kandidaten')).toBeVisible()
    expect(within(tbaCard!).getAllByText('0')).toHaveLength(3)
  })

  it('renders a clear empty state when the API has no shows', async () => {
    fetchMock.mockResolvedValue(jsonResponse([]))
    render(<App />)

    expect(await screen.findByText('Noch keine Mottoshows verfügbar.')).toBeVisible()
  })

  it('uses the accessible logo as the drawer home link without a visible text wordmark', async () => {
    const user = userEvent.setup()
    window.history.pushState({}, '', '/participants')
    fetchMock.mockResolvedValue(jsonResponse([]))
    render(<App />)

    expect(screen.getByRole('img', { name: 'CSC X Tool' })).toBeVisible()
    const homeLink = screen.getByRole('link', { name: 'CSC X Tool' })
    expect(homeLink).toHaveAttribute('href', '/')
    expect(screen.queryByText('CSC X Tool')).not.toBeInTheDocument()

    await user.click(homeLink)
    expect(window.location.pathname).toBe('/')
  })

  it('shows the closed Top-15 workflow and every later result detail on the overview', async () => {
    fetchMock.mockResolvedValue(jsonResponse([{
      ...shows[0], assignedEntryCount: 17, activeParticipantCount: 20, knownActiveResultCount: 18,
      ballotClosedAt: '2026-08-27T12:00:00Z',
      calculatedTotalPoints: 42, officialTotalPoints: 40, officialTotalDifference: -2,
      finalPlace: 7, finalPlaceTied: true,
    }]))
    render(<App />)

    expect(await screen.findByText('Abgeschlossen')).toBeVisible()
    expect(screen.getByText('17 / 18 Beiträge')).toBeVisible()
    expect(screen.getByText('18 / 20 aktive Teilnehmer erfasst')).toBeVisible()
    expect(screen.getByText('42 Punkte')).toBeVisible()
    expect(screen.getByText('Berechnet')).toBeVisible()
    expect(screen.getByText('40 Punkte')).toBeVisible()
    expect(screen.getByText('Offiziell')).toBeVisible()
    expect(screen.getByRole('alert')).toHaveTextContent('Die offizielle Summe weicht um 2 Punkte ab.')
    expect(screen.getByText('Endplatzierung: 7. Platz (geteilt)')).toBeVisible()
  })

  it('identifies a completed result independently from a completed Top 15', async () => {
    fetchMock.mockResolvedValue(jsonResponse([{
      ...shows[0], ballotClosedAt: '2026-08-27T12:00:00Z', resultsClosedAt: '2026-08-28T12:00:00Z',
    }]))
    render(<App />)

    expect(await screen.findAllByText('Abgeschlossen')).toHaveLength(2)
  })

  it('persists a renamed show and updates the overview', async () => {
    const user = userEvent.setup()
    fetchMock
      .mockResolvedValueOnce(jsonResponse(shows))
      .mockResolvedValueOnce(jsonResponse({ id: 9, showNumber: 9, name: 'Neues Motto' }))
    render(<App />)

    await screen.findByRole('heading', { name: 'TBA' })
    const actions = screen.getByRole('button', { name: 'Weitere Aktionen für Show 9' })
    actions.focus()
    await user.keyboard('{Enter}')
    expect(screen.getByRole('menuitem', { name: 'Name bearbeiten' })).toBeVisible()
    await user.keyboard('{Enter}')
    await user.clear(screen.getByLabelText('Name der Mottoshow'))
    await user.type(screen.getByLabelText('Name der Mottoshow'), 'Neues Motto')
    await user.click(screen.getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.getByRole('heading', { name: 'Neues Motto' })).toBeVisible()
    expect(fetchMock).toHaveBeenLastCalledWith('/api/shows/9', expect.objectContaining({
      method: 'PATCH',
      body: JSON.stringify({ name: 'Neues Motto' }),
    }))
  })

  it('keeps the rename dialog open and displays structured API errors', async () => {
    const user = userEvent.setup()
    fetchMock
      .mockResolvedValueOnce(jsonResponse(shows))
      .mockResolvedValueOnce(jsonResponse({
        timestamp: '2026-08-27T00:00:00Z',
        status: 400,
        code: 'VALIDATION_ERROR',
        message: 'Der Show-Name darf nicht leer sein.',
        path: '/api/shows/9',
      }, 400))
    render(<App />)

    await screen.findByRole('heading', { name: 'TBA' })
    await user.click(screen.getByRole('button', { name: 'Weitere Aktionen für Show 9' }))
    await user.click(screen.getByRole('menuitem', { name: 'Name bearbeiten' }))
    await user.clear(screen.getByLabelText('Name der Mottoshow'))
    await user.click(screen.getByRole('button', { name: 'Speichern' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Der Show-Name darf nicht leer sein.')
    expect(screen.getByRole('dialog')).toBeVisible()
  })

  it('finds mixed global search results and navigates to their matching work area', async () => {
    const user = userEvent.setup()
    fetchMock.mockImplementation(async (input) => {
      if (input === '/api/shows') return jsonResponse(shows)
      if (input === '/api/search?q=Artist') return jsonResponse([{
        type: 'ENTRY', id: 42, showId: 3, showNumber: 3, showName: 'ESC in the CSC', artist: 'Artist', title: 'Titel',
      }])
      return jsonResponse([])
    })
    render(<App />)

    await screen.findByRole('heading', { name: 'Super Men' })
    await user.type(screen.getByLabelText('Globale Suche'), 'Artist')
    await user.click(await screen.findByRole('option', { name: /Artist – Titel.*Beitrag.*Show 3/ }))

    expect(window.location.pathname).toBe('/shows/3/voting')
  })

  it('shows a static completion message after the CSRF-protected shutdown command is accepted', async () => {
    const user = userEvent.setup()
    fetchMock
      .mockResolvedValueOnce(jsonResponse(shows))
      .mockResolvedValueOnce(jsonResponse({ status: 'SHUTTING_DOWN' }, 202))
    render(<App />)

    await screen.findByRole('heading', { name: 'Super Men' })
    await user.click(screen.getByRole('button', { name: 'Anwendung beenden' }))

    expect(await screen.findByRole('heading', { name: 'Anwendung wurde beendet' })).toBeVisible()
  })
})
