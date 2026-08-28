import { DragDropContext, Draggable, Droppable, type DropResult } from '@hello-pangea/dnd'
import {
  Alert,
  Box,
  Button,
  Card,
  CardActions,
  CardContent,
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControlLabel,
  Link,
  MenuItem,
  Paper,
  Select,
  Skeleton,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link as RouterLink, useParams } from 'react-router-dom'
import { ApiErrorNotice } from '../../components/ApiErrorNotice'
import { fetchShows, type MottoShow } from '../shows/api'
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
import { YoutubePlayerPanel } from '../songs/YoutubePlayerPanel'
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
          <Typography component="h2" variant="h5">Kandidaten</Typography>
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
            <Alert severity="info" sx={{ mt: 2 }}>
              Drag-and-drop ist nur bei manueller Reihenfolge ohne Suche oder Statusfilter aktiv.
            </Alert>
          )}
          {dragEnabled && hiddenRejectedCount > 0 && (
            <Typography color="text.secondary" sx={{ mt: 2 }} variant="body2">
              {hiddenRejectedCount === 1 ? 'Ein verworfener Kandidat bleibt' : `${hiddenRejectedCount} verworfene Kandidaten bleiben`} ausgeblendet und {hiddenRejectedCount === 1 ? 'wird' : 'werden'} bei der Reihenfolge vollständig berücksichtigt.
            </Typography>
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
        <Paper component="aside" elevation={0} sx={{ border: 1, borderColor: 'divider', p: 2, position: { md: 'sticky' }, top: 24, width: { md: 390, xs: '100%' } }}>
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
    return <Alert severity="info">Eigene Einreichung: noch nicht festgelegt.</Alert>
  }
  return (
    <Alert severity="success" action={<Button color="inherit" onClick={onClear} size="small">Aufheben</Button>}>
      <Stack spacing={0.5}>
        <Typography><strong>Eigene Einreichung:</strong> {selectedCandidate.artist} – {selectedCandidate.title}</Typography>
        <Link href={selectedCandidate.youtubeUrl} rel="noreferrer" sx={{ overflowWrap: 'anywhere' }} target="_blank">
          {selectedCandidate.youtubeUrl}
        </Link>
        <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap' }} useFlexGap>
          <Button onClick={() => void onCopy(`${selectedCandidate.artist} – ${selectedCandidate.title}`)} size="small">Interpret &amp; Titel kopieren</Button>
          <Button onClick={() => void onCopy(selectedCandidate.youtubeUrl)} size="small">Link kopieren</Button>
        </Stack>
      </Stack>
    </Alert>
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
    <Paper component="section" elevation={0} sx={{ border: 1, borderColor: 'divider', p: { xs: 1.5, md: 2 } }}>
      <Stack direction="row" spacing={2} sx={{ alignItems: 'center', justifyContent: 'space-between' }}>
        <Box>
          <Typography component="h2" variant="h6">Kandidaten schnell erfassen</Typography>
          {!open && <Typography color="text.secondary" variant="body2">Interpret, Titel und YouTube-Link direkt hinzufügen.</Typography>}
        </Box>
        <Button aria-expanded={open} onClick={onToggle} variant={open ? 'text' : 'contained'}>
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
    <Paper component="section" elevation={0} sx={{ border: 1, borderColor: 'divider', mt: 1.5, p: 1.25 }}>
      <Stack aria-label="Kandidaten filtern und sortieren" direction={{ md: 'row', xs: 'column' }} role="toolbar" spacing={1.25}>
      <TextField label="Interpret oder Titel suchen" onChange={(event) => onSearch(event.target.value)} size="small" sx={{ flex: 1, minWidth: { md: 220 } }} value={search} />
      <TextField label="Status" onChange={(event) => onStatusFilter(event.target.value as StatusFilter)} select size="small" value={statusFilter}>
        <MenuItem value="ALL">Alle Status</MenuItem>
        {statuses.map((status) => <MenuItem key={status.value} value={status.value}>{status.label}</MenuItem>)}
      </TextField>
      <TextField label="Darstellung sortieren" onChange={(event) => onSortMode(event.target.value as SortMode)} select size="small" value={sortMode}>
        <MenuItem value="MANUAL">Manuelle Reihenfolge</MenuItem>
        <MenuItem value="ARTIST">Interpret</MenuItem>
        <MenuItem value="TITLE">Titel</MenuItem>
        <MenuItem value="STATUS">Status</MenuItem>
        <MenuItem value="CREATED">Erfassungszeitpunkt</MenuItem>
      </TextField>
      <FormControlLabel control={<Checkbox checked={showRejected} onChange={(event) => onShowRejected(event.target.checked)} />} label="Verworfene anzeigen" sx={{ ml: { md: 0 } }} />
      </Stack>
    </Paper>
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
            sx={{ mt: 1.5 }}
          >
            {snapshot.isDraggingOver && <Alert aria-live="polite" severity="info" sx={{ mb: 1, pointerEvents: 'none' }}>Loslassen, um den Kandidaten an dieser Position einzufügen.</Alert>}
            <Stack spacing={0.75}>
              {candidates.map((candidate, index) => (
                <Draggable draggableId={String(candidate.id)} index={index} isDragDisabled={!dragEnabled} key={candidate.id}>
                  {(dragProvided, dragSnapshot) => (
                    <Card
                      {...dragProvided.draggableProps}
                      elevation={dragSnapshot.isDragging ? 8 : 0}
                      ref={dragProvided.innerRef}
                      sx={{
                        border: 1,
                        borderColor: candidate.id === selectedCandidateId ? 'success.main' : dragSnapshot.isDragging ? 'secondary.main' : 'divider',
                        bgcolor: candidate.id === activeCandidateId ? 'action.selected' : 'background.paper',
                        opacity: candidate.status === 'VERWORFEN' ? 0.72 : 1,
                        outline: dragSnapshot.isDragging ? '2px solid' : 'none',
                        outlineColor: 'secondary.main',
                        transition: 'border-color 120ms ease, background-color 120ms ease, box-shadow 120ms ease',
                      }}
                    >
                      <CardContent sx={{ pb: 0.75, pt: 1.25, px: { xs: 1.25, md: 1.5 } }}>
                        <Stack direction="row" spacing={1} sx={{ alignItems: 'flex-start' }}>
                          <Box
                            {...dragProvided.dragHandleProps}
                            aria-disabled={!dragEnabled}
                            aria-label={`${candidate.artist} verschieben`}
                            sx={{
                              alignItems: 'center',
                              borderRadius: 1,
                              color: dragEnabled ? 'inherit' : 'action.disabled',
                              cursor: dragSnapshot.isDragging ? 'grabbing' : dragEnabled ? 'grab' : 'default',
                              display: 'inline-flex',
                              fontSize: 20,
                              justifyContent: 'center',
                              minHeight: 32,
                              minWidth: 40,
                              mt: 0.25,
                              px: 0.5,
                            }}
                          >⋮⋮</Box>
                          <Box sx={{ flex: 1, minWidth: 0 }}>
                            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }} useFlexGap>
                              <Typography component="h3" variant="subtitle1">{candidate.artist} – {candidate.title}</Typography>
                              {candidate.id === selectedCandidateId && <Chip color="success" label="Einreichung" size="small" />}
                              {candidate.id === activeCandidateId && <Chip color="secondary" label="Wird angehört" size="small" />}
                              {candidate.status === 'VERWORFEN' && <Chip label="Verworfen" size="small" />}
                            </Stack>
                            {candidate.comment !== null && <Typography color="text.secondary" sx={{ mt: 0.5 }} variant="body2">{candidate.comment}</Typography>}
                          </Box>
                          <Select
                            aria-label={`Status von ${candidate.artist}`}
                            onChange={(event) => onChangeStatus(candidate, event.target.value as CandidateStatus)}
                            size="small"
                            value={candidate.status}
                          >
                            {statuses.map((status) => <MenuItem key={status.value} value={status.value}>{status.label}</MenuItem>)}
                          </Select>
                        </Stack>
                      </CardContent>
                      <CardActions sx={{ justifyContent: 'space-between', pl: 6.25, pr: 1.25, pt: 0, pb: 0.75 }}>
                        <Button onClick={() => onPlay(candidate)} size="small">Anhören</Button>
                        <Button onClick={() => onEdit(candidate)} size="small">Bearbeiten</Button>
                        <Button onClick={() => onCopy(candidate)} size="small">In andere Show kopieren</Button>
                        {candidate.id !== selectedCandidateId && <Button onClick={() => onSelectSubmission(candidate)} size="small">Als Einreichung wählen</Button>}
                        <Button
                          color="error"
                          disabled={candidate.id === selectedCandidateId}
                          onClick={() => onDelete(candidate)}
                          size="small"
                          title={candidate.id === selectedCandidateId ? 'Einreichung zuerst aufheben oder ersetzen' : undefined}
                        >Löschen</Button>
                      </CardActions>
                    </Card>
                  )}
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
  return <Stack spacing={1} sx={{ mt: 2 }}>{Array.from({ length: 3 }, (_, index) => <Skeleton height={120} key={index} variant="rounded" />)}</Stack>
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
