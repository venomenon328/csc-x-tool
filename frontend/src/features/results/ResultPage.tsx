import {
  Alert,
  Box,
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  MenuItem,
  Paper,
  Select,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { useEffect, useState } from 'react'
import { Link as RouterLink, useParams } from 'react-router-dom'
import { ApiErrorNotice } from '../../components/ApiErrorNotice'
import { CountryFlag } from '../participants/CountryFlag'
import { fetchShows, type MottoShow } from '../shows/api'
import {
  closeResults,
  fetchResult,
  reopenResults,
  ResultApiError,
  type ReceivedScoreLine,
  type ReceivedScoreStatus,
  type ShowResult,
  updateReceivedScore,
  updateResultDetails,
} from './api'

const allowedPoints = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 16, 20, 25]

export function ResultPage() {
  const parsedShowId = Number(useParams().showId)
  const showId = Number.isSafeInteger(parsedShowId) && parsedShowId > 0 ? parsedShowId : null
  const [show, setShow] = useState<MottoShow | null>(null)
  const [result, setResult] = useState<ShowResult | null>(null)
  const [error, setError] = useState<ResultApiError | null>(null)
  const [confirmation, setConfirmation] = useState<'close' | 'reopen' | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    let cancelled = false

    async function loadResult() {
      if (showId === null) return
      try {
        const [shows, loadedResult] = await Promise.all([fetchShows(), fetchResult(showId)])
        if (cancelled) return
        setError(null)
        setShow(shows.find((item) => item.id === showId) ?? null)
        setResult(loadedResult)
      } catch (caught) {
        if (cancelled) return
        setResult(null)
        setError(asResultApiError(caught, `/api/shows/${showId}/results`))
      }
    }

    void loadResult()
    return () => { cancelled = true }
  }, [showId])

  async function saveScore(line: ReceivedScoreLine, status: ReceivedScoreStatus, points: number | null) {
    if (showId === null) return
    setError(null)
    setSaving(true)
    try {
      setResult(await updateReceivedScore(showId, line.participantId, status, points))
    } catch (caught) {
      setError(asResultApiError(caught, `/api/shows/${showId}/results/scores/${line.participantId}`))
    } finally {
      setSaving(false)
    }
  }

  async function saveDetails(input: Pick<ShowResult, 'officialTotalPoints' | 'finalPlace' | 'finalPlaceTied'>) {
    if (showId === null) return
    setError(null)
    setSaving(true)
    try {
      setResult(await updateResultDetails(showId, input))
    } catch (caught) {
      setError(asResultApiError(caught, `/api/shows/${showId}/results/details`))
    } finally {
      setSaving(false)
    }
  }

  async function confirmLifecycleAction() {
    if (showId === null || confirmation === null) return
    setSaving(true)
    setError(null)
    try {
      setResult(confirmation === 'close' ? await closeResults(showId) : await reopenResults(showId))
      setConfirmation(null)
    } catch (caught) {
      setError(asResultApiError(caught, `/api/shows/${showId}/results/${confirmation}`))
    } finally {
      setSaving(false)
    }
  }

  if (showId === null) return <Alert severity="error">Die Mottoshow-ID ist ungültig.</Alert>
  const editable = result !== null && result.ballotClosedAt !== null && result.resultsClosedAt === null

  return (
    <Stack spacing={3}>
      <Button component={RouterLink} sx={{ alignSelf: 'flex-start' }} to="/">Zur Übersicht</Button>
      <Box>
        <Typography color="secondary" variant="overline">{show === null ? 'Mottoshow' : `Show ${show.showNumber}`}</Typography>
        <Typography component="h1" variant="h4">{show?.name ?? 'Ergebnis der eigenen Einreichung'}</Typography>
      </Box>
      {error !== null && <ApiErrorNotice error={error.apiError} />}
      {result === null && error === null && <ResultLoading />}
      {result !== null && (
        <>
          {result.ballotClosedAt === null && <Alert severity="info">Die Top 15 ist noch nicht abgeschlossen. Ergebnisdaten sind bis dahin serverseitig gesperrt.</Alert>}
          {result.resultsClosedAt !== null && <Alert severity="success">Die Ergebniserfassung ist abgeschlossen und schreibgeschützt. Für Korrekturen bitte bewusst wieder öffnen.</Alert>}
          <SubmissionCard submission={result.selectedCandidate} />
          <ScoreSummary result={result} />
          <Paper component="section" sx={{ overflowX: 'auto', p: 2 }}>
            <Stack spacing={1} sx={{ mb: 2 }}>
              <Typography component="h2" variant="h6">Erhaltene Punkte</Typography>
              <Typography color="text.secondary">Aktive Teilnehmer werden bis zur Erfassung als unbekannt geführt. Historische Ergebnisse inaktiver Teilnehmer bleiben erhalten.</Typography>
            </Stack>
            <Table aria-label="Erhaltene Punkte je Teilnehmer" size="small">
              <TableHead><TableRow><TableCell>Teilnehmer</TableCell><TableCell>Status</TableCell><TableCell>Punkte</TableCell></TableRow></TableHead>
              <TableBody>
                {result.lines.map((line) => <ScoreRow disabled={!editable || saving} key={line.participantId} line={line} onChange={saveScore} />)}
              </TableBody>
            </Table>
          </Paper>
          <ResultDetails disabled={!editable || saving} onSave={saveDetails} result={result} />
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
            {result.resultsClosedAt === null
              ? <Button disabled={!editable || saving} onClick={() => setConfirmation('close')} variant="contained">Ergebniserfassung abschließen</Button>
              : <Button disabled={saving} onClick={() => setConfirmation('reopen')} variant="outlined">Ergebniserfassung wieder öffnen</Button>}
          </Stack>
        </>
      )}
      <Dialog onClose={() => !saving && setConfirmation(null)} open={confirmation !== null}>
        <DialogTitle>{confirmation === 'close' ? 'Ergebniserfassung abschließen?' : 'Ergebniserfassung wieder öffnen?'}</DialogTitle>
        <DialogContent><Typography>{confirmation === 'close'
          ? 'Der Ergebnisstand wird danach serverseitig gegen Änderungen geschützt.'
          : 'Die bestehenden Werte bleiben erhalten und können anschließend korrigiert werden.'}</Typography></DialogContent>
        <DialogActions>
          <Button disabled={saving} onClick={() => setConfirmation(null)}>Abbrechen</Button>
          <Button disabled={saving} onClick={() => void confirmLifecycleAction()} variant="contained">Bewusst bestätigen</Button>
        </DialogActions>
      </Dialog>
    </Stack>
  )
}

