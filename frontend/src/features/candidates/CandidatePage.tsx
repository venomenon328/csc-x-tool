import { DragDropContext, Draggable, Droppable, type DropResult } from '@hello-pangea/dnd'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControlLabel,
  IconButton,
  InputAdornment,
  Menu,
  MenuItem,
  Paper,
  Select,
  Skeleton,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { Link as RouterLink, useParams } from 'react-router-dom'
import {
  AddIcon,
  CopyIcon,
  DeleteIcon,
  DragIcon,
  EditIcon,
  ExternalLinkIcon,
  FilterIcon,
  InfoIcon,
  MoreIcon,
  PlayIcon,
  RejectedIcon,
  SearchIcon,
  SortIcon,
  SubmissionIcon,
} from '../../components/AppIcons'
import { ApiErrorNotice } from '../../components/ApiErrorNotice'
import { fetchShows, type MottoShow } from '../shows/api'
import { YoutubePlayerPanel } from '../songs/YoutubePlayerPanel'
import {
  CandidateApiError,
  clearSubmission,
  copyCandidate,
  createCandidate,
  deleteCandidate,
  fetchCandidates,
  reorderCandidates,
  selectSubmission,
  updateCandidate,
  type Candidate,
  type CandidateInput,
  type CandidateStatus,
} from './api'
import { visibleCandidates, type SortMode, type StatusFilter } from './candidateListUtils'
import { persistDroppedCandidateOrder } from './candidateReorder'

const statuses: Array<{ value: CandidateStatus, label: string }> = [
  { value: 'OFFEN', label: 'Offen' },
  { value: 'IM_RENNEN', label: 'Im Rennen' },
  { value: 'ENGERE_AUSWAHL', label: 'Engere Auswahl' },
  { value: 'FINALIST', label: 'Finalist' },
  { value: 'VERWORFEN', label: 'Verworfen' },
]

const emptyInput: CandidateInput = { artist: '', title: '', youtubeUrl: '', comment: '' }

