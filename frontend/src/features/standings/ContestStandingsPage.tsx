import { Alert, Autocomplete, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography } from '@mui/material'
import { useEffect, useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { useContest } from '../contests/ContestContext'
import { CountryFlag } from '../participants/CountryFlag'
import { closureLabels, fetchContestStandings, type ContestStanding, type ContestStandings, type ShowCell } from './contestApi'
import { StandingsChart } from './StandingsChart'
import { StatisticsNavigation } from '../statistics/StatisticsNavigation'

export function ContestStandingsPage() {
  const { selectedContestId, selectedContest } = useContest()
  return <Stack spacing={3}>
    <Box><Typography variant="overline" color="secondary">{selectedContest?.name}</Typography><Typography component="h1" variant="h4">Gesamtwertung</Typography></Box>
    <StatisticsNavigation />
    {selectedContestId === null ? <Alert severity="info">Bitte eine CSC-Ausgabe auswählen.</Alert> : <ContestContent contestId={selectedContestId} key={selectedContestId} />}
  </Stack>
}

function ContestContent({ contestId }: { contestId: number }) {
  const [data, setData] = useState<ContestStandings | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const [selectedIds, setSelectedIds] = useState<number[]>([])
  const [metric, setMetric] = useState<'points' | 'rank'>('points')
  const [detail, setDetail] = useState<{ cell: ShowCell, name: string } | null>(null)
  useEffect(() => {
    let cancelled = false
    void fetchContestStandings(contestId).then((loaded) => {
      if (!cancelled && loaded.contestId === contestId) { setData(loaded); setError(null) }
    }).catch((caught: unknown) => { if (!cancelled) { setData(null); setError(caught instanceof Error ? caught.message : 'Die Gesamtwertung konnte nicht geladen werden.') } })
    return () => { cancelled = true }
  }, [contestId, revision])
  if (error) return <Alert severity="error" action={<Button onClick={() => setRevision((n) => n + 1)}>Erneut laden</Button>}>{error}</Alert>
  if (!data) return <Typography>Gesamtwertung wird geladen …</Typography>
  const counted = data.includedShowIds.length
  const selected = data.rows.filter((row) => selectedIds.includes(row.participationId))
  return <>
    <Paper sx={{ p: 2 }}><Stack spacing={1}>
      <Typography component="h2" variant="h6">Datenbasis: {counted} von {data.shows.length} Shows gewertet</Typography>
      <Typography color="text.secondary">Nur bewusst abgeschlossene Shows. Geteilte Plätze überspringen Folgeplätze; alle Beiträge bis Showrang 15 erhalten die vollen Gesamtwertungspunkte.</Typography>
      <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 1 }}>{data.shows.map((show) => <Button key={show.showId} component={RouterLink} to={`/shows/${show.showId}/evaluation?view=standings`} size="small">Show {show.showNumber}: {closureLabels[show.status]}</Button>)}</Stack>
      <Button sx={{ alignSelf: 'flex-start' }} onClick={() => { setDetail(null); setRevision((n) => n + 1) }}>Wertung aktualisieren</Button>
    </Stack></Paper>
    {counted === 0 && <Alert severity="info">Noch keine gewertete Show. Die Teilnehmer bleiben sichtbar; es gibt noch keine Plätze oder Sieger.</Alert>}
    <TableContainer component={Paper} role="region" aria-label="Gesamtwertung horizontal scrollen" tabIndex={0} sx={{ maxWidth: '100%', overflowX: 'auto' }}>
      <Table aria-label="Contest-Gesamtwertung" size="small" sx={{ minWidth: 700 }}>
        <TableHead><TableRow><TableCell>Platz</TableCell><TableCell>Teilnehmer / Land</TableCell><TableCell align="right">Gesamtwertungspunkte</TableCell><TableCell>Rangveränderung</TableCell>{data.shows.map((show) => <TableCell key={show.showId} align="center"><Typography>Show {show.showNumber}</Typography><Typography variant="caption">{closureLabels[show.status]}</Typography></TableCell>)}</TableRow></TableHead>
        <TableBody>{data.rows.map((row) => <TableRow key={row.participationId}>
          <TableCell>{row.rank ?? '–'}</TableCell><TableCell><Stack direction="row" spacing={1} sx={{ alignItems: 'center', minWidth: 160 }}><CountryFlag code={row.countryCode} countryName={row.countryName} /><Box><Typography>{row.displayName}</Typography><Typography variant="body2" color="text.secondary">{row.countryName}</Typography></Box></Stack></TableCell>
          <TableCell align="right">{counted ? row.totalPoints : '–'}</TableCell><TableCell>{rankChange(row.rankChange)}</TableCell>
          {row.shows.map((cell) => <TableCell align="center" key={cell.showId}>{cell.state === 'NOT_COUNTED' ? <span aria-label="Show nicht gewertet">–</span> : cell.state === 'NO_ENTRY' ? 'Keine Einreichung' : <Button aria-label={`${row.displayName}, Show ${data.shows.find((s) => s.showId === cell.showId)?.showNumber}: ${cell.contestPoints} Gesamtwertungspunkte, Details`} onClick={() => setDetail({ cell, name: row.displayName })}>{cell.contestPoints}</Button>}</TableCell>)}
        </TableRow>)}</TableBody>
      </Table>
    </TableContainer>
    <Typography variant="body2" color="text.secondary">0 = gewerteter Beitrag ohne Gesamtwertungspunkte · Keine Einreichung = kein Showrang · – = nicht gewertete Show. Rangveränderung: Vergleich zum vorherigen tatsächlich gewerteten Schritt; beim ersten Schritt kein Vergleich.</Typography>
    <Paper component="section" sx={{ p: 2 }}><Stack spacing={2}>
      <Typography component="h2" variant="h5">Verlauf nach Showreihenfolge</Typography>
      <Autocomplete multiple options={data.rows} value={selected} getOptionLabel={(row: ContestStanding) => `${row.displayName} · ${row.countryName}`} getOptionKey={(row) => row.participationId} isOptionEqualToValue={(a, b) => a.participationId === b.participationId} onChange={(_, rows) => setSelectedIds(rows.map((row) => row.participationId))} renderInput={(params) => <TextField {...params} label="Teilnehmer für den Verlauf auswählen" />} />
      <TextField select label="Verlauf anzeigen" value={metric} onChange={(event) => setMetric(event.target.value as 'points' | 'rank')} sx={{ maxWidth: 300 }}><MenuItem value="points">Gesamtwertungspunkte</MenuItem><MenuItem value="rank">Contestplatz</MenuItem></TextField>
      <StandingsChart rows={selected} shows={data.shows} metric={metric} />
    </Stack></Paper>
    <Dialog open={detail !== null} onClose={() => setDetail(null)} fullWidth maxWidth="sm"><DialogTitle>Showdetails · {detail?.name}</DialogTitle><DialogContent>{detail && <Stack spacing={1}><Typography>{detail.cell.artist} – {detail.cell.title}</Typography><Typography>Showrang: {detail.cell.showRank}</Typography><Typography>Stimmzettelpunkte: {detail.cell.ballotPoints}</Typography><Typography>Gesamtwertungspunkte: {detail.cell.contestPoints}</Typography><Button component={RouterLink} to={`/shows/${detail.cell.showId}/evaluation?view=standings`}>Showauswertung öffnen</Button></Stack>}</DialogContent><DialogActions><Button onClick={() => setDetail(null)}>Schließen</Button></DialogActions></Dialog>
  </>
}

function rankChange(value: number | null) { return value === null ? '–' : value > 0 ? `↑ ${value}` : value < 0 ? `↓ ${-value}` : 'Unverändert' }
