import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Typography } from '@mui/material'
import { useMemo, useState, type ReactNode } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { decimal, percent, type Metric, type SimilarityPair, type Statistics } from './api'
import { PersonLink } from './RelationshipViews'
import { nameOf } from './labels'

export type PreferenceEvidence = { kind: 'similarity', firstId: number, secondId: number } | { kind: 'consensus' | 'exclusive', id: number }
type Actions = { openPreference: (evidence: PreferenceEvidence) => void, openEntry: (id: number) => void }
const pairKey = (first: number, second: number) => `${first}-${second}`
const description = {
  twins: 'Größte gewichtete Favoritenüberschneidung auf gemeinsam wählbaren Drittbeiträgen. Beide eigenen Einreichungen entfallen. Shows zählen gleich viel.',
  parallels: 'Kleinste gewichtete Favoritenüberschneidung auf derselben Drittbeitragsbasis. 0 % ist eine gemessene Überschneidung; N/A bedeutet fehlende Vergleichsbasis.',
  audience: 'Anteil bepunktender an bekannten wählbaren vollständigen Stimmzetteln. Die Punkthöhe ist ergänzende Information und kein Tiebreak.',
  polarization: 'Populationsvarianz der tatsächlichen Stimmzettelpunkte einschließlich echter Nullen. Mindestens zwei Bewertungen für den Rekord; Null heißt außerhalb Top 15.',
  exclusive: 'Punkte für Beiträge mit genau einem positiven vollständigen Stimmzettel im gesamten eingeschlossenen Showfeld. Nichtabgaben sind keine Gegenmeinungen.',
  consensus: 'Gewichtete Favoritenüberschneidung mit dem übrigen Feld. Eigener Stimmzettel und eigene Einreichung entfallen; eigene Punkte und Feldsummen werden jeweils auf Summe 1 normiert. Shows zählen gleich viel.',
}

function MetricText({ metric }: { metric: Metric }) {
  return <>{percent(metric.value)}{metric.value === null && ` · ${metric.reason}`}</>
}
function ShowLink({ data, id }: { data: Statistics, id: number }) {
  const show = data.standings.shows.find(s => s.showId === id)
  return <Button component={RouterLink} to={`/shows/${id}/evaluation?view=standings`}>Show {show?.showNumber} · {show?.name}</Button>
}
function EntryText({ data, id }: { data: Statistics, id: number }) {
  const entry = data.entries.find(e => e.id === id)
  return <>{entry?.artist} – {entry?.title} · Show {data.standings.shows.find(s => s.showId === entry?.showId)?.showNumber}</>
}

/** Pagination limits rendering, never removes a mathematical tie from the supplied list. */
function EvidenceList<T>({ title, description: explanation, values, render }: { title: string, description: string, values: T[], render: (value: T) => ReactNode }) {
  const [page, setPage] = useState(0)
  return <Paper component="section" sx={{ p: 2, overflowWrap: 'anywhere' }}><Stack spacing={1}>
    <Typography component="h2" variant="h5">{title}</Typography><Typography>{explanation}</Typography>
    {!values.length && <Typography>Keine berechenbare Rekordbasis oder kein positives Ereignis.</Typography>}
    {values.slice(page * 25, (page + 1) * 25).map((v, i) => <Box key={page * 25 + i}>{render(v)}</Box>)}
    {values.length > 25 && <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 1 }}><Button aria-label={`Vorherige ${title}`} disabled={page === 0} onClick={() => setPage(p => p - 1)}>Zurück</Button><Typography>Einträge {page * 25 + 1}–{Math.min((page + 1) * 25, values.length)} von {values.length}</Typography><Button aria-label={`Weitere ${title}`} disabled={(page + 1) * 25 >= values.length} onClick={() => setPage(p => p + 1)}>Weiter</Button></Stack>}
  </Stack></Paper>
}
function PairLine({ data, pair, open }: { data: Statistics, pair: SimilarityPair, open: Actions['openPreference'] }) {
  return <><PersonLink data={data} id={pair.firstId} /> ↔ <PersonLink data={data} id={pair.secondId} /><Typography><MetricText metric={pair.similarity} /> · Basis: {pair.comparedShows} {pair.comparedShows === 1 ? 'Show' : 'Shows'}</Typography><Button onClick={() => open({ kind: 'similarity', firstId: pair.firstId, secondId: pair.secondId })}>Favoritenvergleich öffnen</Button></>
}