export function CandidatePage() {
  const parsedShowId = Number(useParams().showId)
  const showId = Number.isSafeInteger(parsedShowId) && parsedShowId > 0 ? parsedShowId : null
  const [shows, setShows] = useState<MottoShow[] | null>(null)
  const [candidates, setCandidates] = useState<Candidate[] | null>(null)
  const confirmedCandidates = useRef<Candidate[]>([])
  const [error, setError] = useState<CandidateApiError | null>(null)
  const [quickInput, setQuickInput] = useState<CandidateInput>(emptyInput)
  const [quickEntryOpen, setQuickEntryOpen] = useState(false)
  const quickEntryInitializedForShow = useRef<number | null>(null)
  const [savingQuickInput, setSavingQuickInput] = useState(false)
  const [editing, setEditing] = useState<Candidate | null>(null)
  const [activeCandidate, setActiveCandidate] = useState<Candidate | null>(null)
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL')
  const [showRejected, setShowRejected] = useState(false)
  const [sortMode, setSortMode] = useState<SortMode>('MANUAL')
  const [reordering, setReordering] = useState(false)
  const [copyingCandidate, setCopyingCandidate] = useState<Candidate | null>(null)
  const [copyTargets, setCopyTargets] = useState<number[]>([])
  const [replacementCandidate, setReplacementCandidate] = useState<Candidate | null>(null)
  const [candidatePendingDeletion, setCandidatePendingDeletion] = useState<Candidate | null>(null)
  const [confirmClearSubmission, setConfirmClearSubmission] = useState(false)
  const [copyFallback, setCopyFallback] = useState<string | null>(null)

  const show = shows?.find((item) => item.id === showId) ?? null
  const displayedCandidates = useMemo(
    () => candidates === null ? [] : visibleCandidates(candidates, search, statusFilter, showRejected, sortMode),
    [candidates, search, showRejected, sortMode, statusFilter],
  )
  const hiddenRejectedCount = !showRejected ? candidates?.filter((candidate) => candidate.status === 'VERWORFEN').length ?? 0 : 0
  const dragEnabled = sortMode === 'MANUAL' && search.trim() === '' && statusFilter === 'ALL' && !reordering

  const load = useCallback(async () => {
    if (showId === null) return
    setError(null)
    try {
      const [loadedShows, loadedCandidates] = await Promise.all([fetchShows(), fetchCandidates(showId)])
      setShows(loadedShows)
      if (quickEntryInitializedForShow.current !== showId) {
        quickEntryInitializedForShow.current = showId
        setQuickEntryOpen(loadedCandidates.length === 0)
      }
      confirmedCandidates.current = loadedCandidates
      setCandidates(loadedCandidates)
    } catch (caught) {
      setError(asCandidateApiError(caught, `/api/shows/${showId}/candidates`))
      setCandidates(null)
    }
  }, [showId])

  useEffect(() => {
    // The asynchronous API boundary is intentionally initiated when the route changes.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load()
  }, [load])

  async function reloadShows() {
    try {
      setShows(await fetchShows())
    } catch (caught) {
      setError(asCandidateApiError(caught, '/api/shows'))
    }
  }

  function replaceConfirmed(candidate: Candidate) {
    confirmedCandidates.current = confirmedCandidates.current.map((item) => item.id === candidate.id ? candidate : item)
    setCandidates(confirmedCandidates.current)
    setActiveCandidate((current) => current?.id === candidate.id ? candidate : current)
  }

  async function saveQuickInput() {
    if (showId === null) return
    setSavingQuickInput(true)
    setError(null)
    try {
      const created = await createCandidate(showId, quickInput)
      confirmedCandidates.current = [...confirmedCandidates.current, created]
      setCandidates(confirmedCandidates.current)
      setQuickInput(emptyInput)
      await reloadShows()
    } catch (caught) {
      setError(asCandidateApiError(caught, `/api/shows/${showId}/candidates`))
    } finally {
      setSavingQuickInput(false)
    }
  }

  async function saveCandidate(candidate: Candidate) {
    if (showId === null) return
    setError(null)
    try {
      replaceConfirmed(await updateCandidate(showId, candidate))
      setEditing(null)
      await reloadShows()
    } catch (caught) {
      setError(asCandidateApiError(caught, `/api/shows/${showId}/candidates/${candidate.id}`))
    }
  }

  async function changeStatus(candidate: Candidate, status: CandidateStatus) {
    await saveCandidate({ ...candidate, status })
  }

  async function removeCandidate(candidate: Candidate): Promise<boolean> {
    if (showId === null) return false
    setError(null)
    try {
      await deleteCandidate(showId, candidate.id)
      confirmedCandidates.current = confirmedCandidates.current
        .filter((item) => item.id !== candidate.id)
        .map((item, index) => ({ ...item, manualPosition: index + 1 }))
      setCandidates(confirmedCandidates.current)
      setActiveCandidate((current) => current?.id === candidate.id ? null : current)
      await reloadShows()
      return true
    } catch (caught) {
      setError(asCandidateApiError(caught, `/api/shows/${showId}/candidates/${candidate.id}`))
      return false
    }
  }

  async function persistDrop(result: DropResult) {
    if (showId === null || !dragEnabled || result.destination === null || result.destination.index === result.source.index) return
    setReordering(true)
    setError(null)
    try {
      await persistDroppedCandidateOrder({
        result,
        confirmedCandidates: confirmedCandidates.current,
        visibleCandidates: displayedCandidates,
        save: (candidateIds) => reorderCandidates(showId, candidateIds),
        onOptimisticChange: setCandidates,
        onConfirmedChange: (nextCandidates) => {
          confirmedCandidates.current = nextCandidates
          setCandidates(nextCandidates)
        },
      })
    } catch (caught) {
      setCandidates(confirmedCandidates.current)
      setError(asCandidateApiError(caught, `/api/shows/${showId}/candidates/reorder`))
    } finally {
      setReordering(false)
    }
  }

  async function submitCopy() {
    if (showId === null || copyingCandidate === null) return
    setError(null)
    try {
      await copyCandidate(showId, copyingCandidate.id, copyTargets)
      setCopyingCandidate(null)
      setCopyTargets([])
      await reloadShows()
    } catch (caught) {
      setError(asCandidateApiError(caught, `/api/shows/${showId}/candidates/${copyingCandidate.id}/copy`))
    }
  }

  async function setSubmission(candidate: Candidate, confirmReplacement: boolean) {
    if (showId === null) return
    setError(null)
    try {
      await selectSubmission(showId, candidate.id, confirmReplacement)
      setReplacementCandidate(null)
      await reloadShows()
    } catch (caught) {
      setError(asCandidateApiError(caught, `/api/shows/${showId}/submission`))
    }
  }

  async function clearCurrentSubmission() {
    if (showId === null) return
    setError(null)
    try {
      await clearSubmission(showId)
      setConfirmClearSubmission(false)
      await reloadShows()
    } catch (caught) {
      setError(asCandidateApiError(caught, `/api/shows/${showId}/submission`))
    }
  }

  async function confirmCandidateDeletion() {
    if (candidatePendingDeletion !== null && await removeCandidate(candidatePendingDeletion)) {
      setCandidatePendingDeletion(null)
    }
  }

  async function copyText(value: string) {
    try {
      if (!navigator.clipboard?.writeText) throw new Error('Clipboard unavailable')
      await navigator.clipboard.writeText(value)
      setCopyFallback(null)
    } catch {
      setCopyFallback(value)
    }
  }

  if (showId === null) {
    return <Alert severity="error">Die Mottoshow-ID ist ungültig.</Alert>
  }

  const visibleCountLabel = candidates === null
    ? null
    : displayedCandidates.length === candidates.length
    ? `${candidates.length} insgesamt`
    : `${displayedCandidates.length} / ${candidates.length} sichtbar`

  return (
    <Stack spacing={3}>
      <Button component={RouterLink} sx={{ alignSelf: 'flex-start' }} to="/">Zur Übersicht</Button>
      {show === null && shows !== null && <Alert severity="error">Die Mottoshow wurde nicht gefunden.</Alert>}
      {show !== null && (
        <>
          <Box>
            <Typography color="secondary" variant="overline">Show {show.showNumber}</Typography>
            <Typography component="h1" variant="h4">{show.name}</Typography>
          </Box>
          <SubmissionHeader
            onClear={() => setConfirmClearSubmission(true)}
            onCopy={copyText}
            selectedCandidate={show.selectedCandidate}
          />
        </>
      )}
      {error !== null && <ApiErrorNotice error={error.apiError} />}
      {copyFallback !== null && (
        <Alert severity="info">
          Das Kopieren wurde vom Browser blockiert. Der Text bleibt zum manuellen Kopieren sichtbar.
          <TextField fullWidth slotProps={{ htmlInput: { readOnly: true } }} sx={{ mt: 1 }} value={copyFallback} />
        </Alert>
      )}
      <QuickEntry
        input={quickInput}
        onChange={setQuickInput}
        onSave={() => void saveQuickInput()}
        saving={savingQuickInput}
        open={quickEntryOpen}
        onToggle={() => setQuickEntryOpen((current) => !current)}
      />
      <Stack direction={{ md: 'row', xs: 'column' }} spacing={3} sx={{ alignItems: 'flex-start' }}>
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
            <Typography component="h2" variant="h5">Kandidaten</Typography>
            {visibleCountLabel !== null && <Chip label={visibleCountLabel} size="small" />}
          </Stack>
          <CandidateControls
            onSearch={setSearch}
            onShowRejected={setShowRejected}
            onSortMode={setSortMode}
            onStatusFilter={setStatusFilter}
            search={search}
            showRejected={showRejected}
            sortMode={sortMode}
            statusFilter={statusFilter}
          />
          {!dragEnabled && candidates !== null && (
            <InlineHint>
              Drag-and-drop ist nur bei manueller Reihenfolge ohne Suche oder Statusfilter aktiv.
            </InlineHint>
          )}
          {dragEnabled && hiddenRejectedCount > 0 && (
            <InlineHint>
              {hiddenRejectedCount === 1 ? 'Ein verworfener Kandidat bleibt' : `${hiddenRejectedCount} verworfene Kandidaten bleiben`} ausgeblendet und {hiddenRejectedCount === 1 ? 'wird' : 'werden'} bei der Reihenfolge vollständig berücksichtigt.
            </InlineHint>
          )}
          {candidates === null && error === null && <CandidateLoading />}
          {candidates !== null && displayedCandidates.length === 0 && (
            <Alert severity="info" sx={{ mt: 2 }}>Für diese Auswahl gibt es noch keine Kandidaten.</Alert>
          )}
          {candidates !== null && displayedCandidates.length > 0 && (
            <ManualCandidateList
              candidates={displayedCandidates}
              dragEnabled={dragEnabled}
              onChangeStatus={(candidate, status) => void changeStatus(candidate, status)}
              onCopy={(candidate) => { setCopyTargets([]); setCopyingCandidate(candidate) }}
              onDelete={setCandidatePendingDeletion}
              onDrop={(result) => void persistDrop(result)}
              onEdit={setEditing}
              onPlay={setActiveCandidate}
              onSelectSubmission={(candidate) => {
                if (show?.selectedCandidate !== null && show?.selectedCandidate.id !== candidate.id) setReplacementCandidate(candidate)
                else void setSubmission(candidate, false)
              }}
              reordering={reordering}
              activeCandidateId={activeCandidate?.id ?? null}
              selectedCandidateId={show?.selectedCandidate?.id ?? null}
            />
          )}
        </Box>
        <Paper
          component="aside"
          elevation={0}
          sx={{ backgroundColor: 'action.hover', border: 1, borderColor: 'divider', p: 2, position: { md: 'sticky' }, top: 24, width: { md: 390, xs: '100%' } }}
        >
          <YoutubePlayerPanel contextLabel="Aktuell ausgewählter Kandidat" emptyMessage="Wähle einen Kandidaten aus, um ihn hier anzuhören." song={activeCandidate} />
        </Paper>
      </Stack>
      <EditCandidateDialog candidate={editing} key={editing?.id ?? 'none'} onClose={() => setEditing(null)} onSave={(candidate) => void saveCandidate(candidate)} />
      <CopyCandidateDialog
        candidate={copyingCandidate}
        onClose={() => setCopyingCandidate(null)}
        onSave={() => void submitCopy()}
        onToggle={(targetId) => setCopyTargets((current) => current.includes(targetId) ? current.filter((id) => id !== targetId) : [...current, targetId])}
        selectedTargets={copyTargets}
        sourceShowId={showId}
        shows={shows ?? []}
      />
      <ReplacementDialog candidate={replacementCandidate} onClose={() => setReplacementCandidate(null)} onConfirm={() => replacementCandidate !== null && void setSubmission(replacementCandidate, true)} />
      <ClearSubmissionDialog onClose={() => setConfirmClearSubmission(false)} onConfirm={() => void clearCurrentSubmission()} open={confirmClearSubmission} />
      <DeleteCandidateDialog candidate={candidatePendingDeletion} onClose={() => setCandidatePendingDeletion(null)} onConfirm={() => void confirmCandidateDeletion()} />
    </Stack>
  )
}

