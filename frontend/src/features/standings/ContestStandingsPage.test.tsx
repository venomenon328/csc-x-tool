import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ContestStandingsPage } from './ContestStandingsPage'
import type { ContestStandings } from './contestApi'
import type * as ContestApi from './contestApi'

const state = vi.hoisted(() => ({ contestId: 1, fetch: vi.fn() }))
vi.mock('../contests/ContestContext', () => ({ useContest: () => ({ selectedContestId: state.contestId, selectedContest: { name: `CSC ${state.contestId}` } }) }))
vi.mock('./contestApi', async (original) => ({ ...await original<typeof ContestApi>(), fetchContestStandings: state.fetch }))

function fixture(contestId = 1): ContestStandings {
  return {
    contestId, includedShowIds: [1, 3],
    shows: [{ showId: 1, showNumber: 1, name: 'First', status: 'CLOSED', closedAt: '2026-10-08T00:00:00Z' }, { showId: 2, showNumber: 2, name: 'Open', status: 'READY', closedAt: null }, { showId: 3, showNumber: 4, name: 'Last', status: 'CLOSED', closedAt: '2026-10-08T00:00:00Z' }],
    rows: Array.from({ length: 17 }, (_, index) => ({
      participationId: index + 1, participantId: index + 90, displayName: `Participant ${index + 1}`, countryCode: 'DE', countryName: 'Deutschland',
      rank: index < 16 ? 1 : 17, totalPoints: index < 16 ? 1 : 0, rankChange: null,
      shows: [{ showId: 1, state: 'SCORED_ENTRY', entryId: index + 100, artist: `Artist ${index + 1}`, title: 'Song', ballotPoints: 10, showRank: index < 16 ? 15 : 17, contestPoints: index < 16 ? 1 : 0 },
        { showId: 2, state: 'NOT_COUNTED', entryId: null, artist: null, title: null, ballotPoints: null, showRank: null, contestPoints: null },
        { showId: 3, state: 'NO_ENTRY', entryId: null, artist: null, title: null, ballotPoints: null, showRank: null, contestPoints: null }],
      history: [{ showId: 1, showNumber: 1, included: true, totalPoints: index < 16 ? 1 : 0, rank: 1, rankChange: null }, { showId: 2, showNumber: 2, included: false, totalPoints: null, rank: null, rankChange: null }, { showId: 3, showNumber: 4, included: true, totalPoints: index < 16 ? 1 : 0, rank: 1, rankChange: 0 }],
    })),
  }
}

describe('contest standings', () => {
  beforeEach(() => { state.contestId = 1; state.fetch.mockReset(); state.fetch.mockResolvedValue(fixture()) })
  it('renders complete boundary groups, distinct cells, details and selectable history from the response', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter><ContestStandingsPage /></MemoryRouter>)
    const table = await screen.findByRole('table', { name: 'Contest-Gesamtwertung' })
    expect(within(table).getAllByRole('row')).toHaveLength(18)
    expect(within(table).getAllByText('Keine Einreichung')).toHaveLength(17)
    expect(within(table).getAllByLabelText('Show nicht gewertet')).toHaveLength(17)
    expect(within(table).getByRole('button', { name: 'Participant 17, Show 1: 0 Gesamtwertungspunkte, Details' })).toBeVisible()
    await user.click(within(table).getByRole('button', { name: 'Participant 16, Show 1: 1 Gesamtwertungspunkte, Details' }))
    expect(screen.getByRole('dialog')).toHaveTextContent('Showrang: 15')
    expect(screen.getByRole('dialog')).toHaveTextContent('Stimmzettelpunkte: 10')
    await user.click(screen.getByRole('button', { name: 'Schließen' }))
    await user.click(await screen.findByRole('combobox', { name: 'Teilnehmer für den Verlauf auswählen' }))
    await user.click(screen.getByRole('option', { name: 'Participant 16 · Deutschland' }))
    const chart = screen.getByRole('img', { name: 'Gesamtwertungspunkte nach Showreihenfolge' })
    expect(chart.querySelectorAll('circle')).toHaveLength(2)
    // No connection across the explicitly open show or the absent show number 3.
    expect(chart.querySelectorAll('line[stroke-width="2"]')).toHaveLength(0)
    expect(screen.getByRole('table', { name: 'Genaue Verlaufswerte' })).toHaveTextContent('Nicht gewertet')
    await user.click(screen.getByRole('combobox', { name: 'Verlauf anzeigen' }))
    await user.click(screen.getByRole('option', { name: 'Contestplatz' }))
    expect(screen.getByRole('img', { name: 'Contestplatz nach Showreihenfolge' })).toBeVisible()
  })

  it('hides old results immediately on contest change and ignores a late response', async () => {
    let resolveFirst!: (data: ContestStandings) => void
    state.fetch.mockImplementation((id: number) => id === 1 ? new Promise<ContestStandings>((resolve) => { resolveFirst = resolve }) : Promise.resolve({ ...fixture(2), rows: [] }))
    const view = render(<MemoryRouter><ContestStandingsPage /></MemoryRouter>)
    state.contestId = 2
    view.rerender(<MemoryRouter><ContestStandingsPage /></MemoryRouter>)
    await screen.findByRole('table', { name: 'Contest-Gesamtwertung' })
    await act(async () => { resolveFirst(fixture(1)) })
    expect(screen.queryByText('Participant 1')).not.toBeInTheDocument()
    expect(screen.getByText('CSC 2')).toBeVisible()
    state.contestId = 1
    view.rerender(<MemoryRouter><ContestStandingsPage /></MemoryRouter>)
    await act(async () => { resolveFirst(fixture(1)) })
    expect(await screen.findByText('Participant 1')).toBeVisible()
    state.contestId = 2
    view.rerender(<MemoryRouter><ContestStandingsPage /></MemoryRouter>)
    expect(screen.queryByText('Participant 1')).not.toBeInTheDocument()
  })

  it('does not invent ranks or winners without a counted show', async () => {
    const empty = fixture()
    empty.includedShowIds = []
    empty.rows = empty.rows.map((r) => ({ ...r, rank: null, totalPoints: 0, shows: r.shows.map((s) => ({ ...s, state: 'NOT_COUNTED', contestPoints: null })) }))
    state.fetch.mockResolvedValue(empty)
    render(<MemoryRouter><ContestStandingsPage /></MemoryRouter>)
    expect(await screen.findByText(/Noch keine gewertete Show/)).toBeVisible()
    expect(screen.getByText('Participant 17')).toBeVisible()
    expect(screen.queryByRole('button', { name: /Gesamtwertungspunkte, Details/ })).not.toBeInTheDocument()
  })
})
