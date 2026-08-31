import { render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ShowStandingsEvaluation } from './ShowStandingsPage'

function jsonResponse(body: unknown) { return new Response(JSON.stringify(body), { headers: { 'Content-Type': 'application/json' } }) }

describe('show standings evaluation', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => { vi.stubGlobal('fetch', fetchMock); fetchMock.mockReset() })
  afterEach(() => vi.unstubAllGlobals())

  it('renders all entries, including zero points and a shared derived interim rank', async () => {
    fetchMock.mockResolvedValue(jsonResponse({
      mottoShowId: 7, votedCount: 2, notVotedCount: 1, unrecordedCount: 1,
      entries: [
        { interimRank: 1, entryId: 11, artist: 'Punkte', title: 'Oben', youtubeUrl: null, submitterParticipantId: 1, submitterDisplayName: 'Alice', submitterCountryCode: 'DE', submitterCountryName: 'Deutschland', points: 25, mentions: 1 },
        { interimRank: 2, entryId: 12, artist: 'Gleich', title: 'Eins', youtubeUrl: null, submitterParticipantId: 2, submitterDisplayName: 'Bob', submitterCountryCode: 'AT', submitterCountryName: 'Österreich', points: 20, mentions: 2 },
        { interimRank: 2, entryId: 13, artist: 'Gleich', title: 'Zwei', youtubeUrl: null, submitterParticipantId: 3, submitterDisplayName: 'Cara', submitterCountryCode: 'CH', submitterCountryName: 'Schweiz', points: 20, mentions: 1 },
        { interimRank: 4, entryId: 14, artist: 'Null', title: 'Bleibt sichtbar', youtubeUrl: null, submitterParticipantId: 4, submitterDisplayName: 'Dana', submitterCountryCode: 'FI', submitterCountryName: 'Finnland', points: 0, mentions: 0 },
      ],
    }))

    render(<ShowStandingsEvaluation showId={7} />)

    expect(await screen.findByRole('table', { name: 'Abgeleiteter Zwischenstand' })).toBeVisible()
    expect(screen.getByText('Punkte – Oben')).toBeVisible()
    expect(screen.getByText('Null – Bleibt sichtbar')).toBeVisible()
    expect(screen.getByText('25')).toBeVisible()
    expect(screen.getAllByText('20')).toHaveLength(2)
    expect(screen.getAllByText('0')).toHaveLength(1)
    expect(screen.getByText(/Punktgleiche Beiträge erhalten denselben Zwischenrang/)).toBeVisible()
    expect(screen.getByText(/Zwischenstand ist unvollständig/)).toBeVisible()
    expect(screen.getByRole('img', { name: 'Flagge von Deutschland' })).toBeVisible()
  })

  it('uses a clear empty state instead of a zero-point table without published ballots', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ mottoShowId: 7, votedCount: 0, notVotedCount: 1, unrecordedCount: 3, entries: [] }))

    render(<ShowStandingsEvaluation showId={7} />)

    expect(await screen.findByText(/Noch keine veröffentlichten Stimmzettel erfasst/)).toBeVisible()
    expect(screen.queryByRole('table', { name: 'Abgeleiteter Zwischenstand' })).not.toBeInTheDocument()
  })
})