function SubmissionHeader({ selectedCandidate, onClear, onCopy }: {
  selectedCandidate: MottoShow['selectedCandidate']
  onClear: () => void
  onCopy: (value: string) => void
}) {
  if (selectedCandidate === null) {
    return (
      <Paper aria-label="Eigene Einreichung" component="section" elevation={0} sx={{ backgroundColor: 'action.hover', border: 1, borderColor: 'divider', p: 1.5 }}>
        <Stack direction="row" spacing={1.25} sx={{ alignItems: 'center' }}>
          <Box sx={{ alignItems: 'center', borderRadius: '50%', color: 'text.secondary', display: 'inline-flex', justifyContent: 'center', p: 1 }}>
            <SubmissionIcon aria-hidden="true" />
          </Box>
          <Box>
            <Typography color="text.secondary" variant="overline">Eigene Einreichung</Typography>
            <Typography variant="body2">Noch nicht festgelegt</Typography>
          </Box>
        </Stack>
      </Paper>
    )
  }

  return (
    <Paper
      aria-label="Eigene Einreichung"
      component="section"
      elevation={0}
      sx={{ backgroundColor: 'action.hover', border: 1, borderColor: 'success.main', borderLeft: 3, p: 1.5 }}
    >
      <Stack direction={{ sm: 'row', xs: 'column' }} spacing={2} sx={{ alignItems: { sm: 'center', xs: 'stretch' }, justifyContent: 'space-between' }}>
        <Stack direction="row" spacing={1.25} sx={{ alignItems: 'center', minWidth: 0 }}>
          <Box sx={{ alignItems: 'center', borderRadius: '50%', color: 'success.main', display: 'inline-flex', flexShrink: 0, justifyContent: 'center', p: 1 }}>
            <SubmissionIcon aria-hidden="true" />
          </Box>
          <Box sx={{ minWidth: 0 }}>
            <Typography color="text.secondary" variant="overline">Eigene Einreichung</Typography>
            <Typography component="h2" sx={{ overflowWrap: 'anywhere' }} variant="h6">{selectedCandidate.title}</Typography>
            <Typography color="text.secondary" sx={{ overflowWrap: 'anywhere' }} variant="body2">{selectedCandidate.artist}</Typography>
          </Box>
        </Stack>
        <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', flexWrap: 'wrap', justifyContent: { sm: 'flex-end' } }} useFlexGap>
          <Tooltip title="Interpret und Titel kopieren">
            <IconButton aria-label="Interpret & Titel kopieren" color="primary" onClick={() => void onCopy(`${selectedCandidate.artist} – ${selectedCandidate.title}`)} size="small">
              <CopyIcon aria-hidden="true" fontSize="small" />
            </IconButton>
          </Tooltip>
          <Tooltip title="YouTube-Link kopieren">
            <IconButton aria-label="Link kopieren" color="primary" onClick={() => void onCopy(selectedCandidate.youtubeUrl)} size="small">
              <CopyIcon aria-hidden="true" fontSize="small" />
            </IconButton>
          </Tooltip>
          <Button
            component="a"
            href={selectedCandidate.youtubeUrl}
            rel="noreferrer"
            size="small"
            startIcon={<ExternalLinkIcon aria-hidden="true" fontSize="small" />}
            target="_blank"
          >
            Auf YouTube öffnen
          </Button>
          <Button color="warning" onClick={onClear} size="small">Aufheben</Button>
        </Stack>
      </Stack>
    </Paper>
  )
}

