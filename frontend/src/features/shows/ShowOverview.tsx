import { useEffect, useState } from 'react'
import {
  Alert,
  Box,
  Button,
  Card,
  CardActions,
  CardContent,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Skeleton,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { Link as RouterLink } from 'react-router-dom'
import { ApiErrorNotice } from '../../components/ApiErrorNotice'
import { fetchShows, renameShow, ShowApiError, type MottoShow } from './api'

export function ShowOverview() {
  const [shows, setShows] = useState<MottoShow[] | null>(null)
  const [loadError, setLoadError] = useState<ShowApiError | null>(null)
  const [editedShow, setEditedShow] = useState<MottoShow | null>(null)
  const [name, setName] = useState('')
  const [saveError, setSaveError] = useState<ShowApiError | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    void loadShows()
  }, [])

  async function loadShows() {
    setLoadError(null)
    try {
      setShows(await fetchShows())
    } catch (error) {
      setLoadError(asShowApiError(error))
      setShows(null)
    }
  }

  function openRenameDialog(show: MottoShow) {
    setEditedShow(show)
    setName(show.name)
    setSaveError(null)
  }

  function closeRenameDialog() {
    if (!saving) {
      setEditedShow(null)
      setSaveError(null)
    }
  }

  async function saveName() {
    if (editedShow === null) {
      return
    }

    setSaving(true)
    setSaveError(null)
    try {
      const renamed = await renameShow(editedShow.id, name)
      setShows((currentShows) => currentShows?.map((show) => show.id === renamed.id ? renamed : show) ?? null)
      setEditedShow(null)
    } catch (error) {
      setSaveError(asShowApiError(error))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Typography component="h1" variant="h4">Übersicht</Typography>
        <Typography color="text.secondary" sx={{ mt: 1 }}>
          Die zwölf Mottoshows werden lokal gespeichert. Die fachlichen Arbeitsbereiche wachsen mit den nächsten Paketen.
        </Typography>
      </Box>

      {loadError !== null && (
        <Stack spacing={1}>
          <ApiErrorNotice error={loadError.apiError} />
          <Box><Button onClick={() => void loadShows()}>Erneut versuchen</Button></Box>
        </Stack>
      )}

      {shows === null && loadError === null && <OverviewLoading />}

      {shows !== null && shows.length === 0 && (
        <Alert severity="info">Noch keine Mottoshows verfügbar.</Alert>
      )}

      {shows !== null && shows.length > 0 && (
        <Box
          aria-label="Mottoshow-Übersicht"
          sx={{ display: 'grid', gap: 2, gridTemplateColumns: 'repeat(auto-fit, minmax(290px, 1fr))' }}
        >
          {shows.map((show) => <ShowCard key={show.id} onRename={() => openRenameDialog(show)} show={show} />)}
        </Box>
      )}

      <Dialog fullWidth maxWidth="sm" onClose={closeRenameDialog} open={editedShow !== null}>
        <DialogTitle>Mottoshow umbenennen</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <Typography color="text.secondary">
              {editedShow === null ? '' : `Show ${editedShow.showNumber}`}
            </Typography>
            {saveError !== null && <ApiErrorNotice error={saveError.apiError} />}
            <TextField
              autoFocus
              fullWidth
              label="Name der Mottoshow"
              onChange={(event) => setName(event.target.value)}
              value={name}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button disabled={saving} onClick={closeRenameDialog}>Abbrechen</Button>
          <Button disabled={saving} onClick={() => void saveName()} variant="contained">
            {saving ? 'Speichert …' : 'Speichern'}
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  )
}

