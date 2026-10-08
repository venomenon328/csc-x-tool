import { Alert, Autocomplete, Box, Button, Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography } from '@mui/material'
import { useEffect, useState, type ReactNode } from 'react'
import { fetchBallotImpact, type BallotImpact, type NearMiss, type Statistics } from './api'
import { PersonLink } from './RelationshipViews'
import { nameOf } from './labels'

function Paged<T>({ title, values, render }: { title: string, values: T[], render: (value: T) => ReactNode }) {
  const [page, setPage] = useState(0)
  return <Stack spacing={1}><Typography component="h3" variant="h6">{title}</Typography>
    {!values.length && <Typography>Keine passende Grenzgruppe. Es gibt keinen Rekordgewinner.</Typography>}
    {values.slice(page * 25, (page + 1) * 25).map((value, i) => <Box key={page * 25 + i}>{render(value)}</Box>)}
    {values.length > 25 && <Stack direction="row" spacing={1}><Button aria-label={`Vorherige ${title}`} disabled={!page} onClick={() => setPage(p => p - 1)}>Zurück</Button><Typography>{page * 25 + 1}–{Math.min(values.length, (page + 1) * 25)} von {values.length}</Typography><Button aria-label={`Weitere ${title}`} disabled={(page + 1) * 25 >= values.length} onClick={() => setPage(p => p + 1)}>Weiter</Button></Stack>}
  </Stack>
}

function CaseLine({ data, value, openEntry }: { data: Statistics, value: NearMiss, openEntry: (id: number) => void }) {
  const entry = data.entries.find(e => e.id === value.entryId)!
  return <><Typography>Show {data.standings.shows.find(s => s.showId === value.showId)?.showNumber} · {entry.artist} – {entry.title}</Typography>
    <PersonLink data={data} id={value.participationId} /><Typography>Showrang {value.showRank} · {value.ballotPoints} Stimmzettelpunkte · {value.contestPoints} Gesamtwertungspunkte · Abstand +{value.gap} Stimmzettelpunkte</Typography>
    <Button onClick={() => openEntry(value.entryId)}>Grenzfall und Beitragsdetails</Button></>
}

export function NearMissView({ data, participantId, openEntry }: { data: Statistics, participantId?: number, openEntry: (id: number) => void }) {
  const [all, setAll] = useState(false)
  const [selectedFrequencyId, setSelectedFrequencyId] = useState<number | null>(null)
  const n = data.nearMisses
  const personal = participantId !== undefined
  const frequencies = n.frequencies.filter(f => personal ? f.participationId === participantId : all || n.frequencyWinnerIds.includes(f.participationId))
  const cases = n.cases.filter(c => personal ? c.participationId === participantId : all || n.smallestGapEntryIds.includes(c.entryId))
  const selectedFrequency = n.frequencies.find(f => f.participationId === selectedFrequencyId)
  const selectedEntryIds = new Set(selectedFrequency?.entryIds ?? [])
  const selectedCases = selectedFrequency ? n.cases.filter(c => c.participationId === selectedFrequency.participationId && selectedEntryIds.has(c.entryId)) : []
  return <Paper component="section" sx={{ p: 2, overflowWrap: 'anywhere' }}><Stack spacing={2}>
    <Typography component="h2" variant="h5">Knapp daneben ist auch vorbei</Typography>
    <Typography>Erste tatsächliche Showranggruppe größer als 15. Abstand zur letzten punktberechtigten Ranggruppe in Stimmzettelpunkten; alle Gleichstände zählen. Keine künstlichen Nullabstände.</Typography>
    {!personal && <Button onClick={() => { setSelectedFrequencyId(null); setAll(v => !v) }}>{all ? 'Grenzfallrekorde anzeigen' : 'Alle Grenzfälle anzeigen'}</Button>}
    <Paged key={`frequency-${all}`} title={personal ? 'Persönliche Häufigkeit' : 'Häufigste knappe Nichtqualifikationen'} values={frequencies} render={f => <><PersonLink data={data} id={f.participationId} /><Typography>{f.count} Fälle als bestplatzierter Beitrag ohne Gesamtwertungspunkte</Typography>
      {!personal && <Button aria-label={`Showbelege von ${nameOf(data, f.participationId)} ${selectedFrequencyId === f.participationId ? 'schließen' : 'anzeigen'}`} onClick={() => setSelectedFrequencyId(id => id === f.participationId ? null : f.participationId)}>{selectedFrequencyId === f.participationId ? 'Showbelege schließen' : 'Showbelege anzeigen'}</Button>}
    </>} />
    {!personal && selectedFrequency && <Box component="section" aria-label={`Showbelege von ${nameOf(data, selectedFrequency.participationId)}`} sx={{ p: 2, border: 1, borderColor: 'divider', borderRadius: 1 }}>
      <Stack spacing={2}>
        <Typography component="h3" variant="h6">Showbelege · {nameOf(data, selectedFrequency.participationId)} · {selectedFrequency.count} knappe Nichtqualifikationen</Typography>
        <Paged key={selectedFrequency.participationId} title="Show-, Rang- und Abstandsbelege" values={selectedCases} render={c => <CaseLine data={data} value={c} openEntry={openEntry} />} />
        <Button onClick={() => setSelectedFrequencyId(null)}>Ausgewählte Showbelege schließen</Button>
      </Stack>
    </Box>}
    <Paged key={`cases-${all}-${participantId}`} title={personal ? 'Persönliche Grenzfälle' : all ? 'Alle Show- und Beitragsfälle' : 'Kleinster positiver Abstand'} values={cases} render={c => <CaseLine data={data} value={c} openEntry={openEntry} />} />
  </Stack></Paper>
}
export function NearMissDetail({ data, entryId, openEntry }: { data: Statistics, entryId: number, openEntry: (id: number) => void }) {
  const c = data.nearMisses.cases.find(v => v.entryId === entryId)
  if (!c) return null
  return <Box><Typography component="h3" variant="h6">Knapp daneben ist auch vorbei · Grenzgruppen</Typography>
    <Typography>Erste Nichtpunktgruppe: Rang {c.showRank} · {c.ballotPoints} Stimmzettelpunkte · {c.contestPoints} Gesamtwertungspunkte. Positiver Abstand: +{c.gap} Stimmzettelpunkte.</Typography>
    <Paged title="Letzte punktberechtigte Ranggruppe" values={c.lastPointGroup} render={b => {
      const e = data.entries.find(v => v.id === b.entryId)!
      return <><Typography>{nameOf(data, b.participationId)} · {e.artist} – {e.title} · Rang {b.showRank} · {b.ballotPoints} Stimmzettelpunkte · {b.contestPoints} Gesamtwertungspunkte</Typography><Button onClick={() => openEntry(b.entryId)}>Grenzbeitrag öffnen</Button></>
    }} />
    <Paged title="Erste nicht punktberechtigte Ranggruppe" values={data.nearMisses.cases.filter(v => v.showId === c.showId)} render={v => <CaseLine data={data} value={v} openEntry={openEntry} />} />
  </Box>
}

