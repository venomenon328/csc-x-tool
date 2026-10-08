import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Typography } from '@mui/material'
import { useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { StandingsChart } from '../standings/StandingsChart'
import { decimal, evaluationLabels, percent, type Direction, type Profile, type Statistics } from './api'
import { PersonLink, RelationList } from './RelationshipViews'
import { nameOf } from './labels'
import { EntryDistribution } from './PreferenceViews'

export type Evidence = { kind: 'entry', id: number } | { kind: 'points' | 'podium', id: number }
type Actions = { openRelation: (direction: Direction) => void, openEvidence: (evidence: Evidence) => void }

export function ProfileView({ data, profile, openRelation, openEvidence }: { data: Statistics, profile: Profile } & Actions) {
  const [allGivers, setAllGivers] = useState(false)
  const [allReceivers, setAllReceivers] = useState(false)
  const row = data.standings.rows.find(r => r.participationId === profile.participationId)!
  const incoming = data.relations.filter(r => r.receiverId === row.participationId && (allGivers ? r.opportunities > 0 : profile.topGiverIds.includes(r.giverId)))
  const outgoing = data.relations.filter(r => r.giverId === row.participationId && (allReceivers ? r.opportunities > 0 : profile.topReceiverIds.includes(r.receiverId)))
  const personalPairs = data.pairs.filter(p => p.firstId === row.participationId || p.secondId === row.participationId)
  return <Stack spacing={3}>
    <Paper sx={{ p: 2 }}><Typography component="h2" variant="h5">{row.displayName} · {row.countryName}</Typography><Typography>Gesamtplatz: {row.rank ?? 'N/A'} · {row.totalPoints} Gesamtwertungspunkte</Typography><ProfileMetrics profile={profile} openEvidence={openEvidence} /></Paper>
    <Box><RelationList key={`givers-${allGivers}`} title={allGivers ? 'Alle Punktegeber einschließlich bekannter Nullen' : 'Top-Punktegeber mit Grenzgleichständen'} data={data} relations={incoming} open={openRelation} /><Button onClick={() => setAllGivers(v => !v)}>{allGivers ? 'Top-Geber anzeigen' : 'Alle Geber anzeigen'}</Button></Box>
    <Box><RelationList key={`receivers-${allReceivers}`} title={allReceivers ? 'Alle Punkteempfänger einschließlich bekannter Nullen' : 'Top-Punkteempfänger mit Grenzgleichständen'} data={data} relations={outgoing} open={openRelation} /><Button onClick={() => setAllReceivers(v => !v)}>{allReceivers ? 'Top-Empfänger anzeigen' : 'Alle Empfänger anzeigen'}</Button></Box>
    <Paper sx={{ p: 2 }}><Typography component="h2" variant="h5">Eigene Beiträge und Showergebnisse</Typography><TableContainer><Table size="small" aria-label="Profilbeiträge"><TableHead><TableRow><TableCell>Show</TableCell><TableCell>Beitrag</TableCell><TableCell>Showrang</TableCell><TableCell>Stimmzettelpunkte</TableCell><TableCell>Gesamtwertungspunkte</TableCell><TableCell>Belege</TableCell></TableRow></TableHead><TableBody>{data.standings.shows.map(show => {
      const cell = row.shows.find(c => c.showId === show.showId)!
      const entry = data.entries.find(e => e.showId === show.showId && e.participationId === row.participationId)
      const unavailable = cell.state === 'NO_ENTRY' ? 'Keine Einreichung' : 'Nicht gewertet'
      return <TableRow key={show.showId}><TableCell><Button component={RouterLink} to={`/shows/${show.showId}/evaluation?view=standings`}>Show {show.showNumber}</Button></TableCell><TableCell>{entry ? `${entry.artist} – ${entry.title}` : 'Keine Einreichung'}</TableCell><TableCell>{cell.showRank ?? 'N/A'}</TableCell><TableCell>{cell.ballotPoints ?? unavailable}</TableCell><TableCell>{cell.contestPoints ?? unavailable}</TableCell><TableCell>{entry && <Button onClick={() => openEvidence({ kind: 'entry', id: entry.id })}>Beitragsdetails</Button>}</TableCell></TableRow>
    })}</TableBody></Table></TableContainer></Paper>
    <Paper sx={{ p: 2 }}><Typography component="h2" variant="h5">Persönlicher Verlauf</Typography><StandingsChart rows={[row]} shows={data.standings.shows} metric="points" /><StandingsChart rows={[row]} shows={data.standings.shows} metric="rank" /></Paper>
    <Paper sx={{ p: 2 }}><Typography component="h2" variant="h5">Persönliche Paarwerte</Typography><Typography>Minimum und Differenz ausschließlich auf gemeinsamer beidseitiger Showbasis.</Typography>{!personalPairs.length && <Typography>Keine gemeinsame beidseitige Bewertungsbasis.</Typography>}{personalPairs.map(p => <Typography key={`${p.firstId}-${p.secondId}`}><Button onClick={() => openRelation({ giverId: p.firstId, receiverId: p.secondId })}>{nameOf(data, p.firstId)} ↔ {nameOf(data, p.secondId)}: Partnerschaft {p.partnership}, Differenz {p.difference}, Basis {p.commonShowIds.length} Shows</Button></Typography>)}</Paper>
  </Stack>
}

function ProfileMetrics({ profile, openEvidence }: { profile: Profile, openEvidence: Actions['openEvidence'] }) {
  return <Stack spacing={1}>
    <Typography>Erhaltene 25er: {profile.twentyFives} / {profile.opportunities} wählbare Bewertungen · Quote {percent(profile.twentyFiveRate)} · {profile.countedEntries} gewertete Einreichungen.</Typography>
    <Typography>Top-15-Platzierungen: {profile.top15Count} · Podien: {profile.podiumCount} · Showsiege: {profile.wins}, einschließlich geteilter Ränge.</Typography>
    <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 1 }}><Button onClick={() => openEvidence({ kind: 'points', id: profile.participationId })}>Punkteserie: {profile.pointRuns[0]?.length ?? 0} Shows</Button><Button onClick={() => openEvidence({ kind: 'podium', id: profile.participationId })}>Podiumsserie: {profile.podiumRuns[0]?.length ?? 0} Shows</Button></Stack>
  </Stack>
}

