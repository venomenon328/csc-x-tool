import { DragDropContext, Draggable, Droppable, type DraggableProvided, type DraggableProvidedDragHandleProps, type DropResult } from '@hello-pangea/dnd'
import {
  Alert, Box, Button, Card, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogTitle,
  IconButton, InputAdornment, Menu, MenuItem, Paper, Select, Skeleton, Stack, TextField, Tooltip, Typography,
} from '@mui/material'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { Link as RouterLink, useParams } from 'react-router-dom'
import { ApiErrorNotice } from '../../components/ApiErrorNotice'
import {
  DeleteIcon, DragIcon, EditIcon, FilterIcon, MoreIcon, PlayIcon, RankIcon, ReplayIcon, SearchIcon,
  SortIcon, SpeakerCheckIcon, SpeakerCrossIcon,
} from '../../components/AppIcons'
import { BallotPanel } from '../ballot/BallotPanel'
import { BallotApiError, closeBallot, fetchBallot, reopenBallot, reorderBallot, type Ballot } from '../ballot/api'
import {
  ENTRY_POOL_DROPPABLE_ID, RANKING_DROPPABLE_ID, applyConfirmedRanking, ballotRankingPayload,
  persistDroppedBallot, removeFromRanking,
} from '../ballot/ballotReorder'
import { fetchParticipants, type Participant } from '../participants/api'
import { ParticipantSelect } from '../participants/ParticipantSelect'
import { YoutubePlayerPanel } from '../songs/YoutubePlayerPanel'
import { fetchShows, type MottoShow } from '../shows/api'
import { ClipboardImportArea } from './ClipboardImportArea'
import { ImportPreview, type EditableImportLine } from './ImportPreview'
import {
  createEntry, deleteEntry, EntryApiError, fetchEntries, importEntries, previewImport, reorderEntryPool,
  updateParticipantAssignment, updateEntry, type ContestEntry, type ContestEntryInput,
} from './api'
import {
  persistDroppedPoolOrder, visiblePoolEntries, type EntryPoolFilters, type EntryPoolSortMode,
} from './entryPoolOrder'

const emptyEntryInput: ContestEntryInput = { artist: '', title: '', youtubeUrl: '', comment: '' }