/** Only selection candidates come from statistics. Every displayed comparison uses the new response. */
export function BallotImpactView({ data, active, select, leave }: { data: Statistics, active: { showId: number, ballotId: number } | null, select: (value: { showId: number, ballotId: number } | null) => void, leave: () => void }) {
  const [showId, setShowId] = useState<number | null>(null)
  const show = data.standings.shows.find(s => s.showId === showId) ?? null
  const ballots = data.preferences.showBases.find(s => s.showId === showId)?.ballots ?? []
  const ballot = ballots.find(b => b.ballotId === active?.ballotId) ?? null
  return <Paper component="section" sx={{ p: 2 }}><Stack spacing={2}>
    <Typography component="h2" variant="h5">Zünglein an der Waage</Typography>
    <Typography>Hypothetisch genau eine gültige veröffentlichte Stimme entfernen. Die Einreichung des Abstimmenden bleibt im Feld. Tatsächliche Stimmzettel, Abschlüsse, Snapshots, Gesamtwertung und Rekorde bleiben erhalten.</Typography>
    <Autocomplete options={data.standings.shows.filter(s => s.status === 'CLOSED')} value={show} getOptionKey={s => s.showId} getOptionLabel={s => `Show ${s.showNumber} · ${s.name}`} isOptionEqualToValue={(a,b) => a.showId === b.showId} onChange={(_, s) => { setShowId(s?.showId ?? null); select(null) }} renderInput={p => <TextField {...p} label="Show für Gegenrechnung" />} />
    <Autocomplete options={ballots} value={ballot} disabled={!show} getOptionKey={b => b.ballotId} getOptionLabel={b => nameOf(data, b.participationId)} isOptionEqualToValue={(a,b) => a.ballotId === b.ballotId} onChange={(_, b) => select(b && show ? { showId: show.showId, ballotId: b.ballotId } : null)} renderInput={p => <TextField {...p} label="Stimme hypothetisch entfernen" />} />
    {!data.standings.includedShowIds.length && <Typography>Keine abgeschlossene Show für eine Gegenrechnung.</Typography>}
    <Typography color="text.secondary">Die Auswahl wird bei jedem Abruf serverseitig im aktuellen Datenstand geprüft. Der Vergleich lädt seinen echten Stand erneut.</Typography>
    {active && <ImpactResult key={`${data.standings.contestId}-${active.showId}-${active.ballotId}`} contestId={data.standings.contestId} showId={active.showId} ballotId={active.ballotId} />}
    <Button onClick={leave}>Zur echten Ansicht zurückkehren</Button>
  </Stack></Paper>
}