function QuickEntry({ input, onChange, onSave, saving, open, onToggle }: {
  input: CandidateInput
  onChange: (input: CandidateInput) => void
  onSave: () => void
  saving: boolean
  open: boolean
  onToggle: () => void
}) {
  return (
    <Paper
      component="section"
      elevation={0}
      sx={{ backgroundColor: open ? 'background.paper' : 'action.hover', border: 1, borderColor: 'divider', p: { xs: 1.25, md: open ? 2 : 1.25 } }}
    >
      <Stack direction="row" spacing={2} sx={{ alignItems: 'center', justifyContent: 'space-between' }}>
        <Stack direction="row" spacing={1.25} sx={{ alignItems: 'center', minWidth: 0 }}>
          <Box sx={{ color: 'secondary.main', display: 'inline-flex', flexShrink: 0 }}><AddIcon aria-hidden="true" /></Box>
          <Box sx={{ minWidth: 0 }}>
            <Typography component="h2" variant="h6">Kandidaten schnell erfassen</Typography>
            {!open && <Typography color="text.secondary" variant="body2">Songdaten direkt hinzufügen.</Typography>}
          </Box>
        </Stack>
        <Button aria-expanded={open} onClick={onToggle} startIcon={!open ? <AddIcon aria-hidden="true" fontSize="small" /> : undefined} variant={open ? 'text' : 'contained'}>
          {open ? 'Erfassung einklappen' : 'Kandidat hinzufügen'}
        </Button>
      </Stack>
      {open && <Stack spacing={1.5} sx={{ mt: 2 }}>
        <Stack direction={{ md: 'row', xs: 'column' }} spacing={1.5}>
          <TextField fullWidth label="Interpret" onChange={(event) => onChange({ ...input, artist: event.target.value })} required size="small" value={input.artist} />
          <TextField fullWidth label="Titel" onChange={(event) => onChange({ ...input, title: event.target.value })} required size="small" value={input.title} />
          <TextField fullWidth label="YouTube-Link" onChange={(event) => onChange({ ...input, youtubeUrl: event.target.value })} required size="small" value={input.youtubeUrl} />
        </Stack>
        <TextField fullWidth label="Kommentar (optional)" multiline minRows={2} onChange={(event) => onChange({ ...input, comment: event.target.value })} size="small" value={input.comment ?? ''} />
        <Box><Button disabled={saving} onClick={onSave} variant="contained">{saving ? 'Wird angelegt …' : 'Kandidat anlegen'}</Button></Box>
      </Stack>}
    </Paper>
  )
}

