import { useCallback, useEffect, useState, type ReactNode } from 'react'
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
  Skeleton,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import { Link as RouterLink } from 'react-router-dom'
import {
  CandidateIcon,
  CheckIcon,
  ClockIcon,
  EditIcon,
  EntriesIcon,
  PlayIcon,
  RankIcon,
  ResultIcon,
  SubmissionIcon,
} from '../../components/AppIcons'
import { ApiErrorNotice } from '../../components/ApiErrorNotice'
import { fetchShows, renameShow, ShowApiError, type MottoShow } from './api'
import { useContest } from '../contests/ContestContext'
import { HistoricalContestPage } from '../history/HistoricalContestPage'
import { evaluationAvailabilityForShow, evaluationStatusForShow, ownEntryStatusForShow, primaryActionForShow } from './showWorkflow'

export function ShowOverview() {
  const { selectedContest } = useContest()
  return selectedContest !== null && !selectedContest.current ? <HistoricalContestPage /> : <CurrentShowOverview />
}

function CurrentShowOverview() {
  const { selectedContestId } = useContest()
  const [shows, setShows] = useState<MottoShow[] | null>(null)
  const [loadError, setLoadError] = useState<ShowApiError | null>(null)
  const [editedShow, setEditedShow] = useState<MottoShow | null>(null)
  const [name, setName] = useState('')
  const [saveError, setSaveError] = useState<ShowApiError | null>(null)
  const [saving, setSaving] = useState(false)

  const loadShows = useCallback(async () => {
    setLoadError(null)
    try {
      setShows(await fetchShows(selectedContestId ?? undefined))
    } catch (error) {
      setLoadError(asShowApiError(error))
      setShows(null)
    }
  }, [selectedContestId])

  useEffect(() => {
    let disposed = false
    void fetchShows(selectedContestId ?? undefined).then((loadedShows) => {
      if (disposed) return
      setLoadError(null)
      setShows(loadedShows)
    }).catch((error: unknown) => {
      if (disposed) return
      setLoadError(asShowApiError(error))
      setShows(null)
    })
    return () => { disposed = true }
  }, [selectedContestId])

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
      <Typography component="h1" variant="h4">Übersicht</Typography>

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
  const candidateCount = show.candidateCount ?? 0
  const contestEntryCount = show.contestEntryCount ?? 0
  const assessedEntryCount = show.assessedEntryCount ?? 0
  const rankedEntryCount = show.rankedEntryCount ?? 0
  const ballotClosed = show.ballotClosedAt !== null && show.ballotClosedAt !== undefined
  const primaryAction = primaryActionForShow(show)
  const evaluationAvailable = evaluationAvailabilityForShow(show) === 'AVAILABLE'
  const ownEntryStatus = ownEntryStatusForShow(show)
  const evaluationStatus = evaluationStatusForShow(show)

  return (
    <Card component="section" elevation={0} sx={{ border: 1, borderColor: 'divider', display: 'flex', flexDirection: 'column' }}>
      <CardContent sx={{ display: 'flex', flexDirection: 'column', gap: 2.5, flexGrow: 1 }}>
        <Box>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', justifyContent: 'space-between' }}>
            <Typography color="secondary" variant="overline">Show {show.showNumber}</Typography>
            <Chip label={formatCount(candidateCount, 'Kandidat', 'Kandidaten')} size="small" />
          </Stack>
          <Stack direction="row" spacing={0.5} sx={{ alignItems: 'flex-start', mt: 0.25 }}>
            <Typography component="h2" sx={{ flex: 1, minWidth: 0, overflowWrap: 'anywhere' }} variant="h6">{show.name}</Typography>
            <Tooltip title="Name bearbeiten">
              <IconButton
                aria-label={`Name von Show ${show.showNumber} bearbeiten`}
                onClick={onRename}
                size="small"
                sx={{ flexShrink: 0, mt: -0.25 }}
              >
                <EditIcon aria-hidden="true" fontSize="small" />
              </IconButton>
            </Tooltip>
          </Stack>
        </Box>

        <SubmissionBlock show={show} />

        <Box aria-label="Arbeitsfortschritt">
          <Typography color="text.secondary" variant="overline">Arbeitsfortschritt</Typography>
          <Box sx={{ display: 'grid', gap: 0.75, gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', mt: 0.5 }}>
            <ProgressMetric count={contestEntryCount} kind="entries" label="Beiträge" total={contestEntryCount} />
            <ProgressMetric count={assessedEntryCount} kind="assessed" label="Eingeschätzt" total={contestEntryCount} />
            <ProgressMetric count={rankedEntryCount} kind="ranked" label="Gerankt" total={contestEntryCount} />
          </Box>
        </Box>

        <Box aria-label="Workflowstatus">
          <Typography color="text.secondary" variant="overline">Workflowstatus</Typography>
          <Stack divider={<Divider flexItem />} spacing={1} sx={{ mt: 0.5 }}>
            {contestEntryCount === 0 && <WorkflowStatus
              icon={<CandidateIcon fontSize="small" />}
              label="Vorbereitung"
              status={candidateCount === 0 ? 'Kandidaten anlegen' : 'Beiträge vorbereiten'}
            />}
            {contestEntryCount > 0 && !ballotClosed && <>
              <WorkflowStatus icon={<ClockIcon fontSize="small" />} label="Top 15" status="Offen" />
              <WorkflowStatus icon={<SubmissionIcon fontSize="small" />} label="Eigene Einreichung" status={ownEntryStatus} />
              <WorkflowStatus icon={<ResultIcon fontSize="small" />} label="Auswertung" status={evaluationStatus} />
            </>}
            {contestEntryCount > 0 && ballotClosed && !evaluationAvailable && <>
              <WorkflowStatus icon={<CheckIcon fontSize="small" />} label="Top 15" status="Abgeschlossen" tone="success.main" />
              <WorkflowStatus icon={<SubmissionIcon fontSize="small" />} label="Eigene Einreichung" status={ownEntryStatus} />
              <WorkflowStatus
                icon={<CandidateIcon fontSize="small" />}
                label="Einreichende"
                status={`${show.assignedEntryCount ?? 0} / ${contestEntryCount} Beiträge`}
              />
            </>}
            {contestEntryCount > 0 && ballotClosed && evaluationAvailable && <>
              <WorkflowStatus icon={<CheckIcon fontSize="small" />} label="Top 15" status="Abgeschlossen" tone="success.main" />
              <WorkflowStatus icon={<SubmissionIcon fontSize="small" />} label="Eigene Einreichung" status={ownEntryStatus} />
              <WorkflowStatus icon={<ResultIcon fontSize="small" />} label="Auswertung" status={evaluationStatus} />
            </>}
          </Stack>
        </Box>
      </CardContent>
      <CardActions sx={{ alignItems: 'stretch', borderTop: 1, borderColor: 'divider', flexDirection: 'column', gap: 1, p: 1.5 }}>
        <Button component={RouterLink} fullWidth to={primaryAction.to} variant="contained">
          {primaryAction.label}
        </Button>
        <ShowNavigation evaluationAvailable={evaluationAvailable} show={show} />
      </CardActions>
    </Card>
  )
}

function ShowNavigation({ evaluationAvailable, show }: { evaluationAvailable: boolean, show: MottoShow }) {
  return <Box aria-label={`Arbeitsbereiche für Show ${show.showNumber}`} component="nav" sx={{ display: 'grid', gap: 0.5, gridTemplateColumns: 'repeat(4, minmax(44px, 1fr))', width: '100%' }}>
    <CompactNavigationLink icon={<CandidateIcon aria-hidden="true" />} label="Kandidaten" to={`/shows/${show.id}/candidates`} />
    <CompactNavigationLink icon={<PlayIcon aria-hidden="true" />} label="Abstimmung" to={`/shows/${show.id}/voting`} />
    <CompactNavigationLink icon={<ResultIcon aria-hidden="true" />} label="Auswertung" notYetAvailable={!evaluationAvailable} to={`/shows/${show.id}/evaluation?view=published-ballots`} />
    <CompactNavigationLink icon={<SubmissionIcon aria-hidden="true" />} label="Tippspiel" to={`/shows/${show.id}/tips`} />
  </Box>
}

function CompactNavigationLink({ icon, label, notYetAvailable = false, to }: { icon: ReactNode, label: string, notYetAvailable?: boolean, to: string }) {
  const accessibleLabel = notYetAvailable ? `${label}: noch nicht verfügbar` : label
  return <Tooltip title={accessibleLabel}><IconButton
    aria-label={accessibleLabel}
    component={RouterLink}
    data-workflow-state={notYetAvailable ? 'not-yet-available' : 'available'}
    size="medium"
    sx={{ border: 1, borderColor: 'divider', borderRadius: 1, minHeight: 44, minWidth: 44, width: '100%' }}
    to={to}
  >{icon}</IconButton></Tooltip>
}

function SubmissionBlock({ show }: { show: MottoShow }) {
  const selectedCandidate = show.selectedCandidate
  return (
    <Box sx={{ backgroundColor: 'action.hover', borderLeft: 3, borderColor: 'secondary.main', borderRadius: 1, p: 1.5 }}>
      <Stack direction="row" spacing={1.25} sx={{ alignItems: 'flex-start' }}>
        <SubmissionIcon aria-hidden="true" color="secondary" fontSize="small" sx={{ mt: 0.25 }} />
        <Box sx={{ minWidth: 0 }}>
          <Typography color="text.secondary" variant="overline">Kandidatenplanung</Typography>
          {selectedCandidate === null || selectedCandidate === undefined
            ? <Typography sx={{ mt: 0.25 }} variant="body2">Noch kein Kandidat ausgewählt</Typography>
            : <Stack spacing={0.25} sx={{ mt: 0.25 }}>
              <Typography color="text.secondary" sx={{ overflowWrap: 'anywhere' }} variant="body2">{selectedCandidate.artist}</Typography>
              <Typography sx={{ overflowWrap: 'anywhere' }} variant="body1">{selectedCandidate.title}</Typography>
            </Stack>}
          <Typography color="text.secondary" sx={{ display: 'block', mt: 1 }} variant="caption">Eigene tatsächliche Einreichung: {ownEntryStatusForShow(show)}</Typography>
        </Box>
      </Stack>
    </Box>
  )
}

type ProgressMetricKind = 'entries' | 'assessed' | 'ranked'

function ProgressMetric({ count, kind, label, total }: { count: number, kind: ProgressMetricKind, label: string, total: number }) {
  const Icon = kind === 'entries' ? EntriesIcon : kind === 'assessed' ? PlayIcon : RankIcon
  const visibleValue = kind === 'entries' || total === 0 ? String(count) : `${count} / ${total}`
  const accessibleLabel = kind === 'entries'
    ? formatCount(count, 'Beitrag', 'Beiträge')
    : total === 0
    ? `0 Beiträge ${kind === 'assessed' ? 'eingeschätzt' : 'gerankt'}`
    : `${count} von ${total} Beiträgen ${kind === 'assessed' ? 'eingeschätzt' : 'gerankt'}`

  return (
    <Box
      aria-label={accessibleLabel}
      role="group"
      sx={{ backgroundColor: 'action.hover', borderRadius: 1, minWidth: 0, p: 0.75 }}
    >
      <Stack aria-hidden="true" direction="row" spacing={0.5} sx={{ alignItems: 'center', minWidth: 0 }}>
        <Icon color="secondary" sx={{ flexShrink: 0, fontSize: '1rem' }} />
        <Box sx={{ minWidth: 0 }}>
          <Typography sx={{ fontSize: '0.9rem', fontVariantNumeric: 'tabular-nums', fontWeight: 700, lineHeight: 1.1, whiteSpace: 'nowrap' }}>
            {visibleValue}
          </Typography>
          <Typography color="text.secondary" sx={{ display: 'block', fontSize: '0.66rem', lineHeight: 1.2, mt: 0.25, whiteSpace: 'nowrap' }} variant="caption">
            {label}
          </Typography>
        </Box>
      </Stack>
    </Box>
  )
}

function WorkflowStatus({ icon, label, status, tone = 'text.primary' }: { icon: ReactNode, label: string, status: string, tone?: string }) {
  return (
    <Stack aria-label={`${label}: ${status}`} direction="row" spacing={0.75} sx={{ alignItems: 'center' }}>
      <Box aria-hidden="true" sx={{ color: tone, display: 'inline-flex', flexShrink: 0 }}>{icon}</Box>
      <Typography sx={{ flex: 1, minWidth: 0 }} variant="body2">{label}</Typography>
      <Typography color={tone} sx={{ maxWidth: '62%', overflowWrap: 'anywhere', textAlign: 'right' }} variant="body2">{status}</Typography>
    </Stack>
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
