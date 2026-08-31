import { render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '../../app/App'

function jsonResponse(body: unknown) { return new Response(JSON.stringify(body), { headers: { 'Content-Type': 'application/json' } }) }
const show = {
  id: 1, contestId: 1, showNumber: 1, name: 'Show Eins', candidateCount: 1, contestEntryCount: 16, assessedEntryCount: 16, rankedEntryCount: 15,
  assignedEntryCount: 16, activeParticipantCount: 5, publishedBallotVotedCount: 2, publishedBallotNotVotedCount: 1, publishedBallotUnrecordedCount: 1,
  entryListComplete: true, ballotClosedAt: null, selectedCandidate: null,
}
const currentContest = { id: 1, name: 'CSC X', displayOrder: 1, current: true, participantCount: 5, showCount: 12, ownParticipationId: 1, createdAt: '', updatedAt: '' }

describe('own-entry evaluation', () => {
  const fetchMock = vi.fn<typeof fetch>()
  beforeEach(() => { window.history.pushState({}, '', '/shows/1/evaluation?view=own-entry'); vi.stubGlobal('fetch', fetchMock); fetchMock.mockReset() })
  afterEach(() => vi.unstubAllGlobals())

  it('renders every derived ballot state without official result controls', async () => {
    fetchMock.mockImplementation(async (input) => {
      if (String(input) === '/api/contests') return jsonResponse([currentContest])
      if (String(input) === '/api/shows/1') return jsonResponse(show)
      if (String(input) === '/api/shows/1/results') return jsonResponse({
        mottoShowId: 1, prerequisite: 'READY', selectedCandidateDiffers: true, votedCount: 2, notVotedCount: 1, unrecordedCount: 1, derivedTotalPoints: 25,
        ownParticipation: { participationId: 1, participantId: 10, displayName: 'Ich', countryCode: 'DE' },
        ownEntry: { entryId: 101, artist: 'Eigene Band', title: 'Eigener Song', youtubeUrl: null },
        lines: [
          { participationId: 1, participantId: 10, displayName: 'Ich', countryCode: 'DE', countryName: 'Deutschland', ballotStatus: 'EIGENE_TEILNAHME', state: 'OWN_ENTRY', rank: null, points: null },
          { participationId: 2, participantId: 11, displayName: 'Rang', countryCode: 'AT', countryName: 'Österreich', ballotStatus: 'ABGESTIMMT', state: 'RANKED', rank: 1, points: 25 },
          { participationId: 3, participantId: 12, displayName: 'Außerhalb', countryCode: 'CH', countryName: 'Schweiz', ballotStatus: 'ABGESTIMMT', state: 'OUTSIDE_TOP_15', rank: null, points: 0 },
          { participationId: 4, participantId: 13, displayName: 'Keine Stimme', countryCode: 'BE', countryName: 'Belgien', ballotStatus: 'NICHT_ABGESTIMMT', state: 'NO_BALLOT', rank: null, points: null },
          { participationId: 5, participantId: 14, displayName: 'Unbekannt', countryCode: 'DK', countryName: 'Dänemark', ballotStatus: 'UNERFASST', state: 'UNKNOWN', rank: null, points: null },
        ],
      })
      throw new Error(`Unexpected request ${String(input)}`)
    })
    render(<App />)

    await screen.findByRole('heading', { name: 'Show Eins – Auswertung' })
    expect(screen.getByRole('tab', { name: 'Meine Einreichung', selected: true })).toBeVisible()
    expect(screen.getByText('Eigene Band – Eigener Song')).toBeVisible()
    expect(screen.getByText('Eigene Einreichung · nicht wählbar')).toBeVisible()
    expect(screen.getByText('In Top 15')).toBeVisible()
    expect(screen.getByText('Außerhalb Top 15')).toBeVisible()
    expect(screen.getByText('Nicht abgestimmt')).toBeVisible()
    expect(screen.getAllByText('Unbekannt')).toHaveLength(2)
    expect(screen.getByText('25 Punkte')).toBeVisible()
    expect(screen.getByText('0 Punkte')).toBeVisible()
    expect(screen.getByText('Keine offizielle Gesamtwertung.')).toBeVisible()
    expect(screen.getByText(/Die Kandidatenplanung weicht/)).toBeVisible()
    expect(screen.getAllByRole('link', { name: 'Stimmzettel' })[0]).toHaveAttribute('href', '/shows/1/evaluation?view=published-ballots')
    expect(screen.queryByText(/Endplatzierung/)).not.toBeInTheDocument()
  })

  it('requires an explicit own contest participation', async () => {
    fetchMock.mockImplementation(async (input) => String(input) === '/api/contests'
      ? jsonResponse([currentContest])
      : String(input) === '/api/shows/1'
      ? jsonResponse(show)
      : jsonResponse({ mottoShowId: 1, prerequisite: 'OWN_PARTICIPATION_MISSING', ownParticipation: null, ownEntry: null, selectedCandidateDiffers: false, votedCount: 0, notVotedCount: 0, unrecordedCount: 0, derivedTotalPoints: 0, lines: [] }))
    render(<App />)
    expect(await screen.findByText(/Markiere in der Teilnehmerliste zuerst ausdrücklich/)).toBeVisible()
    expect(screen.getByRole('link', { name: 'Teilnehmer öffnen' })).toHaveAttribute('href', '/participants')
  })

  it.each([
    ['OWN_ENTRY_UNRESOLVED', 'Bestätige vor der Ergebnisableitung'],
    ['OWN_ENTRY_NONE', 'keine eigene Einreichung hat'],
    ['ENTRY_LIST_INCOMPLETE', 'vollständige Songzuordnung dieser Show ist noch nicht bestätigt'],
    ['OWN_ENTRY_MISSING', 'noch keine tatsächliche Einreichung zugeordnet'],
  ])('renders the clear %s empty state', async (prerequisite, message) => {
    fetchMock.mockImplementation(async (input) => String(input) === '/api/contests'
      ? jsonResponse([currentContest])
      : String(input) === '/api/shows/1'
      ? jsonResponse(show)
      : jsonResponse({ mottoShowId: 1, prerequisite, ownParticipation: null, ownEntry: null, selectedCandidateDiffers: false, votedCount: 0, notVotedCount: 0, unrecordedCount: 0, derivedTotalPoints: 0, lines: [] }))
    render(<App />)

    expect(await screen.findByText(new RegExp(message))).toBeVisible()
  })
})
