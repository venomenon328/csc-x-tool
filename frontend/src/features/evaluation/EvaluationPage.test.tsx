import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '../../app/App'

const show = {
  id: 1, contestId: 1, showNumber: 1, name: 'Show Eins', entryListComplete: false, candidateCount: 1, contestEntryCount: 16,
  assessedEntryCount: 16, rankedEntryCount: 15, assignedEntryCount: 16, activeParticipantCount: 5,
  publishedBallotVotedCount: 0, publishedBallotNotVotedCount: 0, publishedBallotUnrecordedCount: 5,
  ballotClosedAt: '2026-08-31T00:00:00Z', ownParticipationId: 1, ownEntryResolution: 'NO_OWN_ENTRY', ownEntryId: null, selectedCandidate: null,
}
const contest = { id: 1, name: 'CSC X', displayOrder: 1, current: true, participantCount: 5, showCount: 12, ownParticipationId: 1, createdAt: '', updatedAt: '' }

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), { headers: { 'Content-Type': 'application/json' } })
}

describe('evaluation navigation', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockReset()
    fetchMock.mockImplementation(async (input) => {
      switch (String(input)) {
        case '/api/contests': return jsonResponse([contest])
        case '/api/shows/1': return jsonResponse(show)
        case '/api/shows/1/entries': return jsonResponse([])
        case '/api/participants?contestId=1&includeInactive=true': return jsonResponse([])
        case '/api/shows/1/published-ballots': return jsonResponse({ mottoShowId: 1, entryListReady: true, votedCount: 0, notVotedCount: 0, unrecordedCount: 5, participants: [] })
        case '/api/shows/1/results': return jsonResponse({ mottoShowId: 1, prerequisite: 'OWN_ENTRY_NONE', ownParticipation: { participationId: 1, participantId: 1, displayName: 'Ich', countryCode: 'DE' }, ownEntry: null, selectedCandidateDiffers: false, votedCount: 0, notVotedCount: 0, unrecordedCount: 5, derivedTotalPoints: 0, lines: [] })
        default: throw new Error(`Unexpected request ${String(input)}`)
      }
    })
  })

  afterEach(() => vi.unstubAllGlobals())

  it('keeps both views under the canonical URL-addressable evaluation area', async () => {
    const user = userEvent.setup()
    window.history.pushState({}, '', '/shows/1/evaluation?view=published-ballots')
    render(<App />)

    expect(await screen.findByRole('tab', { name: 'Veröffentlichte Stimmzettel', selected: true })).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Veröffentlichte Stimmzettel' })).toBeVisible()
    expect(await screen.findByText('Einzelwertungen')).toBeVisible()
    await user.click(screen.getByRole('tab', { name: 'Meine Einreichung' }))

    expect(await screen.findByRole('tab', { name: 'Meine Einreichung', selected: true })).toBeVisible()
    expect(window.location.pathname + window.location.search).toBe('/shows/1/evaluation?view=own-entry')
    expect(await screen.findByText(/keine eigene Einreichung/)).toBeVisible()
  })

  it.each([
    ['/shows/1/published-ballots', 'Veröffentlichte Stimmzettel', '/shows/1/evaluation?view=published-ballots'],
    ['/shows/1/result', 'Meine Einreichung', '/shows/1/evaluation?view=own-entry'],
  ])('redirects the legacy route %s to its canonical evaluation view', async (legacyPath, tabName, canonicalPath) => {
    window.history.pushState({}, '', legacyPath)
    render(<App />)

    expect(await screen.findByRole('tab', { name: tabName, selected: true })).toBeVisible()
    expect(window.location.pathname + window.location.search).toBe(canonicalPath)
  })
})
