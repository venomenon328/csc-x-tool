import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '../../app/App'
import type { ShowResult } from './api'

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('ResultPage', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    window.history.pushState({}, '', '/shows/1/result')
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockReset()
  })

  afterEach(() => vi.unstubAllGlobals())

  it('keeps a closed result read-only while showing zero points, historical rows and a difference warning', async () => {
    fetchMock.mockImplementation(async (input) => {
      const path = String(input)
      if (path === '/api/shows/1') return jsonResponse({
        id: 1, contestId: 1, showNumber: 1, name: 'Show Eins', candidateCount: 1, contestEntryCount: 15, assessedEntryCount: 15, rankedEntryCount: 15,
        assignedEntryCount: 10, activeParticipantCount: 1, knownActiveResultCount: 1, ballotClosedAt: '2026-08-27T10:00:00Z', resultsClosedAt: '2026-08-27T12:00:00Z',
        calculatedTotalPoints: 5, officialTotalPoints: 7, officialTotalDifference: 2, finalPlace: 3, finalPlaceTied: true,
        selectedCandidate: { id: 7, artist: 'Eigene Band', title: 'Mein Lied', youtubeUrl: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ' },
      })
      if (path === '/api/shows/1/results') return jsonResponse({
        mottoShowId: 1, ballotClosedAt: '2026-08-27T10:00:00Z', resultsClosedAt: '2026-08-27T12:00:00Z',
        selectedCandidate: { id: 7, artist: 'Eigene Band', title: 'Mein Lied', youtubeUrl: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ' },
        calculatedTotalPoints: 5, officialTotalPoints: 7, officialTotalDifference: 2, finalPlace: 3, finalPlaceTied: true,
        lines: [
          { participantId: 1, displayName: 'Alex', countryCode: 'DE', countryName: 'Deutschland', active: true, status: 'ABGESTIMMT', points: 0, persisted: true },
          { participantId: 2, displayName: 'Mira', countryCode: 'AT', countryName: 'Österreich', active: false, status: 'ABGESTIMMT', points: 5, persisted: true },
        ],
      })
      throw new Error(`Unexpected request ${path}`)
    })
    render(<App />)

    await screen.findByRole('heading', { name: 'Show Eins' })
    expect(screen.getByText('Eigene Band – Mein Lied')).toBeVisible()
    expect(screen.getByText('0 Punkte')).toBeVisible()
    expect(screen.getByText('Mira (inaktiv)')).toBeVisible()
    expect(screen.getByText('Die offizielle Summe weicht um 2 Punkte ab.')).toBeVisible()
    expect(screen.getByLabelText('Status von Alex')).toHaveAttribute('aria-disabled', 'true')
    expect(screen.getByRole('button', { name: 'Ergebniserfassung wieder öffnen' })).toBeVisible()
  })

  it('keeps zero points distinct from not voted and persists zero as a regular voted score', async () => {
    const user = userEvent.setup()
    let result: ShowResult = {
      mottoShowId: 1, ballotClosedAt: '2026-08-27T10:00:00Z', resultsClosedAt: null, selectedCandidate: null,
      calculatedTotalPoints: 0, officialTotalPoints: null, officialTotalDifference: null, finalPlace: null, finalPlaceTied: false,
      lines: [
        { participantId: 1, displayName: 'Alex', countryCode: 'DE', countryName: 'Deutschland', active: true, status: 'ABGESTIMMT', points: 0, persisted: true },
        { participantId: 2, displayName: 'Mira', countryCode: 'AT', countryName: 'Österreich', active: true, status: 'NICHT_ABGESTIMMT', points: null, persisted: true },
      ],
    }
    fetchMock.mockImplementation(async (input, init) => {
      const path = String(input)
      if (path === '/api/shows/1') return jsonResponse({
        id: 1, contestId: 1, showNumber: 1, name: 'Show Eins', candidateCount: 0, contestEntryCount: 15, assessedEntryCount: 15, rankedEntryCount: 15,
        assignedEntryCount: 0, activeParticipantCount: 2, knownActiveResultCount: 2, ballotClosedAt: result.ballotClosedAt, resultsClosedAt: null,
        calculatedTotalPoints: result.calculatedTotalPoints, officialTotalPoints: null, officialTotalDifference: null, finalPlace: null, finalPlaceTied: false,
        selectedCandidate: null,
      })
      if (path === '/api/shows/1/results') return jsonResponse(result)
      if (path === '/api/shows/1/results/scores/2' && init?.method === 'PUT') {
        result = {
          ...result,
          lines: [result.lines[0], { ...result.lines[1], status: 'ABGESTIMMT', points: 0 }],
        }
        return jsonResponse(result)
      }
      throw new Error(`Unexpected request ${path}`)
    })
    render(<App />)

    await screen.findByRole('heading', { name: 'Show Eins' })
    expect(screen.getByLabelText('Punkte von Alex')).toHaveTextContent('0 Punkte')
    expect(screen.getByLabelText('Status von Mira')).toHaveTextContent('Nicht abgestimmt')
    expect(screen.queryByLabelText('Punkte von Mira')).not.toBeInTheDocument()

    await user.click(screen.getByLabelText('Status von Mira'))
    await user.click(await screen.findByRole('option', { name: 'Abgestimmt' }))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/shows/1/results/scores/2', expect.objectContaining({
      method: 'PUT', body: JSON.stringify({ status: 'ABGESTIMMT', points: 0 }),
    })))
  })
})