function CandidateControls({ search, statusFilter, showRejected, sortMode, onSearch, onStatusFilter, onShowRejected, onSortMode }: {
  search: string
  statusFilter: StatusFilter
  showRejected: boolean
  sortMode: SortMode
  onSearch: (value: string) => void
  onStatusFilter: (value: StatusFilter) => void
  onShowRejected: (value: boolean) => void
  onSortMode: (value: SortMode) => void
}) {
  return (
    <Paper component="section" elevation={0} sx={{ backgroundColor: 'action.hover', border: 1, borderColor: 'divider', mt: 1.5, p: 1 }}>
      <Stack aria-label="Kandidaten filtern und sortieren" direction={{ md: 'row', xs: 'column' }} role="toolbar" spacing={1}>
        <TextField
          label="Kandidaten suchen"
          onChange={(event) => onSearch(event.target.value)}
          placeholder="Interpret oder Titel"
          size="small"
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon aria-hidden="true" color="secondary" fontSize="small" /></InputAdornment> } }}
          sx={{ flex: 1, minWidth: { md: 220 } }}
          value={search}
        />
        <TextField
          label="Status"
          onChange={(event) => onStatusFilter(event.target.value as StatusFilter)}
          select
          size="small"
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><FilterIcon aria-hidden="true" color="secondary" fontSize="small" /></InputAdornment> } }}
          sx={{ flexShrink: 0, width: { md: 168, xs: '100%' } }}
          value={statusFilter}
        >
          <MenuItem value="ALL">Alle Status</MenuItem>
          {statuses.map((status) => <MenuItem key={status.value} value={status.value}>{status.label}</MenuItem>)}
        </TextField>
        <TextField
          label="Sortierung"
          onChange={(event) => onSortMode(event.target.value as SortMode)}
          select
          size="small"
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SortIcon aria-hidden="true" color="secondary" fontSize="small" /></InputAdornment> } }}
          sx={{ flexShrink: 0, width: { md: 220, xs: '100%' } }}
          value={sortMode}
        >
          <MenuItem value="MANUAL">Manuelle Reihenfolge</MenuItem>
          <MenuItem value="ARTIST">Interpret</MenuItem>
          <MenuItem value="TITLE">Titel</MenuItem>
          <MenuItem value="STATUS">Status</MenuItem>
          <MenuItem value="CREATED">Erfassungszeitpunkt</MenuItem>
        </TextField>
        <Button
          aria-label="Verworfene anzeigen"
          aria-pressed={showRejected}
          onClick={() => onShowRejected(!showRejected)}
          startIcon={<RejectedIcon aria-hidden="true" fontSize="small" />}
          sx={{ flexShrink: 0, minWidth: { md: 132 } }}
          variant={showRejected ? 'contained' : 'outlined'}
        >
          Verworfene
        </Button>
      </Stack>
    </Paper>
  )
}

function InlineHint({ children }: { children: ReactNode }) {
  return (
    <Stack direction="row" role="note" spacing={0.75} sx={{ alignItems: 'center', color: 'text.secondary', mt: 1.25, px: 0.5 }}>
      <InfoIcon aria-hidden="true" fontSize="small" />
      <Typography color="inherit" variant="caption">{children}</Typography>
    </Stack>
  )
}

