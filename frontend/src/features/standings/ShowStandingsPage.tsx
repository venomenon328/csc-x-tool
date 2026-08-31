import { Alert, Box, Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Typography } from '@mui/material'
import { useEffect, useState } from 'react'
import { ApiErrorNotice } from '../../components/ApiErrorNotice'
import { CountryFlag } from '../participants/CountryFlag'
import { fetchShowStandings, ShowStandingsApiError, type ShowStanding, type ShowStandings } from './api'

export function ShowStandingsEvaluation({ showId }: { showId: number }) {
  const [standings, setStandings] = useState<ShowStandings | null>(null)
  const [error, setError] = useState<ShowStandingsApiError | null>(null)

  useEffect(() => {
    let cancelled = false
    void fetchShowStandings(showId).then((loadedStandings) => {
      if (!cancelled) { setStandings(loadedStandings); setError(null) }
    }).catch((caught: unknown) => {
      if (!cancelled) { setStandings(null); setError(asShowStandingsApiError(caught, showId)) }
    })
    return () => { cancelled = true }
  }, [showId])

  return <Stack component="section" spacing={3}>
    <Box>
      <Typography component="h2" variant="h5">Zwischenstand</Typography>
      <Typography color="text.secondary">Punkte aus erfassten Stimmzetteln werden bei jedem Abruf neu aus den gespeicherten Rängen abgeleitet.</Typography>
    </Box>
    {error !== null && <ApiErrorNotice error={error.apiError} />}
    {standings === null && error === null && <Typography color="text.secondary">Zwischenstand wird geladen …</Typography>}
    {standings !== null && <StandingsContent standings={standings} />}
  </Stack>
}

function StandingsContent({ standings }: { standings: ShowStandings }) {
  const incomplete = standings.unrecordedCount > 0
  return <>
    <Paper component="section" sx={{ p: 2 }}>
      <Typography component="h3" variant="h6">Erfassungsstand</Typography>
      <Typography color="text.secondary">{standings.votedCount} abgestimmt · {standings.notVotedCount} nicht abgestimmt · {standings.unrecordedCount} unerfasst</Typography>
      <Typography color="text.secondary" sx={{ mt: 1 }} variant="body2">Nur abgegebene Stimmzettel fließen ein. Das ist keine offizielle Gesamtwertung oder Finalplatzierung.</Typography>
    </Paper>
    {incomplete && <Alert severity="warning">Der Zwischenstand ist unvollständig: Für {standings.unrecordedCount} Teilnehmer {standings.unrecordedCount === 1 ? 'ist' : 'sind'} noch keine Stimmzettel erfasst.</Alert>}
    {standings.votedCount === 0
      ? <Alert severity="info">Noch keine veröffentlichten Stimmzettel erfasst. Sobald ein Stimmzettel gespeichert ist, erscheint hier der abgeleitete Zwischenstand.</Alert>
      : <StandingsTable entries={standings.entries} />}
  </>
}

function StandingsTable({ entries }: { entries: ShowStanding[] }) {
  if (entries.length === 0) return <Alert severity="info">Für diese Show sind noch keine Beiträge vorhanden.</Alert>
  return <Paper component="section" sx={{ p: 2 }}>
    <Typography component="h3" sx={{ mb: 2 }} variant="h6">Punkte aus erfassten Stimmzetteln</Typography>
    <TableContainer aria-label="Zwischenstand horizontal scrollen" role="region" sx={{ overflowX: 'auto' }} tabIndex={0}>
      <Table aria-label="Abgeleiteter Zwischenstand" size="small" sx={{ minWidth: 720 }}>
        <caption>Punktgleiche Beiträge erhalten denselben Zwischenrang. Die Anzeigereihenfolge gleich hoher Punktzahlen ist keine fachliche Tie-Break-Regel.</caption>
        <TableHead><TableRow><TableCell>Zwischenrang</TableCell><TableCell>Beitrag</TableCell><TableCell>Einreichung</TableCell><TableCell align="right">Punkte</TableCell><TableCell align="right">Nennungen</TableCell></TableRow></TableHead>
        <TableBody>{entries.map((entry) => <StandingRow entry={entry} key={entry.entryId} />)}</TableBody>
      </Table>
    </TableContainer>
  </Paper>
}

function StandingRow({ entry }: { entry: ShowStanding }) {
  return <TableRow>
    <TableCell>{entry.interimRank}</TableCell>
    <TableCell><Typography>{entry.artist} – {entry.title}</Typography></TableCell>
    <TableCell>{entry.submitterDisplayName === null
      ? '—'
      : <Stack direction="row" spacing={1} sx={{ alignItems: 'center', minWidth: 160 }}>
        <CountryFlag code={entry.submitterCountryCode} countryName={entry.submitterCountryName} />
        <Box><Typography>{entry.submitterDisplayName}</Typography><Typography color="text.secondary" variant="body2">{entry.submitterCountryName ?? '—'}</Typography></Box>
      </Stack>}</TableCell>
    <TableCell align="right">{entry.points}</TableCell>
    <TableCell align="right">{entry.mentions}</TableCell>
  </TableRow>
}

function asShowStandingsApiError(error: unknown, showId: number) {
  if (error instanceof ShowStandingsApiError) return error
  return new ShowStandingsApiError({ timestamp: new Date().toISOString(), status: 0, code: 'NETWORK_ERROR', message: 'Der Zwischenstand konnte nicht geladen werden.', path: `/api/shows/${showId}/published-ballots/standings` })
}