function SubmissionCard({ submission }: { submission: ShowResult['selectedCandidate'] }) {
  return <Paper component="section" sx={{ p: 2 }}>
    <Typography color="secondary" variant="overline">Eigene Einreichung</Typography>
    {submission === null
      ? <Alert severity="warning">Noch keine eigene Einreichung gewählt. Die Ergebnisdaten können vorbereitet, aber nicht abgeschlossen werden.</Alert>
      : <Typography component="h2" variant="h6">{submission.artist} – {submission.title}</Typography>}
  </Paper>
}

function ScoreSummary({ result }: { result: ShowResult }) {
  return <Paper component="section" sx={{ p: 2 }}>
    <Stack spacing={0.5}>
      <Typography component="h2" variant="h6">Punktesumme</Typography>
      <Typography>Berechnet: {result.calculatedTotalPoints} Punkte</Typography>
      {result.officialTotalPoints !== null && <Typography>Offiziell: {result.officialTotalPoints} Punkte</Typography>}
      {result.officialTotalDifference !== null && result.officialTotalDifference !== 0 && <Alert severity="warning">Die offizielle Summe weicht um {Math.abs(result.officialTotalDifference)} Punkte ab.</Alert>}
      {result.finalPlace !== null && <Typography>Endplatzierung: {result.finalPlace}. Platz{result.finalPlaceTied ? ' (geteilt)' : ''}</Typography>}
    </Stack>
  </Paper>
}