function ManualCandidateList({ candidates, dragEnabled, reordering, selectedCandidateId, activeCandidateId, onDrop, onChangeStatus, onPlay, onEdit, onCopy, onDelete, onSelectSubmission }: {
  candidates: Candidate[]
  dragEnabled: boolean
  reordering: boolean
  selectedCandidateId: number | null
  activeCandidateId: number | null
  onDrop: (result: DropResult) => void
  onChangeStatus: (candidate: Candidate, status: CandidateStatus) => void
  onPlay: (candidate: Candidate) => void
  onEdit: (candidate: Candidate) => void
  onCopy: (candidate: Candidate) => void
  onDelete: (candidate: Candidate) => void
  onSelectSubmission: (candidate: Candidate) => void
}) {
  return (
    <DragDropContext onDragEnd={onDrop}>
      <Droppable droppableId="candidate-list" isDropDisabled={!dragEnabled}>
        {(provided, snapshot) => (
          <Box
            {...provided.droppableProps}
            aria-label="Manuelle Kandidatenreihenfolge"
            ref={provided.innerRef}
            sx={{ mt: 1.25 }}
          >
            {snapshot.isDraggingOver && <Alert aria-live="polite" severity="info" sx={{ mb: 1, pointerEvents: 'none' }}>Loslassen, um den Kandidaten an dieser Position einzufügen.</Alert>}
            <Stack spacing={0.75}>
              {candidates.map((candidate, index) => (
                <Draggable draggableId={String(candidate.id)} index={index} isDragDisabled={!dragEnabled} key={candidate.id}>
                  {(dragProvided, dragSnapshot) => {
                    const selected = candidate.id === selectedCandidateId
                    const active = candidate.id === activeCandidateId
                    return (
                      <Card
                        {...dragProvided.draggableProps}
                        elevation={dragSnapshot.isDragging ? 8 : 0}
                        ref={dragProvided.innerRef}
                        sx={{
                          border: 1,
                          borderColor: selected ? 'success.main' : dragSnapshot.isDragging ? 'secondary.main' : 'divider',
                          bgcolor: active ? 'action.selected' : 'background.paper',
                          opacity: candidate.status === 'VERWORFEN' ? 0.72 : 1,
                          outline: dragSnapshot.isDragging ? '2px solid' : 'none',
                          outlineColor: 'secondary.main',
                          transition: 'border-color 120ms ease, background-color 120ms ease, box-shadow 120ms ease',
                        }}
                      >
                        <CardContent sx={{ p: { xs: 1, md: 1.25 }, '&:last-child': { pb: { xs: 1, md: 1.25 } } }}>
                          <Stack direction={{ md: 'row', xs: 'column' }} spacing={1} sx={{ alignItems: { md: 'center', xs: 'stretch' } }}>
                            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flex: 1, minWidth: 0 }}>
                              <Tooltip title={dragEnabled ? 'Ziehen, um die Reihenfolge zu ändern' : 'Drag-and-drop ist in dieser Ansicht nicht verfügbar'}>
                                <Box
                                  {...dragProvided.dragHandleProps}
                                  aria-disabled={!dragEnabled}
                                  aria-label={`${candidate.artist} verschieben`}
                                  sx={{
                                    alignItems: 'center',
                                    borderRadius: 1,
                                    color: dragEnabled ? 'text.secondary' : 'action.disabled',
                                    cursor: dragSnapshot.isDragging ? 'grabbing' : dragEnabled ? 'grab' : 'default',
                                    display: 'inline-flex',
                                    flexShrink: 0,
                                    justifyContent: 'center',
                                    minHeight: 40,
                                    minWidth: 40,
                                  }}
                                >
                                  <DragIcon aria-hidden="true" />
                                </Box>
                              </Tooltip>
                              <Box sx={{ flex: 1, minWidth: 0 }}>
                                <Typography component="h3" sx={{ fontWeight: 650, overflowWrap: 'anywhere' }} variant="subtitle1">{candidate.title}</Typography>
                                <Typography color="text.secondary" sx={{ overflowWrap: 'anywhere' }} variant="body2">{candidate.artist}</Typography>
                                {(active || candidate.status === 'VERWORFEN') && (
                                  <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', mt: 0.5 }} useFlexGap>
                                    {active && <Chip color="secondary" icon={<PlayIcon aria-hidden="true" />} label="Wird angehört" size="small" />}
                                    {candidate.status === 'VERWORFEN' && <Chip icon={<RejectedIcon aria-hidden="true" />} label="Verworfen" size="small" />}
                                  </Stack>
                                )}
                                {candidate.comment !== null && candidate.comment.trim() !== '' && (
                                  <Typography color="text.secondary" sx={{ mt: 0.5, overflowWrap: 'anywhere' }} variant="body2">{candidate.comment}</Typography>
                                )}
                              </Box>
                            </Stack>
                            <Stack
                              direction="row"
                              spacing={0.25}
                              sx={{
                                alignItems: 'center',
                                flexShrink: 0,
                                flexWrap: { md: 'nowrap', xs: 'wrap' },
                                justifyContent: 'flex-end',
                                minHeight: 40,
                                width: { md: 424, xs: '100%' },
                              }}
                            >
                              <Box sx={{ alignItems: 'center', display: 'flex', flexShrink: 0, justifyContent: 'flex-end', minHeight: 32, pr: { md: 1.25, xs: 0.5 }, width: { md: 116, xs: 'auto' } }}>
                                {selected && (
                                  <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', color: 'success.main', whiteSpace: 'nowrap' }}>
                                    <SubmissionIcon aria-hidden="true" sx={{ fontSize: '1rem' }} />
                                    <Typography color="inherit" sx={{ fontSize: '0.8rem', fontWeight: 700, lineHeight: 1.2 }} variant="body2">
                                      Einreichung
                                    </Typography>
                                  </Stack>
                                )}
                              </Box>
                              <Select
                                aria-label={`Status von ${candidate.artist}`}
                                onChange={(event) => onChangeStatus(candidate, event.target.value as CandidateStatus)}
                                size="small"
                                sx={{ flexShrink: 0, width: 164, '& .MuiSelect-select': { whiteSpace: 'nowrap' } }}
                                value={candidate.status}
                              >
                                {statuses.map((status) => <MenuItem key={status.value} value={status.value}>{status.label}</MenuItem>)}
                              </Select>
                              <Tooltip title="Anhören">
                                <IconButton
                                  aria-label={`${candidate.artist} – ${candidate.title} anhören`}
                                  color={active ? 'secondary' : 'primary'}
                                  onClick={() => onPlay(candidate)}
                                  size="small"
                                >
                                  <PlayIcon aria-hidden="true" fontSize="small" />
                                </IconButton>
                              </Tooltip>
                              <Tooltip title={selected ? 'Bereits als Einreichung gewählt' : 'Als Einreichung wählen'}>
                                <Box component="span" sx={{ display: 'inline-flex' }}>
                                  <IconButton
                                    aria-label={selected ? 'Bereits als Einreichung gewählt' : 'Als Einreichung wählen'}
                                    color="primary"
                                    disabled={selected}
                                    onClick={() => onSelectSubmission(candidate)}
                                    size="small"
                                  >
                                    <SubmissionIcon aria-hidden="true" fontSize="small" />
                                  </IconButton>
                                </Box>
                              </Tooltip>
                              <CandidateOverflowMenu
                                candidate={candidate}
                                onCopy={() => onCopy(candidate)}
                                onDelete={() => onDelete(candidate)}
                                onEdit={() => onEdit(candidate)}
                                selected={selected}
                              />
                            </Stack>
                          </Stack>
                        </CardContent>
                      </Card>
                    )
                  }}
                </Draggable>
              ))}
              {provided.placeholder}
              {reordering && <Typography color="text.secondary" variant="body2">Reihenfolge wird gespeichert …</Typography>}
            </Stack>
          </Box>
        )}
      </Droppable>
    </DragDropContext>
  )
}

