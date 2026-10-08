import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { StatisticsPage } from './StatisticsPage'
import { Heatmap } from './RelationshipViews'
import type { Statistics, Relation } from './api'

const state = vi.hoisted(() => ({ contestId: 1, fetch: vi.fn() }))
vi.mock('../contests/ContestContext', () => ({ useContest: () => ({ selectedContestId: state.contestId, selectedContest: { name: `CSC ${state.contestId}` } }) }))
vi.mock('./api', async original => ({ ...await original<typeof import('./api')>(), fetchStatistics: state.fetch }))

function fixture(contestId = 1, size = 8): Statistics {
  const rows = Array.from({ length: size }, (_, i) => ({
    participationId: i + 1, participantId: i + 100, displayName: `Person ${i + 1}`, countryCode: 'DE', countryName: 'Deutschland', rank: i + 1, totalPoints: 45 - i, rankChange: null,
    shows: [1, 2, 3, 4, 5].map(showId => ({ showId, state: 'SCORED_ENTRY' as const, entryId: showId * 100 + i + 1, artist: `Artist ${i + 1}`, title: `Song ${showId}`, ballotPoints: 30, showRank: showId === 3 ? 17 : i + 1, contestPoints: showId === 3 ? 0 : 20 })),
    history: [1, 2, 3, 4, 5].map(showId => ({ showId, showNumber: showId, included: true, totalPoints: showId * 20, rank: i + 1, rankChange: null })),
  }))
  const relations: Relation[] = []
  for (let a = 1; a <= size; a++) for (let b = 1; b <= size; b++) {
    if (a === b) continue
    const values = a === 1 && b === 2 ? [25, 20, 0] : a === 2 && b === 1 ? [10, null, 16] : a === 1 && b < 8 ? [13, 0, 0] : a === 1 && b === 8 ? [0, 0, 0] : [null, null, null]
    const count = values.filter(p => p !== null).length
    const total = values.reduce<number>((sum, p) => sum + (p ?? 0), 0)
    relations.push({ giverId: a, receiverId: b, points: total, scoredShows: values.filter(p => p !== null && p > 0).length, opportunities: count, average: count ? total / count : null, twentyFives: values.filter(p => p === 25).length,
      shows: values.map((points, i) => ({ showId: i + 1, entryId: (i + 1) * 100 + b, state: points === null ? 'NOT_VOTED' : points === 0 ? 'OUTSIDE_TOP_15' : 'POINTS', ballotRank: points === 25 ? 1 : null, points })),
    })
  }
  relations.sort((a, b) => b.points - a.points)
  return {
    standings: { contestId, includedShowIds: [1, 2, 3, 4, 5], shows: [1, 2, 3, 4, 5].map(showId => ({ showId, showNumber: showId, name: `Show ${showId}`, status: 'CLOSED', closedAt: '2026-10-08T00:00:00Z' })), rows },
    entries: rows.flatMap(r => r.shows.map(c => ({ id: c.entryId, showId: c.showId, participationId: r.participationId, artist: c.artist, title: c.title }))), relations,
    topRelations: relations.filter(r => r.points > 0).map(r => ({ giverId: r.giverId, receiverId: r.receiverId })),
    profiles: rows.map(r => ({ participationId: r.participationId, topGiverIds: r.participationId === 1 ? [2] : [1], topReceiverIds: r.participationId === 1 ? [2, 3, 4, 5, 6, 7] : [], twentyFives: r.participationId === 2 ? 1 : 0, opportunities: 3, twentyFiveRate: r.participationId === 2 ? 1 / 3 : 0, countedEntries: 5, top15Count: 4, podiumCount: 2, wins: 1,
      pointRuns: [{ length: 2, firstShowId: 1, lastShowId: 2, showIds: [1, 2] }, { length: 2, firstShowId: 4, lastShowId: 5, showIds: [4, 5] }], podiumRuns: [{ length: 1, firstShowId: 1, lastShowId: 1, showIds: [1] }],
    })),
    pairs: [{ firstId: 1, secondId: 2, firstToSecond: 25, secondToFirst: 26, partnership: 25, difference: 1, strongerGiverId: 2, commonShowIds: [1, 3] }],
    entryAwards: [{ entryId: 102, twentyFives: 1, opportunities: 3, rate: 1 / 3 }],
    records: { partnerships: [{ giverId: 1, receiverId: 2 }], unrequited: [{ giverId: 1, receiverId: 2 }], twentyFiveParticipantIds: [2], twentyFiveEntryIds: [102], pointRunParticipantIds: [1, 2], podiumRunParticipantIds: [1], top15ParticipantIds: [1], podiumParticipantIds: [1] },
  }
}