export function PreferenceRecords({ data, openPreference, openEntry }: { data: Statistics } & Actions) {
  const [all, setAll] = useState(false)
  const p = data.preferences
  const records = p.records
  const pairs = useMemo(() => new Map(p.pairs.map(pair => [pairKey(pair.firstId, pair.secondId), pair])), [p])
  const entries = useMemo(() => new Map(p.entries.map(entry => [entry.entryId, entry])), [p])
  function pairList(title: string, explanation: string, ids: { giverId: number, receiverId: number }[]) {
    return <EvidenceList key={`${title}-${all}`} title={title} description={explanation} values={ids} render={id => <PairLine data={data} pair={pairs.get(pairKey(id.giverId, id.receiverId))!} open={openPreference} />} />
  }
  function entryList(title: string, explanation: string, ids: number[], variance: boolean) {
    return <EvidenceList key={`${title}-${all}`} title={title} description={explanation} values={ids} render={id => {
      const entry = entries.get(id)!
      return <><Typography><EntryText data={data} id={id} /></Typography><Typography>{variance ? `Varianz ${decimal(entry.variance.value)} · Standardabweichung ${decimal(entry.standardDeviation)}` : `${percent(entry.audienceRate.value)} · ${entry.positiveEvaluations} / ${entry.evaluations} bepunktende Bewertungen`} · Basis: {entry.evaluations} Bewertungen · {entry.sumPoints} Stimmzettelpunkte · {entry.twentyFives} 25er</Typography><Button onClick={() => openEntry(id)}>Verteilung und Beitragsbelege öffnen</Button></>
    }} />
  }
  return <Stack spacing={2}>
    <Button onClick={() => setAll(v => !v)}>{all ? 'S3a-Rekordgewinner anzeigen' : 'Vollständige S3a-Listen anzeigen'}</Button>
    <Typography>Alle mathematischen Rekordgleichstände bleiben gleichberechtigt. Rundung dient nur der Anzeige. Kleine Basis beschreibt beobachtete Top-15-Wertungen, keine Absicht oder Absprachen.</Typography>
    {pairList('Geschmackszwillinge', description.twins, all ? p.pairs.map(pair => ({ giverId: pair.firstId, receiverId: pair.secondId })) : records.twins)}
    {pairList('Musikalische Paralleluniversen', description.parallels, all ? p.parallelOrder : records.parallels)}
    {entryList('Publikumsliebling', description.audience, all ? p.audienceOrder : records.audienceEntryIds, false)}
    {entryList('Kult oder Skip', description.polarization, all ? p.polarizationOrder : records.polarizationEntryIds, true)}
    <EvidenceList key={`exclusive-${all}`} title="Allein auf weiter Flur" description={description.exclusive} values={all ? p.exclusiveOrder : records.exclusiveParticipantIds} render={id => {
      const person = p.participants.find(v => v.participationId === id)!
      return <><PersonLink data={data} id={id} /><Typography>{person.exclusivePoints} exklusive Stimmzettelpunkte · {person.exclusiveEntryIds.length} Beiträge · {person.exclusiveTwentyFiveEntryIds.length} exklusive 25er</Typography><Button onClick={() => openPreference({ kind: 'exclusive', id })}>Exklusive Punkte und 25er öffnen</Button></>
    }} />
    <EvidenceList key={`consensus-${all}`} title="Konsensbeauftragter" description={description.consensus} values={all ? p.participants.map(v => v.participationId) : records.consensusParticipantIds} render={id => {
      const person = p.participants.find(v => v.participationId === id)!
      return <><PersonLink data={data} id={id} /><Typography><MetricText metric={person.consensus} /> · Basis: {person.comparedShows} Shows</Typography><Button onClick={() => openPreference({ kind: 'consensus', id })}>Konsensbelege öffnen</Button></>
    }} />
  </Stack>
}