function CandidateOverflowMenu({ candidate, selected, onEdit, onCopy, onDelete }: {
  candidate: Candidate
  selected: boolean
  onEdit: () => void
  onCopy: () => void
  onDelete: () => void
}) {
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null)
  const open = anchorEl !== null

  function run(action: () => void) {
    setAnchorEl(null)
    action()
  }

  return (
    <>
      <Tooltip title="Weitere Aktionen">
        <IconButton
          aria-controls={open ? `candidate-${candidate.id}-actions` : undefined}
          aria-expanded={open ? 'true' : undefined}
          aria-haspopup="menu"
          aria-label={`Weitere Aktionen für ${candidate.artist} – ${candidate.title}`}
          color="primary"
          onClick={(event) => setAnchorEl(event.currentTarget)}
          size="small"
        >
          <MoreIcon aria-hidden="true" fontSize="small" />
        </IconButton>
      </Tooltip>
      <Menu anchorEl={anchorEl} id={`candidate-${candidate.id}-actions`} onClose={() => setAnchorEl(null)} open={open}>
        <MenuItem onClick={() => run(onEdit)}>
          <EditIcon aria-hidden="true" fontSize="small" sx={{ mr: 1 }} />
          Bearbeiten
        </MenuItem>
        <MenuItem onClick={() => run(onCopy)}>
          <CopyIcon aria-hidden="true" fontSize="small" sx={{ mr: 1 }} />
          In andere Show kopieren
        </MenuItem>
        <MenuItem
          disabled={selected}
          onClick={() => run(onDelete)}
          sx={{ color: selected ? 'text.disabled' : 'error.main' }}
          title={selected ? 'Einreichung zuerst aufheben oder ersetzen' : undefined}
        >
          <DeleteIcon aria-hidden="true" fontSize="small" sx={{ mr: 1 }} />
          Löschen
        </MenuItem>
      </Menu>
    </>
  )
}