export function EntryPage() {
  const parsedShowId = Number(useParams().showId)
  const showId = Number.isSafeInteger(parsedShowId) && parsedShowId > 0 ? parsedShowId : null
  const [shows, setShows] = useState<MottoShow[] | null>(null)
  const [entries, setEntries] = useState<ContestEntry[] | null>(null)
  const [ballot, setBallot] = useState<Ballot | null>(null)
  const [participants, setParticipants] = useState<Participant[] | null>(null)
  const [error, setError] = useState<EntryApiError | null>(null)
  const [activeEntryId, setActiveEntryId] = useState<number | null>(null)
  const [search, setSearch] = useState('')
  const [onlyUnlistened, setOnlyUnlistened] = useState(false)
  const [onlyRelisten, setOnlyRelisten] = useState(false)
  const [onlyUnranked, setOnlyUnranked] = useState(false)
  const [onlyWithoutParticipant, setOnlyWithoutParticipant] = useState(false)
  const [sortMode, setSortMode] = useState<EntryPoolSortMode>('MANUAL')
  const [previewLines, setPreviewLines] = useState<EditableImportLine[] | null>(null)
  const [importing, setImporting] = useState(false)
  const [editing, setEditing] = useState<ContestEntry | null>(null)
  const [creating, setCreating] = useState(false)
  const [savingNewEntry, setSavingNewEntry] = useState(false)
  const [entryPendingDeletion, setEntryPendingDeletion] = useState<ContestEntry | null>(null)
  const [poolReordering, setPoolReordering] = useState(false)
  const [rankingReordering, setRankingReordering] = useState(false)

  const show = shows?.find((item) => item.id === showId) ?? null
  const participantAssignmentOpen = ballot?.ballotClosedAt !== null && ballot?.ballotClosedAt !== undefined
  const filters = useMemo<EntryPoolFilters>(
    () => ({ search, onlyUnlistened, onlyRelisten, onlyUnranked, onlyWithoutParticipant }),
    [onlyRelisten, onlyUnlistened, onlyUnranked, onlyWithoutParticipant, search],
  )
  const visibleEntries = useMemo(
    () => visiblePoolEntries(entries ?? [], filters, sortMode),
    [entries, filters, sortMode],
  )
  const activeEntry = activeEntryId === null ? null : entries?.find((entry) => entry.id === activeEntryId) ?? null
  const activeIndex = activeEntry === null ? -1 : visibleEntries.findIndex((entry) => entry.id === activeEntry.id)
  const poolFiltersActive = search.trim() !== '' || onlyUnlistened || onlyRelisten || onlyUnranked || onlyWithoutParticipant
  const dndBusy = poolReordering || rankingReordering
  const poolReorderEnabled = sortMode === 'MANUAL' && !poolFiltersActive && !dndBusy
  const visibleCountLabel = entries === null ? null : poolFiltersActive ? `${visibleEntries.length} / ${entries.length} sichtbar` : `${entries.length} insgesamt`

  const load = useCallback(async () => {
    if (showId === null) return
    setError(null)
    try {
      const [loadedShows, loadedEntries, loadedBallot] = await Promise.all([fetchShows(), fetchEntries(showId), fetchBallot(showId)])
      setShows(loadedShows)
      setEntries(loadedEntries)
      setBallot(loadedBallot)
      setParticipants(loadedBallot.ballotClosedAt === null ? null : await fetchParticipants({ includeInactive: true }))
    } catch (caught) {
      setEntries(null)
      setBallot(null)
      setParticipants(null)
      setError(asEntryApiError(caught, `/api/shows/${showId}/entries`))
    }
  }, [showId])

  useEffect(() => {
    // Route changes deliberately create a new asynchronous API boundary.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load()
  }, [load])

  async function reloadShows() {
    try { setShows(await fetchShows()) } catch (caught) { setError(asEntryApiError(caught, '/api/shows')) }
  }

  async function saveEntry(entry: ContestEntry): Promise<boolean> {
    if (showId === null) return false
    setError(null)
    try {
      const updated = await updateEntry(showId, entry)
      setEntries((current) => current?.map((item) => item.id === updated.id ? updated : item) ?? null)
      setEditing(null)
      void reloadShows()
      return true
    } catch (caught) {
      setError(asEntryApiError(caught, `/api/shows/${showId}/entries/${entry.id}`))
      return false
    }
  }

  async function saveNewEntry(input: ContestEntryInput) {
    if (showId === null) return
    setError(null)
    setSavingNewEntry(true)
    try {
      const created = await createEntry(showId, input)
      setEntries((current) => current === null ? [created] : [...current, created].sort((left, right) => left.poolPosition - right.poolPosition))
      setCreating(false)
      setActiveEntryId(created.id)
      void reloadShows()
    } catch (caught) {
      setError(asEntryApiError(caught, `/api/shows/${showId}/entries`))
    } finally { setSavingNewEntry(false) }
  }

  async function removeEntry(entry: ContestEntry) {
    if (showId === null) return
    setError(null)
    try {
      await deleteEntry(showId, entry.id)
      setEntries(await fetchEntries(showId))
      setActiveEntryId((current) => current === entry.id ? null : current)
      setEntryPendingDeletion(null)
      void reloadShows()
    } catch (caught) { setError(asEntryApiError(caught, `/api/shows/${showId}/entries/${entry.id}`)) }
  }

  async function pasteForPreview(html: string, text: string) {
    if (showId === null) return
    setError(null)
    try { setPreviewLines((await previewImport(showId, html, text)).map((line) => ({ ...line, included: true }))) }
    catch (caught) { setError(asEntryApiError(caught, `/api/shows/${showId}/entries/import-preview`)) }
  }

  async function confirmImport() {
    if (showId === null || previewLines === null) return
    setError(null)
    setImporting(true)
    try {
      setEntries(await importEntries(showId, previewLines.filter((line) => line.included).map((line) => ({
        artist: line.artist ?? '', title: line.title ?? '', youtubeUrl: line.youtubeUrl ?? '', comment: null,
      }))))
      setPreviewLines(null)
      void reloadShows()
    } catch (caught) { setError(asEntryApiError(caught, `/api/shows/${showId}/entries/import`)) }
    finally { setImporting(false) }
  }

  async function persistPoolDrop(result: DropResult) {
    if (showId === null || entries === null || !poolReorderEnabled) return
    const confirmed = [...entries].sort((left, right) => left.poolPosition - right.poolPosition)
    setError(null)
    setPoolReordering(true)
    try {
      await persistDroppedPoolOrder({
        result,
        confirmedEntries: confirmed,
        save: (entryIds) => reorderEntryPool(showId, entryIds),
        onOptimisticChange: setEntries,
        onConfirmedChange: setEntries,
      })
    } catch (caught) { setError(asEntryApiError(caught, `/api/shows/${showId}/entries/reorder`)) }
    finally { setPoolReordering(false) }
  }

  async function persistRankingDrop(result: DropResult) {
    if (showId === null || entries === null || ballot?.ballotClosedAt !== null) return
    const confirmed = entries
    setError(null)
    setRankingReordering(true)
    try {
      await persistDroppedBallot({
        result,
        confirmedEntries: confirmed,
        save: (ranking) => reorderBallot(showId, ranking),
        onOptimisticChange: setEntries,
        onConfirmedChange: setEntries,
      })
    } catch (caught) { setError(asEntryApiError(caught, `/api/shows/${showId}/ballot/reorder`)) }
    finally { setRankingReordering(false) }
  }

  async function onDragEnd(result: DropResult) {
    if (result.destination === null || dndBusy) return
    if (result.source.droppableId === ENTRY_POOL_DROPPABLE_ID && result.destination.droppableId === ENTRY_POOL_DROPPABLE_ID) {
      await persistPoolDrop(result)
      return
    }
    if (
      (result.source.droppableId === ENTRY_POOL_DROPPABLE_ID || result.source.droppableId === RANKING_DROPPABLE_ID)
      && (result.destination.droppableId === ENTRY_POOL_DROPPABLE_ID || result.destination.droppableId === RANKING_DROPPABLE_ID)
    ) await persistRankingDrop(result)
  }

  async function removeRanking(entry: ContestEntry) {
    if (showId === null || entries === null || ballot?.ballotClosedAt !== null || dndBusy) return
    const confirmed = entries
    const optimistic = removeFromRanking(confirmed, entry.id)
    setError(null)
    setRankingReordering(true)
    setEntries(optimistic)
    try {
      const ranking = await reorderBallot(showId, ballotRankingPayload(optimistic))
      setEntries(applyConfirmedRanking(confirmed, ranking))
    } catch (caught) {
      setEntries(confirmed)
      setError(asEntryApiError(caught, `/api/shows/${showId}/ballot/reorder`))
    } finally { setRankingReordering(false) }
  }

  async function closeRanking() {
    if (showId === null) return
    setError(null)
    try {
      const closedBallot = await closeBallot(showId)
      setBallot(closedBallot)
      setParticipants(await fetchParticipants({ includeInactive: true }))
      void reloadShows()
    } catch (caught) { setError(asEntryApiError(caught, `/api/shows/${showId}/ballot/close`)) }
  }

  async function reopenRanking() {
    if (showId === null) return
    setError(null)
    try {
      setBallot(await reopenBallot(showId))
      setParticipants(null)
      void reloadShows()
    } catch (caught) { setError(asEntryApiError(caught, `/api/shows/${showId}/ballot/reopen`)) }
  }

  async function saveParticipantAssignment(entry: ContestEntry, participant: Participant | null) {
    if (showId === null) return
    setError(null)
    try {
      const updated = await updateParticipantAssignment(showId, entry.id, participant?.id ?? null)
      setEntries((current) => current?.map((item) => item.id === updated.id ? updated : item) ?? null)
      setBallot(await fetchBallot(showId))
      void reloadShows()
    } catch (caught) { setError(asEntryApiError(caught, `/api/shows/${showId}/entries/${entry.id}/participant`)) }
  }

  if (showId === null) return <Alert severity="error">Die Mottoshow-ID ist ungültig.</Alert>

  return (
    <Stack spacing={3}>
      <Button component={RouterLink} sx={{ alignSelf: 'flex-start' }} to="/">Zur Übersicht</Button>
      {show === null && shows !== null && <Alert severity="error">Die Mottoshow wurde nicht gefunden.</Alert>}
      {show !== null && <Box>
        <Typography color="secondary" variant="overline">Show {show.showNumber}</Typography>
        <Typography component="h1" variant="h4">{show.name} – Abstimmung</Typography>
        <Typography color="text.secondary" sx={{ mt: 1 }}>Wettbewerbsbeiträge importieren, anhören, bewerten und als eindeutige Top 15 abschließen.</Typography>
        {show.ballotClosedAt != null && <Stack spacing={0.5} sx={{ mt: 1 }}>
          <Typography color="text.secondary" variant="body2">Teilnehmerzuordnung: {show.assignedEntryCount}/{show.contestEntryCount} · Ergebnis: {show.resultsClosedAt === null ? `${show.knownActiveResultCount}/${show.activeParticipantCount} aktive Teilnehmer erfasst` : 'abgeschlossen'}</Typography>
          <Typography color="text.secondary" variant="body2">Berechnet: {show.calculatedTotalPoints ?? 0} Punkte{show.officialTotalPoints != null ? ` · Offiziell: ${show.officialTotalPoints} Punkte` : ''}</Typography>
          {show.officialTotalDifference != null && show.officialTotalDifference !== 0 && <Alert severity="warning">Die offizielle Summe weicht um {Math.abs(show.officialTotalDifference)} Punkte ab.</Alert>}
          {show.finalPlace != null && <Typography color="text.secondary" variant="body2">Endplatzierung: {show.finalPlace}. Platz{show.finalPlaceTied ? ' (geteilt)' : ''}</Typography>}
        </Stack>}
      </Box>}
      {error !== null && <ApiErrorNotice error={error.apiError} />}
      {entries === null && error === null && <EntryLoading />}
      {entries !== null && show !== null && <>
        <ClipboardImportArea onPasteData={pasteForPreview} />
        {previewLines !== null && <ImportPreview importing={importing} lines={previewLines} onCancel={() => setPreviewLines(null)} onChange={setPreviewLines} onImport={() => void confirmImport()} />}
        <DragDropContext onDragEnd={(result) => void onDragEnd(result)}>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={3} sx={{ alignItems: 'flex-start' }}>
            <Box aria-label="Beitragspool" component="section" sx={{ flex: 1, minWidth: 0 }}>
              <Stack spacing={1.5}>
                <Stack direction={{ sm: 'row', xs: 'column' }} spacing={1} sx={{ alignItems: { sm: 'center' }, justifyContent: 'space-between' }}>
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center', minWidth: 0 }}>
                    <Typography component="h2" variant="h5">Beitragspool</Typography>
                    {visibleCountLabel !== null && <Chip label={visibleCountLabel} size="small" />}
                  </Stack>
                  <Button onClick={() => { setEditing(null); setCreating(true) }} variant="outlined">Beitrag manuell anlegen</Button>
                </Stack>
                <EntryPoolControls filters={filters} onFilters={{
                  search: setSearch,
                  unlistened: () => setOnlyUnlistened((current) => !current),
                  relisten: () => setOnlyRelisten((current) => !current),
                  unranked: () => setOnlyUnranked((current) => !current),
                  withoutParticipant: () => setOnlyWithoutParticipant((current) => !current),
                }} onSortMode={setSortMode} participantAssignmentOpen={participantAssignmentOpen} sortMode={sortMode} />
                {!poolReorderEnabled && <InlineHint>Die Reihenfolge im Pool kann nur ohne Suche oder Filter bei manueller Reihenfolge geändert werden. Ziehen in die Rangliste bleibt möglich.</InlineHint>}
                {entries.length === 0 && <Alert severity="info">Noch keine Wettbewerbsbeiträge. Lege einen Beitrag an oder füge einen CSC-Beitragsblock ein.</Alert>}
                {entries.length > 0 && visibleEntries.length === 0 && <Alert severity="info">Kein Beitrag erfüllt die aktuelle Suche und Filterkombination.</Alert>}
                {visibleEntries.length > 0 && <PoolList
                  activeEntryId={activeEntryId}
                  dndBusy={dndBusy}
                  entries={visibleEntries}
                  onDelete={setEntryPendingDeletion}
                  onEdit={setEditing}
                  onPlay={(entry) => setActiveEntryId(entry.id)}
                  onSelect={(entry) => setActiveEntryId(entry.id)}
                  onToggleListened={(entry) => void saveEntry({ ...entry, listened: !entry.listened })}
                  onToggleRelisten={(entry) => void saveEntry({ ...entry, relisten: !entry.relisten })}
                />}
              </Stack>
            </Box>
            <Paper component="aside" elevation={0} sx={{ backgroundColor: 'action.hover', border: 1, borderColor: 'divider', p: 2, position: { md: 'sticky' }, top: 24, width: { md: 450, xs: '100%' } }}>
              <Stack spacing={2}>
                <YoutubePlayerPanel contextLabel="Aktuell ausgewählter Wettbewerbsbeitrag" emptyMessage="Wähle einen Wettbewerbsbeitrag aus, um ihn hier anzuhören." song={activeEntry} />
                {activeEntry !== null && <>
                  <Stack direction="row" spacing={1}>
                    <Button disabled={activeIndex <= 0} onClick={() => setActiveEntryId(visibleEntries[activeIndex - 1]?.id ?? null)}>Vorheriger</Button>
                    <Button disabled={activeIndex < 0 || activeIndex >= visibleEntries.length - 1} onClick={() => setActiveEntryId(visibleEntries[activeIndex + 1]?.id ?? null)}>Nächster</Button>
                  </Stack>
                  {participantAssignmentOpen && <Stack spacing={1}>
                    <Typography component="h2" variant="subtitle1">Teilnehmerzuordnung</Typography>
                    {participants === null ? <Skeleton height={56} variant="rounded" /> : <ParticipantSelect
                      label="Teilnehmer dieses Beitrags"
                      onChange={(participant) => void saveParticipantAssignment(activeEntry, participant)}
                      options={participants}
                      value={participants.find((participant) => participant.id === activeEntry.participantId) ?? null}
                    />}
                    {activeEntry.participantId === null && <Alert severity="warning">Diesem Beitrag ist noch kein Teilnehmer zugeordnet.</Alert>}
                  </Stack>}
                </>}
                {ballot !== null && <BallotPanel
                  activeEntryId={activeEntryId}
                  ballot={ballot}
                  entries={entries}
                  onClose={() => void closeRanking()}
                  onRemove={(entry) => void removeRanking(entry)}
                  onReopen={() => void reopenRanking()}
                  onSelect={(entry) => setActiveEntryId(entry.id)}
                  reordering={dndBusy}
                  showId={showId}
                />}
              </Stack>
            </Paper>
          </Stack>
        </DragDropContext>
      </>}
      <EntryDialog creating={creating} entry={editing} key={`${creating}-${editing?.id ?? 'none'}`} onClose={() => { setEditing(null); setCreating(false) }} onCreate={(input) => void saveNewEntry(input)} onSave={(entry) => void saveEntry(entry)} saving={savingNewEntry} />
      <DeleteEntryDialog entry={entryPendingDeletion} onClose={() => setEntryPendingDeletion(null)} onConfirm={() => entryPendingDeletion !== null && void removeEntry(entryPendingDeletion)} />
    </Stack>
  )
}