function ImpactResult({ contestId, showId, ballotId }: { contestId: number, showId: number, ballotId: number }) {
  const [result, setResult] = useState<BallotImpact | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [page, setPage] = useState(0)
  const [all, setAll] = useState(false)
  useEffect(() => {
    let cancelled = false
    void fetchBallotImpact(contestId, showId, ballotId).then(value => {
      if (!cancelled && value.standings.contestId === contestId && value.showId === showId && value.removedBallot.ballotId === ballotId) setResult(value)
    }).catch((e: unknown) => { if (!cancelled) setError(e instanceof Error ? e.message : 'Gegenrechnung nicht verfügbar.') })
    return () => { cancelled = true }
  }, [contestId, showId, ballotId])
  if (error) return <Alert severity="error">{error}</Alert>
  if (!result) return <Typography>Hypothetische Gegenrechnung wird geladen …</Typography>
  const name = (id: number) => result.standings.rows.find(r => r.participationId === id)?.displayName ?? String(id)
  const winners = (ids: number[]) => ids.map(id => {
    const c = result.comparisons.find(v => v.entryId === id)!
    return `${name(c.participationId)} · ${c.artist} – ${c.title}`
  }).join(' · ')
  const rows = all ? result.comparisons : result.comparisons.filter(c => c.delta === null || Object.values(c.delta).some(v => v !== 0))
  const signed = (value: number) => value > 0 ? `+${value}` : String(value)
  return <Stack spacing={2} sx={{ overflowWrap: 'anywhere' }}>
    <Alert severity="warning">Hypothetischer Vergleich · keine Änderung des tatsächlichen Ergebnisses</Alert>
    <Typography>Frisch geladene Basis: {result.standings.includedShowIds.length} abgeschlossene Shows ({result.standings.shows.filter(s => result.standings.includedShowIds.includes(s.showId)).map(s => `Show ${s.showNumber} · ${s.name}`).join(', ')}). Show {result.standings.shows.find(s => s.showId === showId)?.showNumber}: {result.validBallots} gültige Stimmen, hypothetisch {result.remainingBallots}. Entfernte Stimme: {name(result.removedBallot.participationId)}.</Typography>
    <Typography>Echte Siegergruppe: {winners(result.actualWinnerEntryIds)}</Typography>
    {result.state === 'NO_REMAINING_BALLOT' ? <Alert severity="info">Keine wertbare Gegenrechnung ohne diesen Stimmzettel. Alternative Sieger, Showpunkte und Contestsummen: N/A.</Alert> : <Typography>Hypothetische Siegergruppe: {winners(result.hypotheticalWinnerEntryIds)}</Typography>}
    <Typography>Je Zelle: echt → hypothetisch (Differenz). Rangdifferenz positiv = Aufstieg; Punktedifferenz positiv = Zugewinn. Contestsumme = echte Summe − echte Showpunkte + hypothetische Showpunkte; andere Shows unverändert.</Typography>
    <Button onClick={() => { setAll(v => !v); setPage(0) }}>{all ? 'Nur betroffene Beiträge anzeigen' : 'Alle Beiträge anzeigen'}</Button>
    <TableContainer><Table size="small" aria-label="Echtes und hypothetisches Ergebnis"><TableHead><TableRow><TableCell>Teilnehmer / Beitrag</TableCell><TableCell>Stimmzettelpunkte</TableCell><TableCell>Showrang</TableCell><TableCell>Gesamtwertungspunkte</TableCell><TableCell>Contestsumme</TableCell><TableCell>Punktegrenze</TableCell></TableRow></TableHead><TableBody>{rows.slice(page * 25, (page + 1) * 25).map(c => <TableRow key={c.entryId}>
      <TableCell>{name(c.participationId)} · {c.artist} – {c.title}</TableCell>
      {(['ballotPoints', 'showRank', 'contestPoints', 'contestTotal'] as const).map(k => <TableCell key={k}>{c.actual[k]} → {c.hypothetical ? `${c.hypothetical[k]} (${signed(c.delta![k])})` : 'N/A'}</TableCell>)}
      <TableCell>{({ ENTERED: 'Eintritt in Punkte', LEFT: 'Austritt aus Punkten', UNCHANGED: 'Unverändert', NOT_EVALUABLE: 'N/A' } as Record<string, string>)[c.boundaryChange]}</TableCell>
    </TableRow>)}</TableBody></Table></TableContainer>
    <Stack direction="row" spacing={1}><Button aria-label="Vorherige Vergleichsbeiträge" disabled={!page} onClick={() => setPage(p => p - 1)}>Zurück</Button><Typography>{rows.length ? page * 25 + 1 : 0}–{Math.min(rows.length, (page + 1) * 25)} von {rows.length} Beiträgen</Typography><Button aria-label="Weitere Vergleichsbeiträge" disabled={(page + 1) * 25 >= rows.length} onClick={() => setPage(p => p + 1)}>Weiter</Button></Stack>
    <Typography component="h3" variant="h6">Punktebeiträge der hypothetisch entfernten Stimme</Typography>
    <TableContainer><Table size="small" aria-label="Entfernte Stimme"><TableHead><TableRow><TableCell>Rang</TableCell><TableCell>Beitrag</TableCell><TableCell>Stimmzettelpunkte</TableCell></TableRow></TableHead><TableBody>{result.removedBallot.positions.map(p => {
      const c = result.comparisons.find(v => v.entryId === p.entryId)!
      return <TableRow key={p.rank}><TableCell>{p.rank}</TableCell><TableCell>{name(c.participationId)} · {c.artist} – {c.title}</TableCell><TableCell>{p.points}</TableCell></TableRow>
    })}</TableBody></Table></TableContainer>
  </Stack>
}