export function PreferenceProfile({ data, id, openPreference, openEntry }: { data: Statistics, id: number } & Actions) {
  const person = data.preferences.participants.find(p => p.participationId === id)!
  const pairs = data.preferences.pairs.filter(p => p.firstId === id || p.secondId === id)
  const index = new Map(pairs.map(p => [pairKey(p.firstId, p.secondId), p]))
  const parallels = data.preferences.parallelOrder.flatMap(d => { const p = index.get(pairKey(d.giverId, d.receiverId)); return p ? [p] : [] })
  return <Stack spacing={2}>
    <EvidenceList title="Geschmackszwillinge im Profil" description={description.twins} values={pairs} render={p => <PairLine data={data} pair={p} open={openPreference} />} />
    <EvidenceList title="Musikalische Paralleluniversen im Profil" description={description.parallels} values={parallels} render={p => <PairLine data={data} pair={p} open={openPreference} />} />
    <Paper sx={{ p: 2 }}><Typography component="h2" variant="h5">Konsensbeauftragter im Profil</Typography><Typography>{description.consensus}</Typography><Typography><MetricText metric={person.consensus} /> · Basis: {person.comparedShows} Shows</Typography><Button onClick={() => openPreference({ kind: 'consensus', id })}>Konsensbelege öffnen</Button></Paper>
    <Paper sx={{ p: 2 }}><Typography component="h2" variant="h5">Allein auf weiter Flur im Profil</Typography><Typography>{description.exclusive}</Typography><Typography>{person.exclusivePoints} exklusive Stimmzettelpunkte · {person.exclusiveEntryIds.length} Beiträge · {person.exclusiveTwentyFiveEntryIds.length} exklusive 25er</Typography><Button onClick={() => openPreference({ kind: 'exclusive', id })}>Exklusive Punkte und 25er öffnen</Button></Paper>
    <EvidenceList title="Publikumsliebling und Kult oder Skip im Profil" description={`${description.audience} ${description.polarization}`} values={data.preferences.entries.filter(e => data.entries.find(v => v.id === e.entryId)?.participationId === id)} render={e => <><Typography><EntryText data={data} id={e.entryId} /> · Publikumsquote {percent(e.audienceRate.value)} · {e.positiveEvaluations} / {e.evaluations} · Varianz {decimal(e.variance.value)} · Basis: {e.evaluations} Bewertungen{!e.polarizationEligible && ' · Kein Polarisierungsrekord: weniger als zwei Bewertungen'}</Typography><Button onClick={() => openEntry(e.entryId)}>Verteilung und Beitragsbelege öffnen</Button></>} />
  </Stack>
}

export function EntryDistribution({ data, id }: { data: Statistics, id: number }) {
  const e = data.preferences.entries.find(v => v.entryId === id)
  if (!e) return null
  return <Stack spacing={1}><Typography component="h3" variant="h6">Publikumsliebling · Kult oder Skip</Typography><Typography>{description.audience}</Typography><Typography>Publikumsquote <MetricText metric={e.audienceRate} /> · {e.positiveEvaluations} / {e.evaluations} · Basis: {e.evaluations} Bewertungen · Summe {e.sumPoints} Stimmzettelpunkte</Typography><Typography>{description.polarization}</Typography><Typography>Varianz {decimal(e.variance.value)}{e.variance.reason && ` · ${e.variance.reason}`} · Standardabweichung {decimal(e.standardDeviation)} · {e.polarizationEligible ? 'Für Polarisierungsrekord geeignet' : 'Kein Polarisierungsrekord: weniger als zwei Bewertungen'}</Typography>
    <TableContainer><Table size="small" aria-label="Histogramm der Stimmzettelpunkte"><TableHead><TableRow><TableCell>Stimmzettelpunkte</TableCell><TableCell>Anzahl Bewertungen</TableCell></TableRow></TableHead><TableBody>{e.histogram.map(bin => <TableRow key={bin.points}><TableCell>{bin.points}{bin.points === 0 && ' · außerhalb Top 15'}</TableCell><TableCell>{bin.count}</TableCell></TableRow>)}</TableBody></Table></TableContainer>
  </Stack>
}