export function StatisticsRecords({ data, openRelation, openEvidence }: { data: Statistics } & Actions) {
  const records = data.records
  function pairRecords(title: string, directions: Direction[], difference: boolean) {
    return <Paper sx={{ p: 2 }}><Typography component="h2" variant="h5">{title}</Typography><Typography>{difference ? 'Größere minus kleinere Richtungssumme auf denselben beidseitigen Shows.' : 'Minimum beider Richtungssummen auf denselben beidseitigen Shows.'} Alle Höchstwertgleichstände; keine Auszeichnung ohne positives Ereignis.</Typography>{!directions.length && <Typography>Kein passendes Ereignis auf gemeinsamer Basis.</Typography>}{directions.map(d => {
      const pair = data.pairs.find(p => p.firstId === d.giverId && p.secondId === d.receiverId)!
      return <Box key={`${d.giverId}-${d.receiverId}`}><PersonLink data={data} id={pair.firstId} /> ↔ <PersonLink data={data} id={pair.secondId} /><Typography>{pair.firstToSecond} / {pair.secondToFirst} Stimmzettelpunkte · Basis: {pair.commonShowIds.length} Shows · {difference ? `Differenz ${pair.difference}, Mehrunterstützung ${nameOf(data, pair.strongerGiverId!)} → ${nameOf(data, pair.strongerGiverId === pair.firstId ? pair.secondId : pair.firstId)}` : `Minimum ${pair.partnership}`}</Typography><Button onClick={() => openRelation(d)}>Rekordbelege öffnen</Button></Box>
    })}</Paper>
  }
  function participants(title: string, ids: number[], metric: (p: Profile) => string, kind?: 'points' | 'podium') {
    return <Box><Typography component="h3" variant="h6">{title}</Typography>{!ids.length && <Typography>Kein passendes Ereignis.</Typography>}{ids.map(id => {
      const profile = data.profiles.find(p => p.participationId === id)!
      return <Box key={id}><PersonLink data={data} id={id} /><Typography>{metric(profile)}</Typography>{kind && <Button onClick={() => openEvidence({ kind, id })}>Serienbelege öffnen</Button>}</Box>
    })}</Box>
  }
  return <Stack spacing={3}>
    {pairRecords('Punktepartnerschaft', records.partnerships, false)}
    {pairRecords('Unerwiderte Punkteliebe', records.unrequited, true)}
    <Paper sx={{ p: 2 }}><Stack spacing={2}><Typography component="h2" variant="h5">König der 25er</Typography><Typography>Anzahl veröffentlichter Rang-1-Stimmen; Quote je tatsächlich wählbarer Bewertung. Alle Höchstwertgleichstände.</Typography>
      {participants('Teilnehmer', records.twentyFiveParticipantIds, p => `${p.twentyFives} 25er / ${p.opportunities} Bewertungen · ${percent(p.twentyFiveRate)} · ${p.countedEntries} Einreichungen`)}
      <Typography component="h3" variant="h6">Beiträge</Typography>{!records.twentyFiveEntryIds.length && <Typography>Keine Rang-1-Stimme für einen gewerteten Beitrag.</Typography>}{records.twentyFiveEntryIds.map(id => {
        const entry = data.entries.find(e => e.id === id)!
        const award = data.entryAwards.find(a => a.entryId === id)!
        return <Box key={id}><Typography>{entry.artist} – {entry.title} · {award.twentyFives} / {award.opportunities} Bewertungen · {percent(award.rate)}</Typography><PersonLink data={data} id={entry.participationId!} /><Button onClick={() => openEvidence({ kind: 'entry', id })}>25er-Beitragsbelege öffnen</Button></Box>
      })}</Stack></Paper>
    <Paper sx={{ p: 2 }}><Stack spacing={2}><Typography component="h2" variant="h5">Dauerbrenner</Typography><Typography>Serien verwenden aufeinanderfolgende Shownummern. Offene oder fehlende Shows unterbrechen den Nachweis. Häufigkeiten zählen alle gewerteten Shows, auch geteilte Ränge 3 und 15.</Typography>
      {participants('Längste Punkteserie', records.pointRunParticipantIds, p => `${p.pointRuns[0]?.length} Shows mit Gesamtwertungspunkten`, 'points')}
      {participants('Längste Podiumsserie', records.podiumRunParticipantIds, p => `${p.podiumRuns[0]?.length} Shows mit Rang ≤ 3`, 'podium')}
      {participants('Meiste Top-15-Platzierungen', records.top15ParticipantIds, p => `${p.top15Count} Top-15-Platzierungen; Einzelshows im Profil`)}
      {participants('Meiste Podiumsplätze', records.podiumParticipantIds, p => `${p.podiumCount} Podien; ${p.wins} Showsiege; Einzelshows im Profil`)}
    </Stack></Paper>
  </Stack>
}