function EntryPoolControls({ filters, onFilters, participantAssignmentOpen, sortMode, onSortMode }: {
  filters: EntryPoolFilters
  onFilters: { search: (value: string) => void, unlistened: () => void, relisten: () => void, unranked: () => void, withoutParticipant: () => void }
  participantAssignmentOpen: boolean
  sortMode: EntryPoolSortMode
  onSortMode: (value: EntryPoolSortMode) => void
}) {
  return <Paper component="section" elevation={0} sx={{ backgroundColor: 'action.hover', border: 1, borderColor: 'divider', p: 1 }}>
    <Stack aria-label="Beitragspool filtern und sortieren" direction={{ lg: 'row', xs: 'column' }} role="toolbar" spacing={1}>
      <TextField label="Beiträge suchen" onChange={(event) => onFilters.search(event.target.value)} placeholder="Interpret oder Titel" size="small" sx={{ flex: 1, minWidth: { lg: 200 } }} value={filters.search}
        slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon aria-hidden="true" color="secondary" fontSize="small" /></InputAdornment> } }} />
      <Select aria-label="Beitragspool sortieren" onChange={(event) => onSortMode(event.target.value as EntryPoolSortMode)} size="small" sx={{ minWidth: 180 }} startAdornment={<SortIcon aria-hidden="true" fontSize="small" sx={{ ml: 1, mr: 0.5 }} />} value={sortMode}>
        <MenuItem value="MANUAL">Manuelle Reihenfolge</MenuItem><MenuItem value="ARTIST">Interpret</MenuItem><MenuItem value="TITLE">Titel</MenuItem><MenuItem value="LISTENED">Hörstatus</MenuItem><MenuItem value="RELISTEN">Wiedervorlage</MenuItem><MenuItem value="RANK">Rang</MenuItem><MenuItem value="CREATED">Erfassungszeitpunkt</MenuItem>
      </Select>
      <Stack direction="row" spacing={0.5} sx={{ flexShrink: 0, flexWrap: 'wrap' }} useFlexGap>
        <PoolFilterButton icon={<SpeakerCrossIcon aria-hidden="true" fontSize="small" />} label="Nicht gehört" onClick={onFilters.unlistened} pressed={filters.onlyUnlistened} />
        <PoolFilterButton icon={<ReplayIcon aria-hidden="true" fontSize="small" />} label="Erneut anhören" onClick={onFilters.relisten} pressed={filters.onlyRelisten} />
        <PoolFilterButton icon={<RankIcon aria-hidden="true" fontSize="small" />} label="Noch nicht gerankt" onClick={onFilters.unranked} pressed={filters.onlyUnranked} />
        {participantAssignmentOpen && <PoolFilterButton icon={<FilterIcon aria-hidden="true" fontSize="small" />} label="Ohne Teilnehmer" onClick={onFilters.withoutParticipant} pressed={filters.onlyWithoutParticipant} />}
      </Stack>
    </Stack>
  </Paper>
}