function EditCandidateDialog({ candidate, onClose, onSave }: {
  candidate: Candidate | null
  onClose: () => void
  onSave: (candidate: Candidate) => void
}) {
  const [draft, setDraft] = useState<Candidate | null>(null)
  const [initialCandidate] = useState(candidate)
  const initialDraft = draft ?? initialCandidate
  if (candidate === null || initialDraft === null) return null
  return (
    <Dialog fullWidth maxWidth="sm" onClose={onClose} open>
      <DialogTitle>Kandidat bearbeiten</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <TextField fullWidth label="Interpret" onChange={(event) => setDraft({ ...initialDraft, artist: event.target.value })} value={initialDraft.artist} />
          <TextField fullWidth label="Titel" onChange={(event) => setDraft({ ...initialDraft, title: event.target.value })} value={initialDraft.title} />
          <TextField fullWidth label="YouTube-Link" onChange={(event) => setDraft({ ...initialDraft, youtubeUrl: event.target.value })} value={initialDraft.youtubeUrl} />
          <TextField fullWidth label="Kommentar" multiline minRows={3} onChange={(event) => setDraft({ ...initialDraft, comment: event.target.value })} value={initialDraft.comment ?? ''} />
          <TextField fullWidth label="Status" onChange={(event) => setDraft({ ...initialDraft, status: event.target.value as CandidateStatus })} select value={initialDraft.status}>
            {statuses.map((status) => <MenuItem key={status.value} value={status.value}>{status.label}</MenuItem>)}
          </TextField>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Abbrechen</Button>
        <Button onClick={() => onSave(initialDraft)} variant="contained">Speichern</Button>
      </DialogActions>
    </Dialog>
  )
}

function CopyCandidateDialog({ candidate, sourceShowId, shows, selectedTargets, onToggle, onClose, onSave }: {
  candidate: Candidate | null
  sourceShowId: number
  shows: MottoShow[]
  selectedTargets: number[]
  onToggle: (showId: number) => void
  onClose: () => void
  onSave: () => void
}) {
  return (
    <Dialog fullWidth maxWidth="sm" onClose={onClose} open={candidate !== null}>
      <DialogTitle>In andere Mottoshow kopieren</DialogTitle>
      <DialogContent>
        <Typography color="text.secondary">{candidate === null ? '' : `${candidate.artist} – ${candidate.title}`}</Typography>
        <Stack divider={<Divider flexItem />} sx={{ mt: 2 }}>
          {shows.filter((show) => show.id !== sourceShowId).map((show) => (
            <FormControlLabel
              control={<Checkbox checked={selectedTargets.includes(show.id)} onChange={() => onToggle(show.id)} />}
              key={show.id}
              label={`Show ${show.showNumber}: ${show.name}`}
            />
          ))}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Abbrechen</Button>
        <Button disabled={selectedTargets.length === 0} onClick={onSave} variant="contained">Kopieren</Button>
      </DialogActions>
    </Dialog>
  )
}

function ReplacementDialog({ candidate, onClose, onConfirm }: { candidate: Candidate | null, onClose: () => void, onConfirm: () => void }) {
  return (
    <Dialog onClose={onClose} open={candidate !== null}>
      <DialogTitle>Einreichung bewusst ersetzen?</DialogTitle>
      <DialogContent>
        <Typography>Die bestehende Einreichung wird durch {candidate === null ? '' : `${candidate.artist} – ${candidate.title}`} ersetzt.</Typography>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Abbrechen</Button>
        <Button color="warning" onClick={onConfirm} variant="contained">Einreichung ersetzen</Button>
      </DialogActions>
    </Dialog>
  )
}

function ClearSubmissionDialog({ open, onClose, onConfirm }: { open: boolean, onClose: () => void, onConfirm: () => void }) {
  return (
    <Dialog onClose={onClose} open={open}>
      <DialogTitle>Einreichung aufheben?</DialogTitle>
      <DialogContent><Typography>Die eigene Einreichung wird bewusst aufgehoben.</Typography></DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Abbrechen</Button>
        <Button color="warning" onClick={onConfirm} variant="contained">Einreichung aufheben</Button>
      </DialogActions>
    </Dialog>
  )
}

function DeleteCandidateDialog({ candidate, onClose, onConfirm }: { candidate: Candidate | null, onClose: () => void, onConfirm: () => void }) {
  return (
    <Dialog onClose={onClose} open={candidate !== null}>
      <DialogTitle>Kandidat löschen?</DialogTitle>
      <DialogContent>
        <Typography>{candidate === null ? '' : `${candidate.artist} – ${candidate.title}`} wird dauerhaft aus dieser Mottoshow entfernt.</Typography>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Abbrechen</Button>
        <Button color="error" onClick={onConfirm} variant="contained">Kandidat löschen</Button>
      </DialogActions>
    </Dialog>
  )
}

function CandidateLoading() {
  return <Stack spacing={1} sx={{ mt: 1.5 }}>{Array.from({ length: 3 }, (_, index) => <Skeleton height={76} key={index} variant="rounded" />)}</Stack>
}

function asCandidateApiError(error: unknown, path: string): CandidateApiError {
  if (error instanceof CandidateApiError) return error
  return new CandidateApiError({
    timestamp: new Date().toISOString(),
    status: 0,
    code: 'NETWORK_ERROR',
    message: 'Die Kandidatendaten konnten nicht verarbeitet werden.',
    path,
  })
}