import { Autocomplete, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography } from '@mui/material'
import { useMemo, useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { decimal, evaluationLabels, type Direction, type Relation, type Statistics } from './api'
import { nameOf, profilePath } from './labels'

export function PersonLink({ data, id }: { data: Statistics, id: number }) {
  return <Button component={RouterLink} to={profilePath(id)} sx={{ textTransform: 'none', overflowWrap: 'anywhere' }}>{nameOf(data, id)}</Button>
}

export function RelationList({ data, relations, title, open }: { data: Statistics, relations: Relation[], title: string, open: (direction: Direction) => void }) {
  const [page, setPage] = useState(0)
  const indexed = useMemo(() => new Map(data.relations.map(r => [`${r.giverId}-${r.receiverId}`, r])), [data])
  return <Stack spacing={1}>
    <Typography component="h3" variant="h6">{title}</Typography>
    {!relations.length ? <Typography>Keine auswertbaren Beziehungen in dieser Liste.</Typography> : <>
      <TableContainer component={Paper}><Table size="small" aria-label={title}><TableHead><TableRow><TableCell>Geber → Empfänger</TableCell><TableCell>Stimmzettelpunkte</TableCell><TableCell>Bepunktet / Gelegenheiten</TableCell><TableCell>Ø je Gelegenheit</TableCell><TableCell>25er</TableCell><TableCell>Gegenrichtung / Gelegenheiten</TableCell><TableCell>Belege</TableCell></TableRow></TableHead>
        <TableBody>{relations.slice(page * 25, (page + 1) * 25).map(r => {
          const reverse = indexed.get(`${r.receiverId}-${r.giverId}`)
          return <TableRow key={`${r.giverId}-${r.receiverId}`}>
            <TableCell><PersonLink data={data} id={r.giverId} /> → <PersonLink data={data} id={r.receiverId} /></TableCell><TableCell>{r.points}</TableCell><TableCell>{r.scoredShows} / {r.opportunities}</TableCell><TableCell>{decimal(r.average)}</TableCell><TableCell>{r.twentyFives}</TableCell><TableCell>{reverse?.opportunities ? reverse.points : 'N/A'} / {reverse?.opportunities ?? 0}</TableCell><TableCell><Button onClick={() => open(r)}>Beziehung öffnen</Button></TableCell>
          </TableRow>
        })}</TableBody></Table></TableContainer>
      {relations.length > 25 && <Stack direction="row" spacing={1}><Button disabled={page === 0} onClick={() => setPage(p => p - 1)}>Vorherige Beziehungen</Button><Typography>Zeilen {page * 25 + 1}–{Math.min((page + 1) * 25, relations.length)} von {relations.length}</Typography><Button disabled={(page + 1) * 25 >= relations.length} onClick={() => setPage(p => p + 1)}>Weitere Beziehungen</Button></Stack>}
    </>}
  </Stack>
}

export function RelationshipDetail({ data, direction, close }: { data: Statistics, direction: Direction | null, close: () => void }) {
  const forward = direction && data.relations.find(r => r.giverId === direction.giverId && r.receiverId === direction.receiverId)
  const reverse = direction && data.relations.find(r => r.giverId === direction.receiverId && r.receiverId === direction.giverId)
  const pair = direction && data.pairs.find(p => [p.firstId, p.secondId].includes(direction.giverId) && [p.firstId, p.secondId].includes(direction.receiverId))
  return <Dialog open={!!forward} onClose={close} fullWidth maxWidth="lg"><DialogTitle>Beziehungsdetails · {direction && `${nameOf(data, direction.giverId)} → ${nameOf(data, direction.receiverId)}`}</DialogTitle><DialogContent>
    {forward && reverse && <Stack spacing={2}>
      <Typography>Gesamte gerichtete Basis: {nameOf(data, forward.giverId)} → {nameOf(data, forward.receiverId)}: {forward.points} Stimmzettelpunkte · {forward.scoredShows} / {forward.opportunities} Shows bepunktet · Ø {decimal(forward.average)} · {forward.twentyFives} 25er.</Typography>
      <Typography>Gegenrichtung: {reverse.points} Stimmzettelpunkte · {reverse.scoredShows} / {reverse.opportunities} Shows bepunktet · Ø {decimal(reverse.average)} · {reverse.twentyFives} 25er.</Typography>
      {pair ? <Box><Typography>Gemeinsame beidseitige Basis: {pair.commonShowIds.length} Shows. Diese Basis kann von den gerichteten Gesamtsummen abweichen.</Typography><Typography>{nameOf(data, pair.firstId)} → {nameOf(data, pair.secondId)}: {pair.firstToSecond}; Gegenrichtung: {pair.secondToFirst}. Punktepartnerschaft: {pair.partnership}; Differenz: {pair.difference}{pair.strongerGiverId !== null && ` in Richtung ${nameOf(data, pair.strongerGiverId)} → ${nameOf(data, pair.strongerGiverId === pair.firstId ? pair.secondId : pair.firstId)}`}.</Typography></Box> : <Typography>Keine gemeinsame beidseitige Bewertungsbasis.</Typography>}
      <Typography>0 bedeutet außerhalb Top 15; N/A bedeutet keine Bewertung. Eine eigene Einreichung ist nicht wählbar.</Typography>
      <TableContainer><Table size="small" aria-label="Einzelshowbelege der Beziehung"><TableHead><TableRow><TableCell>Show / gemeinsame Basis</TableCell><TableCell>Empfängerbeitrag</TableCell><TableCell>Hinrichtung / Rang</TableCell><TableCell>Geberbeitrag</TableCell><TableCell>Gegenrichtung / Rang</TableCell></TableRow></TableHead><TableBody>
        {forward.shows.map(e => {
          const back = reverse.shows.find(b => b.showId === e.showId)
          const show = data.standings.shows.find(s => s.showId === e.showId)!
          const entry = data.entries.find(a => a.id === e.entryId)
          const backEntry = data.entries.find(a => a.id === back?.entryId)
          return <TableRow key={e.showId}><TableCell><Button component={RouterLink} to={`/shows/${e.showId}/evaluation?view=standings`}>Show {show.showNumber} · {show.name}</Button>{pair?.commonShowIds.includes(e.showId) && <Typography variant="caption">Gemeinsame Basis</Typography>}</TableCell><TableCell>{entry ? `${entry.artist} – ${entry.title}` : 'Keine Einreichung'}</TableCell><TableCell>{e.points ?? 'N/A'} · {evaluationLabels[e.state]}{e.ballotRank !== null && ` · Rang ${e.ballotRank}`}</TableCell><TableCell>{backEntry ? `${backEntry.artist} – ${backEntry.title}` : 'Keine Einreichung'}</TableCell><TableCell>{back?.points ?? 'N/A'} · {back && evaluationLabels[back.state]}{back?.ballotRank != null && ` · Rang ${back.ballotRank}`}</TableCell></TableRow>
        })}
      </TableBody></Table></TableContainer>
    </Stack>}
  </DialogContent><DialogActions><Button onClick={close}>Schließen</Button></DialogActions></Dialog>
}

export function Heatmap({ data, open }: { data: Statistics, open: (direction: Direction) => void }) {
  const people = data.standings.rows
  const [rowPage, setRowPage] = useState(0)
  const [columnPage, setColumnPage] = useState(0)
  const indexed = useMemo(() => new Map(data.relations.map(r => [`${r.giverId}-${r.receiverId}`, r])), [data])
  const rows = people.slice(rowPage * 10, (rowPage + 1) * 10)
  const columns = people.slice(columnPage * 10, (columnPage + 1) * 10)
  return <Paper component="section" sx={{ p: 2 }}><Stack spacing={2}>
    <Typography component="h2" variant="h5">Gerichtete Heatmap</Typography>
    <Typography>Zeilen = Geber, Spalten = Empfänger. Zahlen = Stimmzettelpunktsummen, 0 = bekannte Null, N/A = keine Gelegenheit, — = eigene Einreichung / nicht wählbar. Je Ausschnitt höchstens 10 × 10; alle Teilnehmer sind erreichbar.</Typography>
    <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>{(['Geber', 'Empfänger'] as const).map((label, i) => <Autocomplete key={label} options={people} getOptionLabel={p => p.displayName} getOptionKey={p => p.participationId} onChange={(_, person) => { if (person) (i === 0 ? setRowPage : setColumnPage)(Math.floor(people.indexOf(person) / 10)) }} renderInput={params => <TextField {...params} label={`${label} in Heatmap finden`} />} sx={{ flex: 1 }} />)}</Stack>
    {[['Geberzeilen', rowPage, setRowPage], ['Empfängerspalten', columnPage, setColumnPage]].map(([label, page, setPage]) => {
      const current = page as number
      const update = setPage as (value: number) => void
      return <Stack key={label as string} direction="row" spacing={1} sx={{ flexWrap: 'wrap' }}><Button aria-label={`Vorherige ${label}`} disabled={current === 0} onClick={() => update(current - 1)}>Zurück</Button><Typography>{label as string}: {people.length ? current * 10 + 1 : 0}–{Math.min((current + 1) * 10, people.length)} von {people.length}</Typography><Button aria-label={`Weitere ${label}`} disabled={(current + 1) * 10 >= people.length} onClick={() => update(current + 1)}>Weiter</Button></Stack>
    })}
    <TableContainer role="region" aria-label="Heatmap horizontal scrollen" tabIndex={0} sx={{ maxHeight: 650, overflow: 'auto' }}><Table stickyHeader size="small" aria-label="Geber-Empfänger-Heatmap"><TableHead><TableRow><TableCell sx={{ minWidth: 180 }}>Geber ↓ / Empfänger →</TableCell>{columns.map(p => <TableCell key={p.participationId} scope="col" sx={{ minWidth: 120, maxWidth: 180, overflowWrap: 'anywhere' }}>{p.displayName}</TableCell>)}</TableRow></TableHead><TableBody>{rows.map(giver => <TableRow key={giver.participationId}><TableCell component="th" scope="row" sx={{ position: 'sticky', left: 0, bgcolor: 'background.paper', zIndex: 1, maxWidth: 240, overflowWrap: 'anywhere' }}>{giver.displayName}</TableCell>{columns.map(receiver => {
      const relation = indexed.get(`${giver.participationId}-${receiver.participationId}`)
      if (giver.participationId === receiver.participationId) return <TableCell key={receiver.participationId}><span aria-label={`${giver.displayName}: eigene Einreichung, nicht wählbar`}>—</span></TableCell>
      const value = relation?.opportunities ? String(relation.points) : 'N/A'
      return <TableCell key={receiver.participationId} sx={{ bgcolor: relation?.points ? `rgba(97, 218, 251, ${0.04 + relation.points / Math.max(data.relations[0]?.points ?? 1, 1) * 0.25})` : undefined }}><Button aria-label={`${giver.displayName} → ${receiver.displayName}: ${value}, Beziehungsdetails`} onClick={() => open({ giverId: giver.participationId, receiverId: receiver.participationId })}>{value}</Button></TableCell>
    })}</TableRow>)}</TableBody></Table></TableContainer>
  </Stack></Paper>
}