export function PreferenceDetail({ data, evidence, close, openEntry }: { data: Statistics, evidence: PreferenceEvidence, close: () => void, openEntry: (id: number) => void }) {
  const [selectedShow, setSelectedShow] = useState<number | null>(null)
  const [selectedBallot, setSelectedBallot] = useState<number | null>(null)
  const [only25, setOnly25] = useState(false)
  const pair = evidence.kind === 'similarity' ? data.preferences.pairs.find(p => p.firstId === evidence.firstId && p.secondId === evidence.secondId)! : null
  const person = evidence.kind !== 'similarity' ? data.preferences.participants.find(p => p.participationId === evidence.id)! : null
  const title = pair ? `Favoritenvergleich · ${nameOf(data, pair.firstId)} ↔ ${nameOf(data, pair.secondId)}` : `${evidence.kind === 'consensus' ? 'Konsensbelege' : 'Exklusive Punkte und 25er'} · ${nameOf(data, person!.participationId)}`
  const pairShow = pair?.shows.find(s => s.showId === selectedShow)
  const consensusShow = person?.shows.find(s => s.showId === selectedShow)
  const basis = data.preferences.showBases.find(s => s.showId === selectedShow)
  const visibleBallots = basis?.ballots.filter(b => b.ballotId === selectedBallot) ?? []
  const excluded = pairShow?.excludedEntryIds ?? (consensusShow?.excludedEntryId ? [consensusShow.excludedEntryId] : [])
  const entries = data.entries.filter(e => e.showId === selectedShow && !excluded.includes(e.id))
  return <Dialog open onClose={close} fullWidth maxWidth="lg"><DialogTitle>{title}</DialogTitle><DialogContent><Stack spacing={2}>
    {pair && <><Typography>{description.twins} {description.parallels}</Typography><Typography><MetricText metric={pair.similarity} /> · Basis: {pair.comparedShows} Shows · Jede berechenbare Show hat gleiches Gewicht.</Typography>{pair.shows.map(s => <Box key={s.showId}><ShowLink data={data} id={s.showId} /><Typography><MetricText metric={s.similarity} /> · {s.comparisonEntries} Drittbeiträge · Ausgeschlossene eigene Einreichungen: {s.excludedEntryIds.length ? s.excludedEntryIds.map(id => { const e = data.entries.find(v => v.id === id); return `${e?.artist} – ${e?.title}` }).join('; ') : 'Keine eigenen Einreichungen'}</Typography>{s.similarity.value !== null && <Button onClick={() => setSelectedShow(s.showId)}>Vergleich Show {data.standings.shows.find(v => v.showId === s.showId)?.showNumber} öffnen</Button>}</Box>)}</>}
    {person && evidence.kind === 'consensus' && <><Typography>{description.consensus}</Typography><Typography><MetricText metric={person.consensus} /> · Basis: {person.comparedShows} Shows</Typography>{person.shows.map(s => <Box key={s.showId}><ShowLink data={data} id={s.showId} /><Typography><MetricText metric={s.similarity} /> · {s.otherBallots} andere vollständige Stimmzettel · {s.comparisonEntries} Beiträge · Eigene Punktsumme {s.ownPointSum}, übrige Feldsumme {s.fieldPointSum} · Eigene Einreichung ausgeschlossen: {s.excludedEntryId === null ? 'Keine' : data.entries.find(e => e.id === s.excludedEntryId)?.title}</Typography>{s.similarity.value !== null && <Button onClick={() => setSelectedShow(s.showId)}>Stimmzettelbelege Show {data.standings.shows.find(v => v.showId === s.showId)?.showNumber} öffnen</Button>}</Box>)}</>}
    {person && evidence.kind === 'exclusive' && <><Typography>{description.exclusive}</Typography><Typography>{person.exclusivePoints} exklusive Stimmzettelpunkte · {person.exclusiveEntryIds.length} Beiträge · {person.exclusiveTwentyFiveEntryIds.length} exklusive 25er</Typography><Button onClick={() => setOnly25(v => !v)}>{only25 ? 'Alle exklusiven Beiträge anzeigen' : 'Nur exklusive 25er anzeigen'}</Button>{!(only25 ? person.exclusiveTwentyFiveEntryIds : person.exclusiveEntryIds).length && <Typography>Kein passendes positives Ereignis.</Typography>}{(only25 ? person.exclusiveTwentyFiveEntryIds : person.exclusiveEntryIds).map(id => {
      const e = data.preferences.entries.find(v => v.entryId === id)!
      return <Box key={id}><Typography><EntryText data={data} id={id} /> · {e.exclusivePoints} eigene Stimmzettelpunkte · Basis: {e.evaluations} bekannte wählbare Bewertungen{e.evaluations === 1 && ' · Einzelbewertung'}</Typography><Button onClick={() => { close(); openEntry(id) }}>Exklusivbeitragsbelege öffnen</Button></Box>
    })}</>}
    {selectedShow !== null && basis && <><Typography component="h3" variant="h6">Vergleichsbelege · Show {data.standings.shows.find(s => s.showId === selectedShow)?.showNumber}</Typography><Typography>0 = außerhalb Top 15, ohne bekannten Rang. Eigene Einreichungen sind nicht wählbar. Beide Vergleichsvektoren enthalten dieselben verbleibenden Beiträge.</Typography>
      {pair && <TableContainer><Table size="small" aria-label="Drittbeitragsvergleich"><TableHead><TableRow><TableCell>Beitrag</TableCell><TableCell>{nameOf(data, pair.firstId)} · Punkte / Rang</TableCell><TableCell>{nameOf(data, pair.secondId)} · Punkte / Rang</TableCell><TableCell>Belege</TableCell></TableRow></TableHead><TableBody>{entries.map(e => <TableRow key={e.id}><TableCell><EntryText data={data} id={e.id} /></TableCell>{[pair.firstId, pair.secondId].map(id => { const point = basis.ballots.find(b => b.participationId === id)?.positions.find(p => p.entryId === e.id); return <TableCell key={id}>{point ? `${point.points} / Rang ${point.rank}` : '0 · außerhalb Top 15'}</TableCell> })}<TableCell><Button onClick={() => { close(); openEntry(e.id) }}>Beitragsbelege öffnen</Button></TableCell></TableRow>)}</TableBody></Table></TableContainer>}
      {person && <><Typography>Eigener vollständiger Stimmzettel wird separat gezeigt und ist aus allen übrigen Feldsummen entfernt. Normierung: eigene Punkte / {consensusShow?.ownPointSum}; übrige Feldpunkte / {consensusShow?.fieldPointSum}.</Typography>{basis.ballots.map(b => <Button key={b.ballotId} onClick={() => setSelectedBallot(b.ballotId)}>Stimmzettel von {nameOf(data, b.participationId)} öffnen</Button>)}{visibleBallots.map(b => <Box key={b.ballotId}><Typography component="h4" variant="h6">{b.participationId === person.participationId ? 'Eigener Stimmzettel · aus Feld entfernt' : 'Übriger Stimmzettel'} · {nameOf(data, b.participationId)}</Typography><TableContainer><Table size="small" aria-label={`Stimmzettel ${b.ballotId}`}><TableHead><TableRow><TableCell>Vergleichsbeitrag</TableCell><TableCell>Stimmzettelpunkte / Rang / Zustand</TableCell></TableRow></TableHead><TableBody>{entries.map(e => { const point = b.positions.find(p => p.entryId === e.id); return <TableRow key={e.id}><TableCell><EntryText data={data} id={e.id} /></TableCell><TableCell>{e.participationId === b.participationId ? 'Eigene Einreichung · nicht wählbar' : point ? `${point.points} / Rang ${point.rank}` : '0 · außerhalb Top 15'}</TableCell></TableRow> })}</TableBody></Table></TableContainer></Box>)}</>}
    </>}
  </Stack></DialogContent><DialogActions><Button onClick={close}>Schließen</Button></DialogActions></Dialog>
}
