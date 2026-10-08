import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { StatisticsPage } from './StatisticsPage'
import { Heatmap } from './RelationshipViews'
import type { Statistics, Relation, Metric } from './api'
import { PreferenceRecords } from './PreferenceViews'
import type * as statisticsApi from './api'

const state = vi.hoisted(() => ({ contestId: 1, fetch: vi.fn() }))
vi.mock('../contests/ContestContext', () => ({ useContest: () => ({ selectedContestId: state.contestId, selectedContest: { name: `CSC ${state.contestId}` } }) }))
vi.mock('./api', async original => ({ ...await original<typeof statisticsApi>(), fetchStatistics: state.fetch }))

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
    preferences: { showBases: [], pairs: [], parallelOrder: [], entries: [], audienceOrder: [], polarizationOrder: [], exclusiveOrder: [], participants: rows.map(r => ({ participationId: r.participationId, consensus: missing('Keine berechenbare Show'), comparedShows: 0, shows: [], exclusivePoints: 0, exclusiveEntryIds: [], exclusiveTwentyFiveEntryIds: [] })), records: { twins: [], parallels: [], audienceEntryIds: [], polarizationEntryIds: [], exclusiveParticipantIds: [], consensusParticipantIds: [] } },
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

function metric(n: number, d = 1): Metric { return { numerator: String(n), denominator: String(d), value: n / d, reason: null } }
function missing(reason: string): Metric { return { numerator: null, denominator: null, value: null, reason } }