function PoolFilterButton({ label, pressed, icon, onClick }: { label: string, pressed: boolean, icon: ReactNode, onClick: () => void }) {
  return <Button aria-label={label} aria-pressed={pressed} onClick={onClick} size="small" startIcon={icon} variant={pressed ? 'contained' : 'outlined'}>{label}</Button>
}

function InlineHint({ children }: { children: ReactNode }) {
  return <Alert icon={<DragIcon aria-hidden="true" fontSize="small" />} severity="info" sx={{ py: 0.25 }}>{children}</Alert>
}

function PoolList({ activeEntryId, dndBusy, entries, onSelect, onPlay, onToggleListened, onToggleRelisten, onEdit, onDelete }: {
  activeEntryId: number | null
  dndBusy: boolean
  entries: ContestEntry[]
  onSelect: (entry: ContestEntry) => void
  onPlay: (entry: ContestEntry) => void
  onToggleListened: (entry: ContestEntry) => void
  onToggleRelisten: (entry: ContestEntry) => void
  onEdit: (entry: ContestEntry) => void
  onDelete: (entry: ContestEntry) => void
}) {
  return <Droppable droppableId={ENTRY_POOL_DROPPABLE_ID} isDropDisabled={dndBusy}>
    {(provided) => <Stack {...provided.droppableProps} aria-label="Wettbewerbsbeiträge" ref={provided.innerRef} spacing={0.75}>
      {entries.map((entry, index) => <Draggable draggableId={`pool-entry-${entry.id}`} index={index} isDragDisabled={dndBusy} key={entry.id}>
        {(dragProvided, dragSnapshot) => <EntryPoolCard
          active={activeEntryId === entry.id}
          dragHandleProps={dragProvided.dragHandleProps}
          dragging={dragSnapshot.isDragging}
          entry={entry}
          onDelete={() => onDelete(entry)} onEdit={() => onEdit(entry)} onPlay={() => onPlay(entry)} onSelect={() => onSelect(entry)}
          onToggleListened={() => onToggleListened(entry)} onToggleRelisten={() => onToggleRelisten(entry)} provided={dragProvided}
        />}
      </Draggable>)}
      {provided.placeholder}
    </Stack>}
  </Droppable>
}