function renderPage(view: 'participants' | 'relationships' | 'records', initial = `/statistics/${view}`) {
  return render(<MemoryRouter initialEntries={[initial]}><StatisticsPage view={view} /></MemoryRouter>)
}

async function closeDialog(user: ReturnType<typeof userEvent.setup>) {
  await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Schließen' }))
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
}

describe('S2-T06 statistics UI and shared evidence', () => {
  beforeEach(() => { state.contestId = 1; state.fetch.mockReset(); state.fetch.mockResolvedValue(fixture()) })

  it('shows boundary ties, known zeros, N/A, self cells and both direction bases', async () => {
    const user = userEvent.setup()
    renderPage('relationships')
    const top = await screen.findByRole('table', { name: 'Globale Top-Beziehungen mit Grenzgleichständen' })
    expect(within(top).getAllByRole('row')).toHaveLength(8) // Six outgoing and the reverse relation.
    const heatmap = screen.getByRole('table', { name: 'Geber-Empfänger-Heatmap' })
    expect(within(heatmap).getByRole('button', { name: 'Person 1 → Person 8: 0, Beziehungsdetails' })).toBeVisible()
    expect(within(heatmap).getByRole('button', { name: 'Person 3 → Person 1: N/A, Beziehungsdetails' })).toBeVisible()
    expect(within(heatmap).getByLabelText('Person 1: eigene Einreichung, nicht wählbar')).toBeVisible()
    await user.click(within(heatmap).getByRole('button', { name: 'Person 1 → Person 2: 45, Beziehungsdetails' }))
    const dialog = screen.getByRole('dialog')
    expect(dialog).toHaveTextContent('45 Stimmzettelpunkte')
    expect(dialog).toHaveTextContent('Gegenrichtung: 26 Stimmzettelpunkte')
    expect(dialog).toHaveTextContent('Gemeinsame beidseitige Basis: 2 Shows')
    expect(dialog).toHaveTextContent('Punktepartnerschaft: 25; Differenz: 1 in Richtung Person 2 → Person 1')
    expect(within(dialog).getByRole('table', { name: 'Einzelshowbelege der Beziehung' })).toHaveTextContent('Außerhalb Top 15')
    expect(dialog).toHaveTextContent('Nicht abgestimmt')
    expect(within(dialog).getByRole('link', { name: 'Show 1 · Show 1' })).toHaveAttribute('href', '/shows/1/evaluation?view=standings')
    await closeDialog(user)
    await user.click(screen.getByRole('button', { name: 'Vollständige Beziehungsliste anzeigen' }))
    const full = screen.getByRole('table', { name: 'Alle gerichteten Beziehungen einschließlich bekannter Nullen' })
    expect(within(full).getAllByRole('row')).toHaveLength(9)
    expect(within(full).getAllByText('0').length).toBeGreaterThan(0)
  })

  it('selects profiles with complete lists, contributions, history and personal record evidence', async () => {
    const user = userEvent.setup()
    renderPage('participants', '/statistics/participants?participant=1')
    expect(await screen.findByRole('heading', { name: 'Person 1 · Deutschland' })).toBeVisible()
    expect(screen.getByRole('table', { name: 'Top-Punkteempfänger mit Grenzgleichständen' }).querySelectorAll('tbody tr')).toHaveLength(6)
    await user.click(screen.getByRole('button', { name: 'Alle Empfänger anzeigen' }))
    expect(screen.getByRole('table', { name: 'Alle Punkteempfänger einschließlich bekannter Nullen' }).querySelectorAll('tbody tr')).toHaveLength(7)
    expect(screen.getByRole('img', { name: 'Gesamtwertungspunkte nach Showreihenfolge' })).toBeVisible()
    expect(screen.getByRole('img', { name: 'Contestplatz nach Showreihenfolge' })).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Punkteserie: 2 Shows' }))
    expect(screen.getByRole('dialog')).toHaveTextContent('Alle gleich langen maximalen Läufe')
    expect(screen.getByRole('dialog')).toHaveTextContent('Show 1 bis 2')
    expect(screen.getByRole('dialog')).toHaveTextContent('Show 4 bis 5')
    await closeDialog(user)
    await user.click(within(screen.getByRole('table', { name: 'Profilbeiträge' })).getAllByRole('button', { name: 'Beitragsdetails' })[0])
    expect(screen.getByRole('dialog')).toHaveTextContent('Beitragsdetails · Artist 1 – Song 1')
    await closeDialog(user)
    await user.click(screen.getByRole('combobox', { name: 'Teilnehmerprofil auswählen' }))
    await user.click(screen.getByRole('option', { name: 'Person 2 · Deutschland' }))
    expect(await screen.findByRole('heading', { name: 'Person 2 · Deutschland' })).toBeVisible()
    expect(screen.queryByRole('heading', { name: 'Person 1 · Deutschland' })).not.toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('opens all four record kinds and their show, contribution or run evidence', async () => {
    const user = userEvent.setup()
    renderPage('records')
    for (const title of ['Punktepartnerschaft', 'Unerwiderte Punkteliebe', 'König der 25er', 'Dauerbrenner']) expect(await screen.findByRole('heading', { name: title })).toBeVisible()
    await user.click(screen.getAllByRole('button', { name: 'Rekordbelege öffnen' })[0])
    expect(screen.getByRole('dialog')).toHaveTextContent('Gemeinsame beidseitige Basis: 2 Shows')
    await closeDialog(user)
    await user.click(screen.getByRole('button', { name: '25er-Beitragsbelege öffnen' }))
    expect(screen.getByRole('dialog')).toHaveTextContent('1 erhaltene 25er / 3 wählbare Bewertungen')
    expect(within(screen.getByRole('dialog')).getByRole('table', { name: 'Beitragswertungen' })).toHaveTextContent('Rang 1')
    await closeDialog(user)
    await user.click(screen.getAllByRole('button', { name: 'Serienbelege öffnen' })[0])
    expect(screen.getByRole('dialog')).toHaveTextContent('Gesamtwertungspunkte')
    await closeDialog(user)
    expect(screen.getByRole('navigation', { name: 'Contestauswertung' })).toHaveTextContent('Gesamtwertung')
  })

  it('discards late contest responses and old details on selection change', async () => {
    let first!: (data: Statistics) => void
    state.fetch.mockImplementation((id: number) => id === 1 ? new Promise<Statistics>(resolve => { first = resolve }) : Promise.resolve(fixture(2)))
    const view = renderPage('relationships')
    state.contestId = 2
    view.rerender(<MemoryRouter><StatisticsPage view="relationships" /></MemoryRouter>)
    await screen.findByRole('table', { name: 'Geber-Empfänger-Heatmap' })
    await act(async () => { const old = fixture(); old.standings.rows[0].displayName = 'Wrong contest'; first(old) })
    expect(screen.queryByText('Wrong contest')).not.toBeInTheDocument()
    expect(screen.getByText('CSC 2')).toBeVisible()
  })

  it('replaces the entire snapshot on refresh even with unchanged includedShowIds', async () => {
    const user = userEvent.setup()
    renderPage('relationships')
    await user.click(await screen.findByRole('button', { name: 'Person 1 → Person 2: 45, Beziehungsdetails' }))
    // Close through an external focus refresh, as after correcting or restoring in another tab.
    let latest!: (data: Statistics) => void
    state.fetch.mockImplementation(() => new Promise<Statistics>(resolve => { latest = resolve }))
    await act(async () => { window.dispatchEvent(new Event('focus')) })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.queryByRole('table', { name: 'Geber-Empfänger-Heatmap' })).not.toBeInTheDocument()
    const changed = fixture()
    changed.relations.find(r => r.giverId === 1 && r.receiverId === 2)!.points = 50
    await act(async () => { latest(changed) })
    expect(await screen.findByRole('button', { name: 'Person 1 → Person 2: 50, Beziehungsdetails' })).toBeVisible()
  })

  it('ignores a late profile response after navigating to another profile', async () => {
    let resolveOld!: (data: Statistics) => void
    state.fetch.mockImplementationOnce(() => new Promise<Statistics>(resolve => { resolveOld = resolve }))
    function Go() { const navigate = useNavigate(); return <button onClick={() => navigate('/statistics/participants?participant=2')}>Switch profile</button> }
    render(<MemoryRouter initialEntries={['/statistics/participants?participant=1']}><Go /><Routes><Route path="/statistics/participants" element={<StatisticsPage view="participants" />} /></Routes></MemoryRouter>)
    await userEvent.setup().click(screen.getByRole('button', { name: 'Switch profile' }))
    expect(await screen.findByRole('heading', { name: 'Person 2 · Deutschland' })).toBeVisible()
    await act(async () => { resolveOld(fixture()) })
    expect(screen.queryByRole('heading', { name: 'Person 1 · Deutschland' })).not.toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('keeps a 100 by 100 matrix bounded and reaches long-named giver and recipient 100', async () => {
    const user = userEvent.setup()
    const data = fixture(1, 100)
    data.standings.rows[99].displayName = 'Very long synthetic participant name with unequivocal identity 100'
    const open = vi.fn()
    render(<MemoryRouter><Heatmap data={data} open={open} /></MemoryRouter>)
    const table = screen.getByRole('table', { name: 'Geber-Empfänger-Heatmap' })
    expect(table.querySelectorAll('tbody tr')).toHaveLength(10)
    expect(table.querySelectorAll('tbody td')).toHaveLength(100)
    await user.click(screen.getByRole('button', { name: 'Weitere Geberzeilen' }))
    expect(screen.getByText('Geberzeilen: 11–20 von 100')).toBeVisible()
    for (const label of ['Geber', 'Empfänger']) {
      await user.type(screen.getByRole('combobox', { name: `${label} in Heatmap finden` }), 'identity 100')
      await user.click(screen.getByRole('option', { name: 'Very long synthetic participant name with unequivocal identity 100' }))
    }
    expect(screen.getByText('Geberzeilen: 91–100 von 100')).toBeVisible()
    expect(screen.getByText('Empfängerspalten: 91–100 von 100')).toBeVisible()
    await user.click(within(table).getByRole('button', { name: 'Very long synthetic participant name with unequivocal identity 100 → Person 99: N/A, Beziehungsdetails' }))
    expect(open).toHaveBeenCalledWith({ giverId: 100, receiverId: 99 })
    expect(table.querySelectorAll('tbody td')).toHaveLength(100)
  })

  it('shows empty records and fewer than five relationships without invented winners', async () => {
    const data = fixture()
    data.standings.includedShowIds = []
    data.records = { partnerships: [], unrequited: [], twentyFiveParticipantIds: [], twentyFiveEntryIds: [], pointRunParticipantIds: [], podiumRunParticipantIds: [], top15ParticipantIds: [], podiumParticipantIds: [] }
    state.fetch.mockResolvedValue(data)
    renderPage('records')
    expect(await screen.findByText(/Noch keine gewertete Show/)).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Rekordbelege öffnen' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '25er-Beitragsbelege öffnen' })).not.toBeInTheDocument()
  })
})