function ShowCard({ show, onRename }: { show: MottoShow, onRename: () => void }) {
  const submission = show.selectedCandidate == null
    ? 'Eigene Einreichung: noch nicht festgelegt'
    : `Eigene Einreichung: ${show.selectedCandidate.artist} – ${show.selectedCandidate.title}`
  const candidateCount = show.candidateCount ?? 0
  const rankedEntryCount = show.rankedEntryCount ?? 0
  const ballotStatus = show.ballotClosedAt === null || show.ballotClosedAt === undefined
    ? 'Top 15: noch nicht abgeschlossen'
    : 'Top 15: abgeschlossen'
  const assignmentProgress = `${show.assignedEntryCount ?? 0}/${show.contestEntryCount ?? 0}`
  const resultStatus = show.ballotClosedAt === null || show.ballotClosedAt === undefined
    ? 'Ergebnis: nach Abschluss der Top 15'
    : show.resultsClosedAt === null || show.resultsClosedAt === undefined
    ? `Ergebnis: ${show.knownActiveResultCount ?? 0}/${show.activeParticipantCount ?? 0} aktive Teilnehmer erfasst`
    : 'Ergebnis: abgeschlossen'
  return (
    <Card component="section" elevation={0} sx={{ border: 1, borderColor: 'divider' }}>
      <CardContent>
        <Typography color="secondary" variant="overline">Show {show.showNumber}</Typography>
        <Typography component="h2" sx={{ minHeight: 56 }} variant="h6">{show.name}</Typography>
        <Stack spacing={0.5} sx={{ mt: 2 }}>
          <Typography color="text.secondary" variant="body2">{submission}</Typography>
          <Typography color="text.secondary" variant="body2">
            Kandidaten: {candidateCount === 1 ? '1 Kandidat' : `${candidateCount} Kandidaten`}
          </Typography>
          <Typography color="text.secondary" variant="body2">
            Wettbewerbsbeiträge: {show.contestEntryCount === 1 ? '1 Beitrag' : `${show.contestEntryCount} Beiträge`}
          </Typography>
          <Typography color="text.secondary" variant="body2">
            Gehört: {show.listenedEntryCount === 1 ? '1 Beitrag' : `${show.listenedEntryCount} Beiträge`}
          </Typography>
          <Typography color="text.secondary" variant="body2">
            Eingeordnet: {rankedEntryCount === 1 ? '1 Beitrag' : `${rankedEntryCount} Beiträge`}
          </Typography>
          <Typography color="text.secondary" variant="body2">{ballotStatus}</Typography>
          {show.ballotClosedAt !== null && show.ballotClosedAt !== undefined && <Typography color="text.secondary" variant="body2">Teilnehmer zugeordnet: {assignmentProgress}</Typography>}
          <Typography color="text.secondary" variant="body2">{resultStatus}</Typography>
          {show.ballotClosedAt !== null && show.ballotClosedAt !== undefined && <Typography color="text.secondary" variant="body2">Berechnet: {show.calculatedTotalPoints ?? 0} Punkte{show.officialTotalPoints != null ? ` · Offiziell: ${show.officialTotalPoints} Punkte` : ''}</Typography>}
          {show.officialTotalDifference != null && show.officialTotalDifference !== 0 && <Alert severity="warning">Die offizielle Summe weicht um {Math.abs(show.officialTotalDifference)} Punkte ab.</Alert>}
          {show.finalPlace !== null && <Typography color="text.secondary" variant="body2">Endplatzierung: {show.finalPlace}. Platz{show.finalPlaceTied ? ' (geteilt)' : ''}</Typography>}
        </Stack>
      </CardContent>
      <CardActions sx={{ flexWrap: 'wrap' }}>
        <Button aria-label={`Show ${show.showNumber} bearbeiten`} onClick={onRename} size="small">Name bearbeiten</Button>
        <Button component={RouterLink} size="small" to={`/shows/${show.id}/candidates`}>Kandidaten</Button>
        <Button component={RouterLink} size="small" to={`/shows/${show.id}/voting`}>Abstimmung</Button>
        <Button component={RouterLink} size="small" to={`/shows/${show.id}/result`}>Ergebnis</Button>
      </CardActions>
    </Card>
  )
}

function OverviewLoading() {
  return (
    <Box aria-label="Mottoshows werden geladen" sx={{ display: 'grid', gap: 2, gridTemplateColumns: 'repeat(auto-fit, minmax(290px, 1fr))' }}>
      {Array.from({ length: 3 }, (_, index) => (
        <Card key={index} sx={{ p: 2 }}>
          <Skeleton width="30%" />
          <Skeleton height={44} />
          <Skeleton />
          <Skeleton />
        </Card>
      ))}
    </Box>
  )
}

function asShowApiError(error: unknown): ShowApiError {
  if (error instanceof ShowApiError) {
    return error
  }
  return new ShowApiError({
    timestamp: new Date().toISOString(),
    status: 0,
    code: 'NETWORK_ERROR',
    message: 'Die Übersicht konnte nicht geladen werden.',
    path: '/api/shows',
  })
}