function EntryPoolCard({ entry, active, dragging, provided, dragHandleProps, onSelect, onPlay, onToggleListened, onToggleRelisten, onEdit, onDelete }: {
  entry: ContestEntry
  active: boolean
  dragging: boolean
  provided: DraggableProvided
  dragHandleProps: DraggableProvidedDragHandleProps | null | undefined
  onSelect: () => void
  onPlay: () => void
  onToggleListened: () => void
  onToggleRelisten: () => void
  onEdit: () => void
  onDelete: () => void
}) {
  const dragEnabled = dragHandleProps !== null && dragHandleProps !== undefined
  /* eslint-disable react-hooks/refs -- @hello-pangea/dnd requires forwarding these render-prop refs. */
  return <Card {...provided.draggableProps} elevation={dragging ? 8 : 0} ref={provided.innerRef} sx={{ backgroundColor: active ? 'action.selected' : 'background.paper', border: 1, borderColor: dragging || active ? 'secondary.main' : 'divider', outline: dragging ? '2px solid' : 'none', outlineColor: 'secondary.main', transition: 'border-color 120ms ease, background-color 120ms ease' }}>
    <CardContent sx={{ p: { xs: 1, md: 1.25 }, '&:last-child': { pb: { xs: 1, md: 1.25 } } }}>
      <Stack direction={{ md: 'row', xs: 'column' }} spacing={1} sx={{ alignItems: { md: 'center', xs: 'stretch' } }}>
        <Tooltip title={dragEnabled ? 'Ziehen: im Pool umsortieren oder zur Rangliste hinzufügen' : 'Drag-and-drop wird gespeichert'}>
          <Box {...(dragHandleProps ?? {})} aria-disabled={!dragEnabled} aria-label={`${entry.artist} verschieben`} sx={{ alignItems: 'center', borderRadius: 1, color: dragEnabled ? 'text.secondary' : 'action.disabled', cursor: dragging ? 'grabbing' : dragEnabled ? 'grab' : 'default', display: 'inline-flex', flexShrink: 0, justifyContent: 'center', minHeight: 40, minWidth: 40, '&:active': { cursor: 'grabbing' } }}><DragIcon aria-hidden="true" /></Box>
        </Tooltip>
        <Box aria-current={active ? 'true' : undefined} aria-label={`${entry.title} von ${entry.artist} auswählen`} onClick={onSelect} onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); onSelect() } }} role="button" sx={{ cursor: 'pointer', flex: 1, minWidth: 0, outlineOffset: 4, '&:focus-visible': { outline: '2px solid', outlineColor: 'secondary.main' } }} tabIndex={0}>
          <Typography component="h3" sx={{ fontWeight: 650, overflowWrap: 'anywhere' }} variant="subtitle1">{entry.title}</Typography>
          <Typography color="text.secondary" sx={{ overflowWrap: 'anywhere' }} variant="body2">{entry.artist}</Typography>
          {active && <Chip color="secondary" icon={<PlayIcon aria-hidden="true" />} label="Wird angehört" size="small" sx={{ mt: 0.75 }} />}
          {entry.comment !== null && entry.comment.trim() !== '' && <Typography color="text.secondary" sx={{ mt: 0.5, overflowWrap: 'anywhere' }} variant="body2">{entry.comment}</Typography>}
        </Box>
        <Stack direction="row" spacing={0.25} sx={{ alignItems: 'center', flexShrink: 0, flexWrap: { md: 'nowrap', xs: 'wrap' }, justifyContent: 'flex-end', minHeight: 40 }}>
          <EntryStatusToggle ariaLabel={`${entry.title}: ${entry.listened ? 'Gehört' : 'Nicht gehört'}`} color={entry.listened ? 'success' : 'error'} icon={entry.listened ? <SpeakerCheckIcon aria-hidden="true" fontSize="small" /> : <SpeakerCrossIcon aria-hidden="true" fontSize="small" />} onClick={onToggleListened} pressed={entry.listened} title={entry.listened ? 'Gehört – als nicht gehört markieren' : 'Nicht gehört – als gehört markieren'} />
          <EntryStatusToggle ariaLabel={`${entry.title}: ${entry.relisten ? 'Erneut anhören' : 'Nicht zur Wiedervorlage markiert'}`} color={entry.relisten ? 'secondary' : 'default'} icon={<ReplayIcon aria-hidden="true" fontSize="small" />} onClick={onToggleRelisten} pressed={entry.relisten} title={entry.relisten ? 'Erneut anhören – Wiedervorlage entfernen' : 'Für erneutes Anhören vormerken'} />
          <Tooltip title={entry.rankingPosition === null ? 'Noch nicht gerankt' : `Rang ${entry.rankingPosition}`}><Chip aria-label={`${entry.title}: ${entry.rankingPosition === null ? 'Noch nicht gerankt' : `Rang ${entry.rankingPosition}`}`} icon={<RankIcon aria-hidden="true" fontSize="small" />} label={entry.rankingPosition === null ? 'Noch nicht gerankt' : `Rang ${entry.rankingPosition}`} size="small" sx={{ color: entry.rankingPosition === null ? 'text.secondary' : 'secondary.main', maxWidth: { xs: 'none', sm: 164 } }} /></Tooltip>
          <Tooltip title="Anhören"><IconButton aria-label={`${entry.artist} – ${entry.title} anhören`} color={active ? 'secondary' : 'primary'} onClick={onPlay} size="small"><PlayIcon aria-hidden="true" fontSize="small" /></IconButton></Tooltip>
          <EntryOverflowMenu entry={entry} onDelete={onDelete} onEdit={onEdit} />
        </Stack>
      </Stack>
    </CardContent>
  </Card>
  /* eslint-enable react-hooks/refs */
}