function ScoreRow({ line, disabled, onChange }: {
  line: ReceivedScoreLine
  disabled: boolean
  onChange: (line: ReceivedScoreLine, status: ReceivedScoreStatus, points: number | null) => void
}) {
  const nextStatus = (status: ReceivedScoreStatus) => onChange(line, status, status === 'ABGESTIMMT' ? line.points ?? 0 : null)
  return <TableRow sx={{ opacity: line.active ? 1 : 0.7 }}>
    <TableCell>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
        <CountryFlag code={line.countryCode} countryName={line.countryName} />
        <Box><Typography>{line.displayName}{line.active ? '' : ' (inaktiv)'}</Typography><Typography color="text.secondary" variant="body2">{line.countryName}</Typography></Box>
      </Stack>
    </TableCell>
    <TableCell>
      <Select aria-label={`Status von ${line.displayName}`} disabled={disabled} onChange={(event) => nextStatus(event.target.value as ReceivedScoreStatus)} size="small" value={line.status}>
        <MenuItem value="UNBEKANNT">Unbekannt</MenuItem><MenuItem value="NICHT_ABGESTIMMT">Nicht abgestimmt</MenuItem><MenuItem value="ABGESTIMMT">Abgestimmt</MenuItem>
      </Select>
    </TableCell>
    <TableCell>
      {line.status === 'ABGESTIMMT'
        ? <Select aria-label={`Punkte von ${line.displayName}`} disabled={disabled} onChange={(event) => onChange(line, 'ABGESTIMMT', Number(event.target.value))} size="small" value={line.points ?? 0}>
          {allowedPoints.map((points) => <MenuItem key={points} value={points}>{points} Punkte</MenuItem>)}
        </Select>
        : <Typography color="text.secondary">—</Typography>}
    </TableCell>
  </TableRow>
}

function ResultDetails({ result, disabled, onSave }: {
  result: ShowResult
  disabled: boolean
  onSave: (input: Pick<ShowResult, 'officialTotalPoints' | 'finalPlace' | 'finalPlaceTied'>) => void
}) {
  const [officialTotalPoints, setOfficialTotalPoints] = useState<string>(result.officialTotalPoints?.toString() ?? '')
  const [finalPlace, setFinalPlace] = useState<string>(result.finalPlace?.toString() ?? '')
  const [finalPlaceTied, setFinalPlaceTied] = useState(result.finalPlaceTied)
  const save = () => onSave({
    officialTotalPoints: optionalNumber(officialTotalPoints), finalPlace: optionalNumber(finalPlace), finalPlaceTied,
  })
  return <Paper component="section" sx={{ p: 2 }}>
    <Stack spacing={2}>
      <Typography component="h2" variant="h6">Offizielles Ergebnis</Typography>
      <TextField disabled={disabled} label="Offizielle Gesamtpunktzahl (optional)" onChange={(event) => setOfficialTotalPoints(event.target.value)} slotProps={{ htmlInput: { min: 0 } }} type="number" value={officialTotalPoints} />
      <TextField disabled={disabled} label="Endplatzierung" onChange={(event) => setFinalPlace(event.target.value)} required slotProps={{ htmlInput: { min: 1 } }} type="number" value={finalPlace} />
      <FormControlLabel control={<Checkbox checked={finalPlaceTied} disabled={disabled} onChange={(event) => setFinalPlaceTied(event.target.checked)} />} label="Geteilter Platz" />
      <Box><Button disabled={disabled} onClick={save} variant="outlined">Ergebnisdaten speichern</Button></Box>
    </Stack>
  </Paper>
}

function optionalNumber(value: string): number | null {
  return value.trim() === '' ? null : Number(value)
}

function ResultLoading() {
  return <Stack spacing={1}>{Array.from({ length: 3 }, (_, index) => <Skeleton height={100} key={index} variant="rounded" />)}</Stack>
}

function asResultApiError(error: unknown, path: string): ResultApiError {
  if (error instanceof ResultApiError) return error
  return new ResultApiError({ timestamp: new Date().toISOString(), status: 0, code: 'NETWORK_ERROR', message: 'Die Ergebnisdaten konnten nicht verarbeitet werden.', path })
}