export function EvidenceDetail({ data, evidence, close, openRelation }: { data: Statistics, evidence: Evidence | null, close: () => void, openRelation: Actions['openRelation'] }) {
  const entry = evidence?.kind === 'entry' ? data.entries.find(e => e.id === evidence.id) : null
  const profile = evidence && evidence.kind !== 'entry' ? data.profiles.find(p => p.participationId === evidence.id) : null
  const row = profile && data.standings.rows.find(r => r.participationId === profile.participationId)
  const runs = profile ? evidence?.kind === 'points' ? profile.pointRuns : profile.podiumRuns : []
  const award = entry && data.entryAwards.find(a => a.entryId === entry.id)
  return <Dialog open={evidence !== null} onClose={close} fullWidth maxWidth="md"><DialogTitle>{entry ? `Beitragsdetails · ${entry.artist} – ${entry.title}` : `Serienbelege · ${profile ? nameOf(data, profile.participationId) : ''}`}</DialogTitle><DialogContent><Stack spacing={2}>
    {entry && <><Typography>Show {data.standings.shows.find(s => s.showId === entry.showId)?.showNumber} · {entry.participationId !== null && nameOf(data, entry.participationId)}</Typography><Typography>{award ? `${award.twentyFives} erhaltene 25er / ${award.opportunities} wählbare Bewertungen · Quote ${percent(award.rate)}` : 'Show nicht gewertet; keine Rekordbasis.'}</Typography><Typography>Showrang: {data.standings.rows.find(r => r.participationId === entry.participationId)?.shows.find(c => c.showId === entry.showId)?.showRank ?? 'N/A'}</Typography>
      <Button component={RouterLink} to={`/shows/${entry.showId}/evaluation?view=standings`}>Showauswertung öffnen</Button>
      <EntryDistribution data={data} id={entry.id} />
      <TableContainer><Table size="small" aria-label="Beitragswertungen"><TableHead><TableRow><TableCell>Geber</TableCell><TableCell>Punkte / Rang / Zustand</TableCell><TableCell>Beleg</TableCell></TableRow></TableHead><TableBody>{data.standings.rows.map(giver => {
        const evaluation = data.relations.find(r => r.giverId === giver.participationId && r.receiverId === entry.participationId)?.shows.find(e => e.showId === entry.showId)
        return <TableRow key={giver.participationId}><TableCell><PersonLink data={data} id={giver.participationId} /></TableCell><TableCell>{giver.participationId === entry.participationId ? 'Eigene Einreichung · nicht wählbar' : `${evaluation?.points ?? 'N/A'} · ${evaluation ? evaluationLabels[evaluation.state] : 'Keine Gelegenheit'}${evaluation?.ballotRank != null ? ` · Rang ${evaluation.ballotRank}` : ''}`}</TableCell><TableCell>{giver.participationId !== entry.participationId && entry.participationId !== null && <Button onClick={() => { close(); openRelation({ giverId: giver.participationId, receiverId: entry.participationId! }) }}>Beziehung öffnen</Button>}</TableCell></TableRow>
      })}</TableBody></Table></TableContainer></>}
    {profile && <><Typography>{evidence?.kind === 'points' ? 'Mindestens ein Gesamtwertungspunkt je Show.' : 'Showrang höchstens 3 je Show.'} Alle gleich langen maximalen Läufe.</Typography>{!runs.length && <Typography>Keine passende Serie.</Typography>}{runs.map(run => <Box key={run.firstShowId}><Typography>{run.length} Shows · Show {data.standings.shows.find(s => s.showId === run.firstShowId)?.showNumber} bis {data.standings.shows.find(s => s.showId === run.lastShowId)?.showNumber}</Typography>{run.showIds.map(id => {
      const cell = row?.shows.find(c => c.showId === id)
      return <Typography key={id}><Button component={RouterLink} to={`/shows/${id}/evaluation?view=standings`}>Show {data.standings.shows.find(s => s.showId === id)?.showNumber}</Button> · Showrang {cell?.showRank} · {decimal(cell?.contestPoints ?? null)} Gesamtwertungspunkte</Typography>
    })}</Box>)}</>}
  </Stack></DialogContent><DialogActions><Button onClick={close}>Schließen</Button></DialogActions></Dialog>
}