function EntryStatusToggle({ ariaLabel, color, icon, onClick, pressed, title }: { ariaLabel: string, color: 'default' | 'error' | 'success' | 'secondary', icon: ReactNode, onClick: () => void, pressed: boolean, title: string }) {
  return <Tooltip title={title}><IconButton aria-label={ariaLabel} aria-pressed={pressed} color={color} onClick={onClick} size="small" sx={{ border: 1, borderColor: pressed ? 'secondary.main' : 'divider', color: pressed ? undefined : color === 'default' ? 'text.disabled' : undefined }}>{icon}</IconButton></Tooltip>
}

function EntryOverflowMenu({ entry, onEdit, onDelete }: { entry: ContestEntry, onEdit: () => void, onDelete: () => void }) {
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null)
  const open = anchorEl !== null
  function run(action: () => void) { setAnchorEl(null); action() }
  return <><Tooltip title="Weitere Aktionen"><IconButton aria-controls={open ? `entry-${entry.id}-actions` : undefined} aria-expanded={open ? 'true' : undefined} aria-haspopup="menu" aria-label={`Weitere Aktionen für ${entry.artist} – ${entry.title}`} color="primary" onClick={(event) => setAnchorEl(event.currentTarget)} size="small"><MoreIcon aria-hidden="true" fontSize="small" /></IconButton></Tooltip>
    <Menu anchorEl={anchorEl} id={`entry-${entry.id}-actions`} onClose={() => setAnchorEl(null)} open={open}>
      <MenuItem onClick={() => run(onEdit)}><EditIcon aria-hidden="true" fontSize="small" sx={{ mr: 1 }} />Bearbeiten</MenuItem>
      <MenuItem onClick={() => run(onDelete)} sx={{ color: 'error.main' }}><DeleteIcon aria-hidden="true" fontSize="small" sx={{ mr: 1 }} />Löschen</MenuItem>
    </Menu>
  </>
}

