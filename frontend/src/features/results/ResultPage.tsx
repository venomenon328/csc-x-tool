import { Alert, Box, Button, Paper, Skeleton, Stack, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material'
import { useEffect, useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { ApiErrorNotice } from '../../components/ApiErrorNotice'
import { CountryFlag } from '../participants/CountryFlag'
import { evaluationPath } from '../shows/showWorkflow'
import { fetchResult, ResultApiError, type DerivedResultLine, type ShowResult } from './api'

export function OwnEntryEvaluation({ showId }: { showId: number }) {
  const [result, setResult] = useState<ShowResult | null>(null)
  const [error, setError] = useState<ResultApiError | null>(null)

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        const loadedResult = await fetchResult(showId)
        if (cancelled) return
        setResult(loadedResult); setError(null)
      } catch (caught) {
        if (!cancelled) { setResult(null); setError(asResultApiError(caught, `/api/shows/${showId}/results`)) }
      }
    }
    void load()
    return () => { cancelled = true }
  }, [showId])

  return <Stack component="section" spacing={3}>
    <Box>
      <Typography component="h2" variant="h5">Meine Einreichung</Typography>
    </Box>
    {error !== null && <ApiErrorNotice error={error.apiError} />}
    {result === null && error === null && <ResultLoading />}
    {result?.prerequisite === 'OWN_PARTICIPATION_MISSING' && <Alert action={<Button component={RouterLink} to="/participants">Teilnehmer öffnen</Button>} severity="info">Markiere in der Teilnehmerliste zuerst ausdrücklich deine Contest-Teilnahme. Es wird keine Identität aus einem Namen erraten.</Alert>}
    {result?.prerequisite === 'OWN_ENTRY_UNRESOLVED' && <Alert action={<Button component={RouterLink} to={`/shows/${showId}/voting`}>Abstimmung öffnen</Button>} severity="warning">Bestätige vor der Ergebnisableitung, ob du in dieser Show selbst eine tatsächliche Einreichung hast.</Alert>}
    {result?.prerequisite === 'OWN_ENTRY_NONE' && <Alert severity="info">Für diese Show ist ausdrücklich bestätigt, dass du keine eigene Einreichung hast. Daher gibt es keine persönliche Ergebnisableitung.</Alert>}
    {result?.prerequisite === 'ENTRY_LIST_INCOMPLETE' && <Alert severity="warning">Die vollständige Songzuordnung dieser Show ist noch nicht bestätigt. Deshalb werden aus veröffentlichten Stimmzetteln noch keine eigenen Ergebnisse abgeleitet.</Alert>}
    {result?.prerequisite === 'OWN_ENTRY_MISSING' && <Alert severity="warning">Für deine gewählte Contest-Teilnahme ist dieser Show noch keine tatsächliche Einreichung zugeordnet. Deshalb können keine erhaltenen Bewertungen abgeleitet werden.</Alert>}
    {result?.prerequisite === 'READY' && <DerivedResult result={result} showId={showId} />}
  </Stack>
}

function DerivedResult({ result, showId }: { result: ShowResult, showId: number }) {
  const ownEntry = result.ownEntry
  if (ownEntry === null) return null
  return <>
    <Paper component="section" sx={{ p: 2 }}><Typography color="secondary" variant="overline">Eigene tatsächliche Einreichung</Typography><Typography component="h3" variant="h6">{ownEntry.artist} – {ownEntry.title}</Typography>{result.selectedCandidateDiffers && <Alert severity="info" sx={{ mt: 1 }}>Die Kandidatenplanung weicht von der zugeordneten Wettbewerbseinreichung ab. Es wurden keine Daten automatisch geändert.</Alert>}</Paper>
    <Paper component="section" sx={{ p: 2 }}><Typography component="h3" variant="h6">Veröffentlichte Stimmzettel</Typography><Typography color="text.secondary">{result.votedCount} abgegeben · {result.notVotedCount} nicht abgestimmt · {result.unrecordedCount} unerfasst</Typography><Typography sx={{ mt: 1 }}>Abgeleitete Summe: {result.derivedTotalPoints} Punkte</Typography></Paper>
    <Paper component="section" sx={{ overflowX: 'auto', p: 2 }}>
      <Typography component="h3" sx={{ mb: 2 }} variant="h6">Erhaltene Bewertung je Teilnehmer</Typography>
      <Table aria-label="Abgeleitete Bewertung der eigenen Einreichung" size="small"><TableHead><TableRow><TableCell>Teilnehmer</TableCell><TableCell>Status</TableCell><TableCell>Rang</TableCell><TableCell>Punkte</TableCell><TableCell>Quelle</TableCell></TableRow></TableHead><TableBody>{result.lines.map((line) => <DerivedLine key={line.participationId} line={line} showId={showId} />)}</TableBody></Table>
    </Paper>
  </>
}

function DerivedLine({ line, showId }: { line: DerivedResultLine, showId: number }) {
  const status = line.state === 'OWN_ENTRY' ? 'Eigene Einreichung · nicht wählbar'
    : line.state === 'RANKED' ? 'In Top 15'
    : line.state === 'OUTSIDE_TOP_15' ? 'Außerhalb Top 15'
    : line.state === 'NO_BALLOT' ? 'Nicht abgestimmt'
    : 'Unbekannt'
  return <TableRow><TableCell><Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}><CountryFlag code={line.countryCode} countryName={line.countryName} /><Box><Typography>{line.displayName}</Typography><Typography color="text.secondary" variant="body2">{line.countryName}</Typography></Box></Stack></TableCell><TableCell>{status}</TableCell><TableCell>{line.rank === null ? '—' : line.rank}</TableCell><TableCell>{line.points === null ? '—' : `${line.points} Punkte`}</TableCell><TableCell>{line.state === 'OWN_ENTRY' ? '—' : <Button component={RouterLink} size="small" to={evaluationPath(showId, 'published-ballots')}>Stimmzettel</Button>}</TableCell></TableRow>
}

function ResultLoading() { return <Stack spacing={1}>{Array.from({ length: 3 }, (_, index) => <Skeleton height={100} key={index} variant="rounded" />)}</Stack> }
function asResultApiError(error: unknown, path: string): ResultApiError {
  if (error instanceof ResultApiError) return error
  return new ResultApiError({ timestamp: new Date().toISOString(), status: 0, code: 'NETWORK_ERROR', message: 'Die Ergebnisdaten konnten nicht verarbeitet werden.', path })
}
