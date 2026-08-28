import { useEffect, useState } from 'react'
import {
  Alert,
  Box,
  Button,
  Card,
  CardActions,
  CardContent,
  Chip,
  Divider,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  Menu,
  MenuItem,
  Skeleton,
  Stack,
  TextField,
  Tooltip,
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
          sx={{ display: 'grid', gap: 2, gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 340px), 1fr))' }}
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
  const [actionMenuAnchor, setActionMenuAnchor] = useState<HTMLElement | null>(null)
  const candidateCount = show.candidateCount ?? 0
  const contestEntryCount = show.contestEntryCount ?? 0
  const listenedEntryCount = show.listenedEntryCount ?? 0
  const rankedEntryCount = show.rankedEntryCount ?? 0
  const ballotClosed = show.ballotClosedAt !== null && show.ballotClosedAt !== undefined
  const resultsClosed = show.resultsClosedAt !== null && show.resultsClosedAt !== undefined
  const actionMenuOpen = actionMenuAnchor !== null

  function closeActionMenu() {
    setActionMenuAnchor(null)
  }

  function startRename() {
    closeActionMenu()
    onRename()
  }

  return (
    <Card component="section" elevation={0} sx={{ border: 1, borderColor: 'divider', display: 'flex', flexDirection: 'column' }}>
      <CardContent sx={{ display: 'flex', flexDirection: 'column', gap: 2.5, flexGrow: 1 }}>
        <Box>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', justifyContent: 'space-between' }}>
            <Typography color="secondary" variant="overline">Show {show.showNumber}</Typography>
            <Chip label={formatCount(candidateCount, 'Kandidat', 'Kandidaten')} size="small" />
          </Stack>
          <Typography component="h2" sx={{ overflowWrap: 'anywhere' }} variant="h6">{show.name}</Typography>
        </Box>

        <SubmissionBlock selectedCandidate={show.selectedCandidate} />

        <Box aria-label="Arbeitsfortschritt">
          <Typography color="text.secondary" variant="overline">Arbeitsfortschritt</Typography>
          <Box sx={{ display: 'grid', gap: 1.5, gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', mt: 0.5 }}>
            <ProgressMetric count={contestEntryCount} label="Wettbewerbsbeiträge" total={contestEntryCount} />
            <ProgressMetric count={listenedEntryCount} label="Gehört" total={contestEntryCount} />
            <ProgressMetric count={rankedEntryCount} label="Eingeordnet" total={contestEntryCount} />
          </Box>
        </Box>

        <Box aria-label="Workflowstatus">
          <Typography color="text.secondary" variant="overline">Workflowstatus</Typography>
          <Stack divider={<Divider flexItem />} spacing={1} sx={{ mt: 0.5 }}>
            <WorkflowStatus
              label="Top 15"
              status={ballotClosed ? 'Abgeschlossen' : 'Noch nicht abgeschlossen'}
              tone={ballotClosed ? 'success.main' : 'text.secondary'}
            />
            {ballotClosed && (
              <WorkflowStatus
                label="Teilnehmer zugeordnet"
                status={`${show.assignedEntryCount ?? 0} / ${contestEntryCount} Beiträge`}
              />
            )}
            <WorkflowStatus
              label="Ergebnis"
              status={!ballotClosed
                ? 'Nach Abschluss der Top 15 verfügbar'
                : resultsClosed
                ? 'Abgeschlossen'
                : `${show.knownActiveResultCount ?? 0} / ${show.activeParticipantCount ?? 0} aktive Teilnehmer erfasst`}
              tone={resultsClosed ? 'success.main' : undefined}
            />
          </Stack>

          {ballotClosed && <ResultDetails show={show} />}
          {show.finalPlace !== null && <Typography color="text.secondary" sx={{ mt: ballotClosed ? 1.5 : 1 }} variant="body2">Endplatzierung: {show.finalPlace}. Platz{show.finalPlaceTied ? ' (geteilt)' : ''}</Typography>}
        </Box>
      </CardContent>
      <CardActions sx={{ borderTop: 1, borderColor: 'divider', display: 'grid', gap: 1, gridTemplateColumns: 'minmax(0, 1fr) auto', p: 2 }}>
        <Box aria-label={`Arbeitsbereiche für Show ${show.showNumber}`} component="nav" sx={{ display: 'grid', gap: 0.5, gridTemplateColumns: 'repeat(3, minmax(0, 1fr))' }}>
          <Button component={RouterLink} size="small" sx={{ minWidth: 0, whiteSpace: 'normal' }} to={`/shows/${show.id}/candidates`} variant="outlined">Kandidaten</Button>
          <Button component={RouterLink} size="small" sx={{ minWidth: 0, whiteSpace: 'normal' }} to={`/shows/${show.id}/voting`} variant="outlined">Abstimmung</Button>
          <Button component={RouterLink} size="small" sx={{ minWidth: 0, whiteSpace: 'normal' }} to={`/shows/${show.id}/result`} variant="outlined">Ergebnis</Button>
        </Box>
        <Tooltip title="Weitere Aktionen">
          <IconButton
            aria-controls={actionMenuOpen ? `show-${show.id}-actions` : undefined}
            aria-expanded={actionMenuOpen ? 'true' : undefined}
            aria-haspopup="menu"
            aria-label={`Weitere Aktionen für Show ${show.showNumber}`}
            onClick={(event) => setActionMenuAnchor(event.currentTarget)}
          >
            <Box aria-hidden="true" component="span" sx={{ fontSize: '1.5rem', lineHeight: 1 }}>⋮</Box>
          </IconButton>
        </Tooltip>
        <Menu anchorEl={actionMenuAnchor} id={`show-${show.id}-actions`} onClose={closeActionMenu} open={actionMenuOpen}>
          <MenuItem onClick={startRename}>Name bearbeiten</MenuItem>
        </Menu>
      </CardActions>
    </Card>
  )
}

function SubmissionBlock({ selectedCandidate }: { selectedCandidate: MottoShow['selectedCandidate'] }) {
  return (
    <Box sx={{ backgroundColor: 'action.hover', borderLeft: 3, borderColor: 'secondary.main', borderRadius: 1, p: 1.5 }}>
      <Typography color="text.secondary" variant="overline">Dein Beitrag</Typography>
      {selectedCandidate === null || selectedCandidate === undefined
        ? <Typography sx={{ mt: 0.25 }} variant="body2">Noch nicht festgelegt</Typography>
        : <Stack spacing={0.25} sx={{ mt: 0.25 }}>
          <Typography sx={{ overflowWrap: 'anywhere' }} variant="body2">{selectedCandidate.artist}</Typography>
          <Typography sx={{ overflowWrap: 'anywhere' }} variant="body1">{selectedCandidate.title}</Typography>
        </Stack>}
    </Box>
  )
}

function ProgressMetric({ count, label, total }: { count: number, label: string, total: number }) {
  const progress = total > 0 ? `${count} / ${total}` : String(count)
  return (
    <Box sx={{ minWidth: 0 }}>
      <Typography sx={{ fontVariantNumeric: 'tabular-nums', lineHeight: 1.2 }} variant="h6">{progress}</Typography>
      <Typography color="text.secondary" sx={{ display: 'block', lineHeight: 1.25, mt: 0.5 }} variant="caption">{label}</Typography>
      <Typography color="text.secondary" sx={{ display: 'block', lineHeight: 1.25 }} variant="caption">{formatCount(count, 'Beitrag', 'Beiträge')}</Typography>
    </Box>
  )
}

function WorkflowStatus({ label, status, tone = 'text.primary' }: { label: string, status: string, tone?: string }) {
  return (
    <Stack direction="row" spacing={1} sx={{ alignItems: 'baseline', justifyContent: 'space-between' }}>
      <Typography sx={{ flexShrink: 0 }} variant="body2">{label}</Typography>
      <Typography color={tone} sx={{ overflowWrap: 'anywhere', textAlign: 'right' }} variant="body2">{status}</Typography>
    </Stack>
  )
}

function ResultDetails({ show }: { show: MottoShow }) {
  return (
    <Box sx={{ backgroundColor: 'action.hover', borderRadius: 1, mt: 1.5, p: 1.5 }}>
      <Typography color="text.secondary" variant="overline">Punkte</Typography>
      <Stack direction="row" spacing={3} sx={{ mt: 0.25 }}>
        <ScoreSummary label="Berechnet" value={show.calculatedTotalPoints ?? 0} />
        {show.officialTotalPoints !== null && show.officialTotalPoints !== undefined && <ScoreSummary label="Offiziell" value={show.officialTotalPoints} />}
      </Stack>
      {show.officialTotalDifference != null && show.officialTotalDifference !== 0 && <Alert severity="warning" sx={{ mt: 1 }}>Die offizielle Summe weicht um {Math.abs(show.officialTotalDifference)} Punkte ab.</Alert>}
    </Box>
  )
}

function ScoreSummary({ label, value }: { label: string, value: number }) {
  return (
    <Box>
      <Typography sx={{ fontVariantNumeric: 'tabular-nums', lineHeight: 1.2 }} variant="h6">{value} Punkte</Typography>
      <Typography color="text.secondary" variant="caption">{label}</Typography>
    </Box>
  )
}

function formatCount(count: number, singular: string, plural: string) {
  return count === 1 ? `1 ${singular}` : `${count} ${plural}`
}

function OverviewLoading() {
  return (
    <Box aria-label="Mottoshows werden geladen" sx={{ display: 'grid', gap: 2, gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 340px), 1fr))' }}>
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