/** Synthetic complete 15-position / 140-point ballots, shared once per show. */
function preferenceFixture(size = 40, showCount = 2): Statistics {
  const d = fixture(1, size)
  const keys = [0,1,2,3,4,5,6,7,8,9,10,11,13,16,20,25]
  const points = [25,20,16,13,11,10,9,8,7,6,5,4,3,2,1]
  d.standings.shows = Array.from({ length: showCount }, (_, i) => ({ showId: i + 1, showNumber: i + 1, name: `Show ${i + 1}`, status: 'CLOSED', closedAt: '2026-10-08T00:00:00Z' }))
  d.standings.includedShowIds = d.standings.shows.map(s => s.showId)
  d.entries = d.standings.shows.flatMap(s => d.standings.rows.map(r => ({ id: s.showId * 1000 + r.participationId, showId: s.showId, participationId: r.participationId, artist: `Artist ${r.participationId}`, title: `Song ${r.participationId}` })))
  d.preferences.showBases = d.standings.shows.map(s => ({ showId: s.showId, ballots: [1,2].map(id => ({ ballotId: s.showId * 1000 + id, participationId: id, positions: points.map((p, i) => ({ entryId: s.showId * 1000 + i + 3, rank: i + 1, points: p })) })) }))
  const pairs = []
  for (let a = 1; a <= size; a++) for (let b = a + 1; b <= size; b++) pairs.push({ firstId: a, secondId: b, similarity: a === 1 && b === 2 ? metric(1) : missing('Keine berechenbare gemeinsame Show'), comparedShows: a === 1 && b === 2 ? showCount : 0,
    shows: d.standings.shows.map(s => ({ showId: s.showId, similarity: a === 1 && b === 2 ? metric(1) : missing('Keine gemeinsame vollständige Abgabe'), comparisonEntries: size - 2, excludedEntryIds: [s.showId * 1000 + a, s.showId * 1000 + b] })),
  })
  d.preferences.pairs = pairs
  d.preferences.parallelOrder = pairs.map(p => ({ giverId: p.firstId, receiverId: p.secondId }))
  d.preferences.entries = d.entries.map(e => {
    const p = points[e.participationId! - 3] ?? 0
    const n = e.participationId! <= 2 ? 1 : 2
    return { entryId: e.id, evaluations: n, positiveEvaluations: p > 0 ? n : 0, sumPoints: n * p, twentyFives: p === 25 ? n : 0, audienceRate: metric(p > 0 ? 1 : 0), variance: metric(0), standardDeviation: 0, polarizationEligible: n >= 2, histogram: keys.map(k => ({ points: k, count: k === p ? n : 0 })), exclusiveGiverId: null, exclusivePoints: 0 }
  })
  d.preferences.audienceOrder = [...d.preferences.entries].sort((a,b) => b.audienceRate.value! - a.audienceRate.value!).map(e => e.entryId)
  d.preferences.polarizationOrder = d.preferences.entries.filter(e => e.polarizationEligible).map(e => e.entryId)
  d.preferences.participants = d.standings.rows.map(r => ({ participationId: r.participationId, consensus: r.participationId <= 2 ? metric(1) : missing('Kein vollständiger eigener Stimmzettel'), comparedShows: r.participationId <= 2 ? showCount : 0, shows: d.standings.shows.map(s => ({ showId: s.showId, similarity: r.participationId <= 2 ? metric(1) : missing('Kein vollständiger eigener Stimmzettel'), otherBallots: r.participationId <= 2 ? 1 : 2, comparisonEntries: size - 1, excludedEntryId: s.showId * 1000 + r.participationId, ownPointSum: r.participationId <= 2 ? 140 : 0, fieldPointSum: r.participationId <= 2 ? 140 : 280 })), exclusivePoints: 0, exclusiveEntryIds: [], exclusiveTwentyFiveEntryIds: [] }))
  d.preferences.records = { twins: [{ giverId: 1, receiverId: 2 }], parallels: [{ giverId: 1, receiverId: 2 }], audienceEntryIds: d.preferences.entries.filter(e => e.positiveEvaluations > 0).map(e => e.entryId), polarizationEntryIds: d.preferences.polarizationOrder, exclusiveParticipantIds: [], consensusParticipantIds: [1,2] }
  // S1 and S2 use the same entries and two complete votes; no stale IDs from the legacy UI fixture.
  d.entryAwards = d.preferences.entries.map(e => ({ entryId: e.entryId, twentyFives: e.twentyFives, opportunities: e.evaluations, rate: e.twentyFives / e.evaluations }))
  for (const r of d.standings.rows) {
    const rank = r.participationId >= 3 && r.participationId <= 17 ? r.participationId - 2 : 16
    const p = points[rank - 1] ?? 0
    r.rank = rank; r.totalPoints = p * showCount
    r.shows = d.standings.shows.map(s => ({ showId: s.showId, state: 'SCORED_ENTRY', entryId: s.showId * 1000 + r.participationId, artist: `Artist ${r.participationId}`, title: `Song ${r.participationId}`, ballotPoints: p * 2, showRank: rank, contestPoints: p }))
    r.history = d.standings.shows.map(s => ({ showId: s.showId, showNumber: s.showNumber, included: true, totalPoints: p * s.showNumber, rank, rankChange: null }))
  }
  d.relations = d.relations.map(r => {
    const p = r.giverId <= 2 ? points[r.receiverId - 3] ?? 0 : null
    return { ...r, points: (p ?? 0) * showCount, scoredShows: p ? showCount : 0, opportunities: p === null ? 0 : showCount, average: p, twentyFives: p === 25 ? showCount : 0,
      shows: d.standings.shows.map(s => ({ showId: s.showId, entryId: s.showId * 1000 + r.receiverId, state: p === null ? 'NOT_VOTED' : p === 0 ? 'OUTSIDE_TOP_15' : 'POINTS', points: p, ballotRank: p ? r.receiverId - 2 : null })),
    }
  }).sort((a,b) => b.points - a.points)
  d.topRelations = d.relations.filter(r => r.receiverId <= 7 && r.points > 0).map(r => ({ giverId: r.giverId, receiverId: r.receiverId }))
  d.pairs = [{ firstId: 1, secondId: 2, firstToSecond: 0, secondToFirst: 0, partnership: 0, difference: 0, strongerGiverId: null, commonShowIds: d.standings.includedShowIds }]
  d.profiles = d.profiles.map(p => {
    const own = d.preferences.entries.filter(e => d.entries.find(v => v.id === e.entryId)?.participationId === p.participationId)
    const row = d.standings.rows.find(r => r.participationId === p.participationId)!
    const run = { length: showCount, firstShowId: 1, lastShowId: showCount, showIds: d.standings.includedShowIds }
    return { ...p, topGiverIds: row.rank! <= 15 ? [1,2] : [], topReceiverIds: p.participationId <= 2 ? [3,4,5,6,7] : [], twentyFives: p.participationId === 3 ? 2 * showCount : 0, opportunities: own.reduce((sum,e) => sum + e.evaluations,0), twentyFiveRate: p.participationId === 3 ? 1 : 0, countedEntries: showCount, top15Count: row.rank! <= 15 ? showCount : 0, podiumCount: row.rank! <= 3 ? showCount : 0, wins: row.rank === 1 ? showCount : 0, pointRuns: row.rank! <= 15 ? [run] : [], podiumRuns: row.rank! <= 3 ? [run] : [] }
  })
  d.records = { partnerships: [], unrequited: [], twentyFiveParticipantIds: [3], twentyFiveEntryIds: d.entryAwards.filter(a => a.twentyFives > 0).map(a => a.entryId), pointRunParticipantIds: Array.from({ length: 15 },(_,i) => i + 3), podiumRunParticipantIds: [3,4,5], top15ParticipantIds: Array.from({ length: 15 },(_,i) => i + 3), podiumParticipantIds: [3,4,5] }
  return d
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

  it('shows empty records without invented winners', async () => {
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

describe('S3a-T07 preference views, shared drilldown and freshness', () => {
  beforeEach(() => { state.contestId = 1; state.fetch.mockReset(); state.fetch.mockResolvedValue(preferenceFixture()) })

  it('opens all six kinds, excluded own songs, complete pair evidence, histogram and consensus ballots', async () => {
    const user = userEvent.setup()
    renderPage('records')
    for (const title of ['Geschmackszwillinge', 'Musikalische Paralleluniversen', 'Publikumsliebling', 'Kult oder Skip', 'Allein auf weiter Flur', 'Konsensbeauftragter']) expect(await screen.findByRole('heading', { name: title })).toBeVisible()
    await user.click(screen.getAllByRole('button', { name: 'Favoritenvergleich öffnen' })[0])
    let dialog = screen.getByRole('dialog')
    expect(dialog).toHaveTextContent('100 % · Basis: 2 Shows')
    expect(dialog).toHaveTextContent('Ausgeschlossene eigene Einreichungen: Artist 1 – Song 1; Artist 2 – Song 2')
    await user.click(within(dialog).getByRole('button', { name: 'Vergleich Show 1 öffnen' }))
    expect(within(dialog).getByRole('table', { name: 'Drittbeitragsvergleich' }).querySelectorAll('tbody tr')).toHaveLength(38)
    expect(dialog).toHaveTextContent('25 / Rang 1')
    expect(dialog).toHaveTextContent('0 · außerhalb Top 15')
    await closeDialog(user)
    await user.click(screen.getAllByRole('button', { name: 'Verteilung und Beitragsbelege öffnen' })[0])
    dialog = screen.getByRole('dialog')
    const histogram = within(dialog).getByRole('table', { name: 'Histogramm der Stimmzettelpunkte' })
    expect(histogram.querySelectorAll('tbody tr')).toHaveLength(16)
    expect(dialog).toHaveTextContent('2 / 2 · Basis: 2 Bewertungen · Summe 50 Stimmzettelpunkte')
    expect(dialog).toHaveTextContent('Varianz 0')
    expect(dialog).toHaveTextContent('Für Polarisierungsrekord geeignet')
    await closeDialog(user)
    await user.click(screen.getAllByRole('button', { name: 'Konsensbelege öffnen' })[0])
    dialog = screen.getByRole('dialog')
    expect(dialog).toHaveTextContent('Eigener Stimmzettel und eigene Einreichung entfallen')
    expect(dialog).toHaveTextContent('Eigene Punktsumme 140, übrige Feldsumme 140')
    await user.click(within(dialog).getByRole('button', { name: 'Stimmzettelbelege Show 1 öffnen' }))
    expect(within(dialog).queryAllByRole('table')).toHaveLength(0)
    await user.click(within(dialog).getByRole('button', { name: 'Stimmzettel von Person 1 öffnen' }))
    expect(within(dialog).getByRole('table', { name: 'Stimmzettel 1001' })).toHaveTextContent('25 / Rang 1')
    expect(dialog).toHaveTextContent('Eigener Stimmzettel · aus Feld entfernt')
    await closeDialog(user)
  })

  it('shows exclusive points, separately reachable exclusive 25s and single evaluation exclusion', async () => {
    const d = preferenceFixture()
    const person = d.preferences.participants[0]
    person.exclusivePoints = 38; person.exclusiveEntryIds = [2005,2006]; person.exclusiveTwentyFiveEntryIds = [2005]
    d.preferences.records.exclusiveParticipantIds = [1]
    d.preferences.exclusiveOrder = [1]
    for (const [id, points] of [[2005,25],[2006,13]]) {
      const e = d.preferences.entries.find(e => e.entryId === id)!
      e.exclusiveGiverId = 1; e.exclusivePoints = points
    }
    state.fetch.mockResolvedValue(d)
    const user = userEvent.setup()
    renderPage('records')
    await user.click(await screen.findByRole('button', { name: 'Exklusive Punkte und 25er öffnen' }))
    let dialog = screen.getByRole('dialog')
    expect(dialog).toHaveTextContent('38 exklusive Stimmzettelpunkte · 2 Beiträge · 1 exklusive 25er')
    expect(within(dialog).getAllByRole('button', { name: 'Exklusivbeitragsbelege öffnen' })).toHaveLength(2)
    await user.click(within(dialog).getByRole('button', { name: 'Nur exklusive 25er anzeigen' }))
    expect(within(dialog).getAllByRole('button', { name: 'Exklusivbeitragsbelege öffnen' })).toHaveLength(1)
    expect(dialog).toHaveTextContent('25 eigene Stimmzettelpunkte · Basis: 2 bekannte wählbare Bewertungen')
    await closeDialog(user)
    await user.click(screen.getByRole('button', { name: 'Vollständige Präferenzlisten anzeigen' }))
    await user.click(screen.getAllByRole('button', { name: 'Verteilung und Beitragsbelege öffnen' })[0])
    await closeDialog(user)
    // Open a one-evaluation own contribution directly through the personal profile.
    state.fetch.mockResolvedValue(d)
    const profile = renderPage('participants', '/statistics/participants?participant=1')
    await user.click((await screen.findAllByRole('button', { name: 'Beitragsdetails' }))[0])
    dialog = screen.getByRole('dialog')
    expect(dialog).toHaveTextContent('Basis: 1 Bewertungen')
    expect(dialog).toHaveTextContent('Kein Polarisierungsrekord: weniger als zwei Bewertungen')
    profile.unmount()
  })

  it('makes measured zero and N/A distinct and keeps all lists reachable with 100 names and 12 shows', async () => {
    const d = preferenceFixture(100,12)
    d.standings.rows[99].displayName = 'Very long synthetic preference participant 100'
    d.preferences.pairs[0].similarity = metric(0)
    d.preferences.records.twins = [{ giverId: 1,receiverId: 2 }]
    const open = vi.fn()
    render(<MemoryRouter><PreferenceRecords data={d} openPreference={open} openEntry={vi.fn()} /></MemoryRouter>)
    const user = userEvent.setup()
    expect(screen.getAllByText('0 % · Basis: 12 Shows')).toHaveLength(2)
    await user.click(screen.getByRole('button', { name: 'Vollständige Präferenzlisten anzeigen' }))
    expect(screen.getAllByText('Einträge 1–25 von 4950')).toHaveLength(2)
    await user.click(screen.getByRole('button', { name: 'Weitere Geschmackszwillinge' }))
    expect(screen.getByText('Einträge 26–50 von 4950')).toBeVisible()
    expect(screen.getAllByText(/N\/A · Keine berechenbare gemeinsame Show/).length).toBeGreaterThan(0)
    await user.click(screen.getByRole('button', { name: 'Weitere Konsensbeauftragter' }))
    await user.click(screen.getByRole('button', { name: 'Weitere Konsensbeauftragter' }))
    await user.click(screen.getByRole('button', { name: 'Weitere Konsensbeauftragter' }))
    expect(screen.getByRole('link', { name: 'Very long synthetic preference participant 100' })).toBeVisible()
    expect(screen.getByText('Einträge 76–100 von 100')).toBeVisible()
  })

  it('replaces new open details on focus and explicit refresh with unchanged show IDs', async () => {
    const user = userEvent.setup()
    renderPage('records')
    await user.click((await screen.findAllByRole('button', { name: 'Favoritenvergleich öffnen' }))[0])
    const changed = preferenceFixture()
    changed.preferences.pairs[0].similarity = metric(1,2)
    state.fetch.mockResolvedValue(changed)
    await act(async () => { window.dispatchEvent(new Event('focus')) })
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    await user.click((await screen.findAllByRole('button', { name: 'Favoritenvergleich öffnen' }))[0])
    expect(screen.getByRole('dialog')).toHaveTextContent('50 % · Basis: 2 Shows')
    await closeDialog(user)
    const refreshButton = screen.getByRole('button', { name: 'Statistiken aktualisieren' })
    await user.click(screen.getAllByRole('button', { name: 'Konsensbelege öffnen' })[0])
    fireEvent.click(refreshButton)
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('clears pair details on contest change and new details on profile change', async () => {
    const user = userEvent.setup()
    let navigateTo!: ReturnType<typeof useNavigate>
    function RoutedProfile() { navigateTo = useNavigate(); return <StatisticsPage view="participants" /> }
    const view = render(<MemoryRouter initialEntries={['/statistics/participants?participant=1']}><RoutedProfile /></MemoryRouter>)
    await user.click((await screen.findAllByRole('button', { name: 'Konsensbelege öffnen' }))[0])
    await act(async () => { await navigateTo('/statistics/participants?participant=2') })
    expect(await screen.findByRole('heading', { name: 'Person 2 · Deutschland' })).toBeVisible()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await user.click(screen.getAllByRole('button', { name: 'Favoritenvergleich öffnen' })[0])
    const other = preferenceFixture(); other.standings.contestId = 2
    state.fetch.mockResolvedValue(other); state.contestId = 2
    view.rerender(<MemoryRouter initialEntries={['/statistics/participants?participant=2']}><RoutedProfile /></MemoryRouter>)
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.getByText('CSC 2')).toBeVisible()
  })
})
