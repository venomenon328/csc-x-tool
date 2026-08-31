import { render, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { PublishedBallotsPanel } from './PublishedBallotsPanel'

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), { headers: { 'Content-Type': 'application/json' } })
}

describe('published ballots panel', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockReset()
  })

  afterEach(() => vi.unstubAllGlobals())

  it('renders the existing country flag next to each country name in the shared overview table', async () => {
    fetchMock.mockResolvedValue(jsonResponse({
      mottoShowId: 7,
      entryListReady: true,
      votedCount: 1,
      notVotedCount: 0,
      unrecordedCount: 0,
      participants: [{
        participationId: 11,
        participantId: 101,
        displayName: 'Alba',
        countryCode: 'XS',
        countryName: 'Schottland',
        status: 'ABGESTIMMT',
        ballotExists: true,
        updatedAt: '2026-08-31T00:00:00Z',
      }],
    }))

    render(<PublishedBallotsPanel entries={[]} participants={[]} showId={7} />)

    const row = (await screen.findByText('Alba')).closest('tr')
    expect(row).not.toBeNull()
    expect(within(row!).getByRole('img', { name: 'Flagge von Schottland' })).toBeVisible()
    expect(within(row!).getByText('Schottland')).toBeVisible()
    expect(within(row!).getByText('ABGESTIMMT')).toBeVisible()
    expect(within(row!).getByRole('button', { name: 'Details' })).toBeVisible()
    expect(screen.queryByText(/Punkte werden nur aus diesen Rängen abgeleitet/)).not.toBeInTheDocument()
  })
})