function EntryDialog({ creating, entry, saving, onClose, onCreate, onSave }: { creating: boolean, entry: ContestEntry | null, saving: boolean, onClose: () => void, onCreate: (input: ContestEntryInput) => void, onSave: (entry: ContestEntry) => void }) {
  const [draft, setDraft] = useState<ContestEntryInput | null>(null)
  const initial = draft ?? (entry === null ? emptyEntryInput : entry)
  const open = creating || entry !== null
  return <Dialog fullWidth maxWidth="sm" onClose={onClose} open={open}><DialogTitle>{creating ? 'Wettbewerbsbeitrag anlegen' : 'Wettbewerbsbeitrag bearbeiten'}</DialogTitle><DialogContent><Stack spacing={2} sx={{ pt: 1 }}>
    <TextField fullWidth label="Interpret" onChange={(event) => setDraft({ ...initial, artist: event.target.value })} value={initial.artist} />
    <TextField fullWidth label="Titel" onChange={(event) => setDraft({ ...initial, title: event.target.value })} value={initial.title} />
    <TextField fullWidth label="YouTube-Link" onChange={(event) => setDraft({ ...initial, youtubeUrl: event.target.value })} value={initial.youtubeUrl} />
    <TextField fullWidth label="Kommentar / Hörnotiz" minRows={3} multiline onChange={(event) => setDraft({ ...initial, comment: event.target.value })} value={initial.comment ?? ''} />
  </Stack></DialogContent><DialogActions><Button disabled={saving} onClick={onClose}>Abbrechen</Button><Button disabled={saving} onClick={() => creating ? onCreate(initial) : entry !== null && onSave({ ...entry, ...initial })} variant="contained">Speichern</Button></DialogActions></Dialog>
}

function DeleteEntryDialog({ entry, onClose, onConfirm }: { entry: ContestEntry | null, onClose: () => void, onConfirm: () => void }) {
  return <Dialog onClose={onClose} open={entry !== null}><DialogTitle>Wettbewerbsbeitrag löschen?</DialogTitle><DialogContent><Typography>{entry === null ? '' : `${entry.artist} – ${entry.title}`} wird dauerhaft entfernt.</Typography></DialogContent><DialogActions><Button onClick={onClose}>Abbrechen</Button><Button color="error" onClick={onConfirm} variant="contained">Beitrag löschen</Button></DialogActions></Dialog>
}

function EntryLoading() { return <Stack spacing={1}>{Array.from({ length: 3 }, (_, index) => <Skeleton height={100} key={index} variant="rounded" />)}</Stack> }

function asEntryApiError(error: unknown, path: string): EntryApiError {
  if (error instanceof EntryApiError) return error
  if (error instanceof BallotApiError) return new EntryApiError(error.apiError)
  return new EntryApiError({ timestamp: new Date().toISOString(), status: 0, code: 'NETWORK_ERROR', message: 'Die Wettbewerbsbeiträge konnten nicht verarbeitet werden.', path })
}
