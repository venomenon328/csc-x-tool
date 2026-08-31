import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from './App'

const shows = [
  {
    id: 1, contestId: 1, showNumber: 1, name: 'Super Men', entryListComplete: false, candidateCount: 2,
    selectedCandidate: { id: 101, artist: 'Artist', title: 'Titel', youtubeUrl: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ' },
    contestEntryCount: 18, assessedEntryCount: 16, rankedEntryCount: 15,
    assignedEntryCount: 0, activeParticipantCount: 0, publishedBallotVotedCount: 0, publishedBallotNotVotedCount: 0, publishedBallotUnrecordedCount: 0,
    ballotClosedAt: null, ownParticipationId: 1, ownEntryResolution: 'NO_OWN_ENTRY', ownEntryId: null,
  },
  {
    id: 9, contestId: 1, showNumber: 9, name: 'TBA', entryListComplete: false, candidateCount: 0, selectedCandidate: null,
    contestEntryCount: 0, assessedEntryCount: 0, rankedEntryCount: 0,
    assignedEntryCount: 0, activeParticipantCount: 0, publishedBallotVotedCount: 0, publishedBallotNotVotedCount: 0, publishedBallotUnrecordedCount: 0,
    ballotClosedAt: null, ownParticipationId: 1, ownEntryResolution: 'NO_OWN_ENTRY', ownEntryId: null,
  },
]

const currentContest = { id: 1, name: 'CSC X', displayOrder: 1, current: true, participantCount: 0, showCount: 12, ownParticipationId: null, createdAt: '', updatedAt: '' }

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

  it('loads a compact accessible show overview with a primary action and four stable work areas', async () => {
    const user = userEvent.setup()
    fetchMock.mockImplementation(async (input) => jsonResponse(String(input) === '/api/contests' ? [currentContest] : shows))
    render(<App />)

    expect(screen.getByLabelText('Mottoshows werden geladen')).toBeVisible()
    const superMenCard = (await screen.findByRole('heading', { name: 'Super Men' })).closest('section')
    expect(superMenCard).not.toBeNull()
    const card = within(superMenCard!)

    expect(card.getByText('Kandidatenplanung')).toBeVisible()
    expect(card.getByText('Artist')).toBeVisible()
    expect(card.getByText('Titel')).toBeVisible()
    expect(card.getByText('2 Kandidaten')).toBeVisible()

    const entriesMetric = card.getByRole('group', { name: '18 Beiträge' })
    expect(within(entriesMetric).getByText('18')).toBeVisible()
    expect(within(entriesMetric).getByText('Beiträge')).toBeVisible()
    expect(card.queryByText('18 / 18')).not.toBeInTheDocument()

    const assessedMetric = card.getByRole('group', { name: '16 von 18 Beiträgen eingeschätzt' })
    expect(within(assessedMetric).getByText('16 / 18')).toBeVisible()
    expect(within(assessedMetric).getByText('Eingeschätzt')).toBeVisible()

    const rankedMetric = card.getByRole('group', { name: '15 von 18 Beiträgen gerankt' })
    expect(within(rankedMetric).getByText('15 / 18')).toBeVisible()
    expect(within(rankedMetric).getByText('Gerankt')).toBeVisible()

    expect(card.getByLabelText('Top 15: Offen')).toBeVisible()
    expect(card.getByLabelText('Eigene Einreichung: Keine eigene Einreichung bestätigt')).toBeVisible()
    expect(card.getByLabelText('Auswertung: Auswertung noch nicht verfügbar')).toBeVisible()
    expect(card.getByRole('link', { name: 'Abstimmung fortsetzen' })).toHaveAttribute('href', '/shows/1/voting')
    const navigation = card.getByRole('navigation', { name: 'Arbeitsbereiche für Show 1' })
    expect(within(navigation).getAllByRole('link')).toHaveLength(4)
    expect(within(navigation).getByRole('link', { name: 'Kandidaten' })).toHaveAttribute('href', '/shows/1/candidates')
    expect(within(navigation).getByRole('link', { name: 'Abstimmung' })).toHaveAttribute('href', '/shows/1/voting')
    const evaluationLink = within(navigation).getByRole('link', { name: /Auswertung/ })
    expect(evaluationLink).toHaveAttribute('href', '/shows/1/evaluation?view=published-ballots')
    await user.hover(evaluationLink)
    expect(await screen.findByRole('tooltip')).toHaveTextContent('Auswertung: noch nicht verfügbar')
    expect(within(navigation).getByRole('link', { name: 'Tippspiel' })).toHaveAttribute('href', '/shows/1/tips')
    expect(within(navigation).queryByRole('link', { name: 'Ergebnis' })).not.toBeInTheDocument()
    expect(card.getByRole('button', { name: 'Name von Show 1 bearbeiten' })).toBeVisible()
    expect(card.queryByRole('button', { name: /Weitere Aktionen/ })).not.toBeInTheDocument()

    const tbaCard = screen.getByRole('heading', { name: 'TBA' }).closest('section')
    expect(tbaCard).not.toBeNull()
    const emptyCard = within(tbaCard!)
    expect(emptyCard.getByText('Noch kein Kandidat ausgewählt')).toBeVisible()
    expect(emptyCard.getByText('0 Kandidaten')).toBeVisible()
    expect(emptyCard.getByRole('group', { name: '0 Beiträge' })).toBeVisible()
    expect(emptyCard.getByRole('group', { name: '0 Beiträge eingeschätzt' })).toBeVisible()
    expect(emptyCard.getByRole('group', { name: '0 Beiträge gerankt' })).toBeVisible()
    expect(emptyCard.queryByText('0 / 0')).not.toBeInTheDocument()
  })

  it('renders a clear empty state when the API has no shows', async () => {
    fetchMock.mockImplementation(async (input) => jsonResponse(String(input) === '/api/contests' ? [currentContest] : []))
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

  it('shows published-ballot progress without official result fields on the overview', async () => {
    fetchMock.mockImplementation(async (input) => jsonResponse(String(input) === '/api/contests' ? [currentContest] : [{
      ...shows[0], assignedEntryCount: 18, activeParticipantCount: 20, publishedBallotVotedCount: 18, publishedBallotNotVotedCount: 1, publishedBallotUnrecordedCount: 1,
      ballotClosedAt: '2026-08-27T12:00:00Z',
    }]))
    render(<App />)

    expect(await screen.findByLabelText('Top 15: Abgeschlossen')).toBeVisible()
    expect(screen.getByLabelText('Auswertung: 18 von 20 Stimmzetteln erfasst')).toBeVisible()
    expect(screen.queryByText('Offiziell')).not.toBeInTheDocument()
    expect(screen.queryByText(/Endplatzierung/)).not.toBeInTheDocument()
  })

  it('keeps the own Top-15 lifecycle separate from published-ballot progress', async () => {
    fetchMock.mockImplementation(async (input) => jsonResponse(String(input) === '/api/contests' ? [currentContest] : [{
      ...shows[0], ballotClosedAt: '2026-08-27T12:00:00Z', publishedBallotVotedCount: 3, publishedBallotNotVotedCount: 2, publishedBallotUnrecordedCount: 4,
    }]))
    render(<App />)

    expect(await screen.findByLabelText('Top 15: Abgeschlossen')).toBeVisible()
    expect(screen.getByLabelText('Einreichende: 0 / 18 Beiträge')).toBeVisible()
  })

  it('persists a renamed show and updates the overview from the direct edit action', async () => {
    const user = userEvent.setup()
    fetchMock.mockImplementation(async (input, init) => {
      const path = String(input)
      if (path === '/api/contests') return jsonResponse([currentContest])
      if (path === '/api/shows' || path === '/api/shows?contestId=1') return jsonResponse(shows)
      if (path === '/api/shows/9' && init?.method === 'PATCH') return jsonResponse({ ...shows[1], name: 'Neues Motto' })
      throw new Error(`Unexpected request ${path}`)
    })
    render(<App />)

    await screen.findByRole('heading', { name: 'TBA' })
    const editButton = screen.getByRole('button', { name: 'Name von Show 9 bearbeiten' })
    editButton.focus()
    await user.keyboard('{Enter}')
    expect(screen.getByRole('dialog', { name: 'Mottoshow umbenennen' })).toBeVisible()
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
    fetchMock.mockImplementation(async (input, init) => {
      const path = String(input)
      if (path === '/api/contests') return jsonResponse([currentContest])
      if (path === '/api/shows' || path === '/api/shows?contestId=1') return jsonResponse(shows)
      if (path === '/api/shows/9' && init?.method === 'PATCH') return jsonResponse({
        timestamp: '2026-08-27T00:00:00Z',
        status: 400,
        code: 'VALIDATION_ERROR',
        message: 'Der Show-Name darf nicht leer sein.',
        path: '/api/shows/9',
      }, 400)
      throw new Error(`Unexpected request ${path}`)
    })
    render(<App />)

    await screen.findByRole('heading', { name: 'TBA' })
    await user.click(screen.getByRole('button', { name: 'Name von Show 9 bearbeiten' }))
    await user.clear(screen.getByLabelText('Name der Mottoshow'))
    await user.click(screen.getByRole('button', { name: 'Speichern' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Der Show-Name darf nicht leer sein.')
    expect(screen.getByRole('dialog')).toBeVisible()
  })

  it('finds mixed global search results and navigates to their matching work area', async () => {
    const user = userEvent.setup()
    fetchMock.mockImplementation(async (input) => {
      if (input === '/api/contests') return jsonResponse([currentContest])
      if (input === '/api/shows' || input === '/api/shows?contestId=1') return jsonResponse(shows)
      if (input === '/api/search?q=Artist&contestId=1') return jsonResponse([{
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
    fetchMock.mockImplementation(async (input, init) => {
      const path = String(input)
      if (path === '/api/contests') return jsonResponse([currentContest])
      if (path === '/api/shows' || path === '/api/shows?contestId=1') return jsonResponse(shows)
      if (path === '/api/system/shutdown' && init?.method === 'POST') return jsonResponse({ status: 'SHUTTING_DOWN' }, 202)
      throw new Error(`Unexpected request ${path}`)
    })
    render(<App />)

    await screen.findByRole('heading', { name: 'Super Men' })
    await user.click(screen.getByRole('button', { name: 'Anwendung beenden' }))

    expect(await screen.findByRole('heading', { name: 'Anwendung wurde beendet' })).toBeVisible()
  })
})
