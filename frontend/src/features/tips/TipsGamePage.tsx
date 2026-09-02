import { DragDropContext, Draggable, Droppable, type DropResult } from '@hello-pangea/dnd'
import {
  Alert, Box, Button, Card, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogTitle,
  FormControl, InputAdornment, InputLabel, MenuItem, Paper, Select, Skeleton, Stack, TextField, Tooltip, Typography,
} from '@mui/material'
import { type ReactNode, useEffect, useMemo, useRef, useState } from 'react'
import { Link as RouterLink, useParams } from 'react-router-dom'
import { ApiErrorNotice } from '../../components/ApiErrorNotice'
import { CheckIcon, DragIcon, FilterIcon, SearchIcon } from '../../components/AppIcons'
import { CountryFlag } from '../participants/CountryFlag'
import {
  fetchTipsGame, fetchTipsHistory, reopenTipsGame, resolveTipsGame, saveTipsGame, type TipsAssignment,
  type TipsConfidence, type TipsEntry, type TipsGame, TipsGameApiError, type TipsGameStatus, type TipsHistory, type TipsParticipant,
} from './api'
import { TipsExportPanel } from './TipsExportPanel'
import { assignParticipant, changeTipMetadata, persistTipsDraft } from './tipsAssignments'

type TipsFilter = 'ALL' | 'UNASSIGNED' | 'LOW' | 'CORRECT' | 'INCORRECT'

export function TipsGamePage() {
  const parsedShowId = Number(useParams().showId)
  const showId = Number.isSafeInteger(parsedShowId) && parsedShowId > 0 ? parsedShowId : null
  const [game, setGame] = useState<TipsGame | null>(null)
  const [error, setError] = useState<TipsGameApiError | null>(null)
  const [saving, setSaving] = useState(false)
  const [search, setSearch] = useState('')
  const [filter, setFilter] = useState<TipsFilter>('ALL')
  const [selectedParticipationId, setSelectedParticipationId] = useState<number | null>(null)
  const [history, setHistory] = useState<TipsHistory | null>(null)
  const [historyLoading, setHistoryLoading] = useState(false)
  const [historyError, setHistoryError] = useState<TipsGameApiError | null>(null)
  const [historySearch, setHistorySearch] = useState('')
  const [resolutionAction, setResolutionAction] = useState<'resolve' | 'reopen' | null>(null)
  const [movedNotice, setMovedNotice] = useState<string | null>(null)
  const [loadRevision, setLoadRevision] = useState(0)
  const historyRequestId = useRef(0)

  useEffect(() => {
    if (showId === null) return
    let cancelled = false
    void fetchTipsGame(showId)
      .then((nextGame) => { if (!cancelled) { setError(null); setGame(nextGame) } })
      .catch((caught) => { if (!cancelled) { setGame(null); setError(asTipsError(caught, `/api/shows/${showId}/tips`)) } })
    return () => { cancelled = true }
  }, [loadRevision, showId])

  const participantsById = useMemo(() => new Map(game?.participants.map((participant) => [participant.participationId, participant]) ?? []), [game])
  const visibleEntries = useMemo(() => game?.entries.filter((entry) => visible(entry, participantsById, search, filter, game.status)) ?? [], [filter, game, participantsById, search])
  const unusedParticipants = useMemo(() => game?.participants.filter((participant) => !game.entries.some((entry) => entry.tip?.guessedParticipationId === participant.participationId)) ?? [], [game])
  const eligibleEntryCount = game?.entries.filter((entry) => !entry.ownEntry).length ?? 0
  const assignments = game?.entries.filter((entry) => !entry.ownEntry && entry.tip !== null).length ?? 0
  const allActualAssignmentsKnown = game?.actualAssignmentsComplete ?? false
  const editable = game?.status === 'DRAFT' && !saving

  async function persistEntries(previous: TipsGame, entries: TipsEntry[]) {
    if (showId === null || saving) return
    setSaving(true); setError(null)
    const optimistic = { ...previous, entries }
    try {
      await persistTipsDraft({ previous, optimistic, save: (payload) => saveTipsGame(showId, payload), onChange: setGame })
    } catch (caught) {
      setError(asTipsError(caught, `/api/shows/${showId}/tips`))
    } finally { setSaving(false) }
  }

  function changeAssignment(entryId: number, participationId: number | null) {
    if (game === null || !editable) return
    if (game.entries.find((entry) => entry.id === entryId)?.ownEntry) return
    const displaced = participationId === null ? null : game.entries.find((entry) => entry.id !== entryId && entry.tip?.guessedParticipationId === participationId)
    const participant = participationId === null ? null : participantsById.get(participationId)
    if (displaced !== undefined && displaced !== null && participant !== null && participant !== undefined) {
      setMovedNotice(`${participant.displayName} wird von „${displaced.artist} – ${displaced.title}“ zu diesem Tipp verschoben.`)
    }
    void persistEntries(game, assignParticipant(game.entries, entryId, participationId))
  }

  function focusParticipant(participationId: number) {
    if (showId === null) return
    const requestId = historyRequestId.current + 1
    historyRequestId.current = requestId
    setSelectedParticipationId(participationId)
    setHistory(null)
    setHistoryError(null)
    setHistoryLoading(true)
    void fetchTipsHistory(showId, participationId)
      .then((nextHistory) => { if (historyRequestId.current === requestId) setHistory(nextHistory) })
      .catch((caught) => { if (historyRequestId.current === requestId) setHistoryError(asTipsError(caught, `/api/shows/${showId}/tips/participants/${participationId}/history`)) })
      .finally(() => { if (historyRequestId.current === requestId) setHistoryLoading(false) })
  }

  function saveMetadata(entryId: number, patch: Pick<TipsAssignment, 'confidence' | 'note'>) {
    if (game === null || !editable) return
    void persistEntries(game, changeTipMetadata(game.entries, entryId, patch))
  }

  function dragEnd(result: DropResult) {
    if (result.destination === null || !result.destination.droppableId.startsWith('tips-entry-')) return
    const participationId = Number(result.draggableId.replace('tips-participant-', ''))
    const entryId = Number(result.destination.droppableId.replace('tips-entry-', ''))
    if (Number.isSafeInteger(participationId) && Number.isSafeInteger(entryId) && !game?.entries.find((entry) => entry.id === entryId)?.ownEntry) changeAssignment(entryId, participationId)
  }

  async function changeResolution() {
    if (showId === null || game === null || resolutionAction === null) return
    setSaving(true); setError(null)
    try {
      setGame(resolutionAction === 'resolve' ? await resolveTipsGame(showId) : await reopenTipsGame(showId))
      setResolutionAction(null)
    } catch (caught) { setError(asTipsError(caught, `/api/shows/${showId}/tips/${resolutionAction}`)) } finally { setSaving(false) }
  }

  if (game === null) return error === null ? <TipsLoading /> : <Stack spacing={2}><ApiErrorNotice error={error.apiError} /><Button onClick={() => setLoadRevision((value) => value + 1)}>Erneut versuchen</Button></Stack>

  return <Stack spacing={3}>
    <Box>
      <Typography component="h1" variant="h4">Tippspiel</Typography>
      <Typography color="text.secondary">Ordne anonyme Wettbewerbsbeiträge bewusst vermuteten Teilnehmern zu. Die echten Einreichenden werden hier weder angezeigt noch verändert, bevor die Show aufgelöst wird.</Typography>
    </Box>
    {error !== null && <ApiErrorNotice error={error.apiError} />}
    {movedNotice !== null && <Alert onClose={() => setMovedNotice(null)} severity="info">{movedNotice}</Alert>}
    <TipsStatus game={game} assignments={assignments} eligibleEntryCount={eligibleEntryCount} allActualAssignmentsKnown={allActualAssignmentsKnown} onResolve={() => setResolutionAction('resolve')} onReopen={() => setResolutionAction('reopen')} saving={saving} />
    <TipsExportPanel ready={game.persisted && game.entries.length > 0 && assignments === eligibleEntryCount} showId={game.showId} />
    {game.entries.length === 0 && <Alert severity="info">Sobald die anonyme Songliste erfasst ist, kann hier ein Tippstand begonnen werden.</Alert>}
    {game.participants.length === 0 && <Alert severity="info">Pflege zuerst das Teilnehmerfeld der aktuellen CSC-Ausgabe.</Alert>}
    {game.statistics !== null && <TipsStatistics statistics={game.statistics} />}
    <Paper component="section" sx={{ p: 2 }}>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={1}>
        <TextField fullWidth label="Song oder Teilnehmer suchen" onChange={(event) => setSearch(event.target.value)} slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon color="action" fontSize="small" /></InputAdornment> } }} value={search} />
        <FormControl sx={{ minWidth: { xs: '100%', md: 230 } }}><InputLabel id="tips-filter-label">Filter</InputLabel><Select label="Filter" labelId="tips-filter-label" onChange={(event) => setFilter(event.target.value as TipsFilter)} value={filter}>
          <MenuItem value="ALL"><FilterIcon fontSize="small" sx={{ mr: 1, verticalAlign: 'middle' }} />Alle Beiträge</MenuItem>
          <MenuItem value="UNASSIGNED">Nur unzugeordnet</MenuItem>
          <MenuItem value="LOW">Niedrige Sicherheit</MenuItem>
          <MenuItem disabled={game.status !== 'RESOLVED'} value="CORRECT">Korrekt</MenuItem>
          <MenuItem disabled={game.status !== 'RESOLVED'} value="INCORRECT">Falsch</MenuItem>
        </Select></FormControl>
      </Stack>
    </Paper>
    {game.status === 'DRAFT' && game.entries.length > 0 && game.participants.length > 0 && <Alert severity="info">Ziehe einen Teilnehmer über den Griff auf einen Song oder nutze direkt das Dropdown an jedem Song. Beide Wege speichern denselben vollständigen Entwurf.</Alert>}
    <DragDropContext onDragEnd={dragEnd}>
      <Box sx={{ alignItems: 'start', display: 'grid', gap: 2, gridTemplateColumns: { xs: 'minmax(0, 1fr)', lg: 'minmax(0, 1fr) minmax(260px, 320px)' } }}>
        <Stack aria-label="Anonyme Beiträge" spacing={1.25}>
          {visibleEntries.length === 0 ? <Alert severity="info">Keine Beiträge für Suche oder Filter.</Alert> : visibleEntries.map((entry) => <TipsEntryCard
            editable={editable} entry={entry} gameStatus={game.status} key={entry.id} participants={game.participants}
            participantsById={participantsById} onAssign={changeAssignment} onFocusParticipant={focusParticipant} onSaveMetadata={saveMetadata}
          />)}
        </Stack>
        <Stack spacing={2} sx={{ position: { lg: 'sticky' }, top: { lg: 24 } }}>
          <ParticipantDragPanel editable={editable} participants={game.participants} unusedParticipants={unusedParticipants} onFocus={focusParticipant} />
          <ParticipantHistoryPanel error={historyError} history={history} loading={historyLoading} participation={selectedParticipationId === null ? null : participantsById.get(selectedParticipationId) ?? null} search={historySearch} onSearch={setHistorySearch} />
        </Stack>
      </Box>
    </DragDropContext>
    <Dialog onClose={() => !saving && setResolutionAction(null)} open={resolutionAction !== null}>
      <DialogTitle>{resolutionAction === 'resolve' ? 'Tippstand bewusst auflösen?' : 'Tippstand wieder öffnen?'}</DialogTitle>
      <DialogContent><Typography>{resolutionAction === 'resolve'
        ? 'Die gespeicherten Tipps werden schreibgeschützt. Die Treffer werden anschließend ausschließlich aus den aktuell gepflegten echten Einreichungszuordnungen abgeleitet.'
        : 'Danach können Tipps wieder geändert werden. Tatsächliche Einreichungszuordnungen bleiben unverändert.'}</Typography>
        {resolutionAction === 'resolve' && !allActualAssignmentsKnown && <Alert severity="warning" sx={{ mt: 2 }}>Noch sind nicht alle erfassten Beiträge tatsächlich zugeordnet.</Alert>}
      </DialogContent>
      <DialogActions><Button disabled={saving} onClick={() => setResolutionAction(null)}>Abbrechen</Button><Button color={resolutionAction === 'resolve' ? 'success' : 'warning'} disabled={saving || (resolutionAction === 'resolve' && !allActualAssignmentsKnown)} onClick={() => void changeResolution()} variant="contained">{resolutionAction === 'resolve' ? 'Jetzt auflösen' : 'Bewusst wieder öffnen'}</Button></DialogActions>
    </Dialog>
  </Stack>
}

function TipsStatus({ game, assignments, eligibleEntryCount, allActualAssignmentsKnown, saving, onResolve, onReopen }: { game: TipsGame, assignments: number, eligibleEntryCount: number, allActualAssignmentsKnown: boolean, saving: boolean, onResolve: () => void, onReopen: () => void }) {
  const unused = game.participants.length - assignments
  return <Paper sx={{ border: 1, borderColor: game.status === 'RESOLVED' ? 'success.main' : 'secondary.main', p: 2 }}><Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ alignItems: { md: 'center' }, justifyContent: 'space-between' }}>
    <Stack spacing={0.5}><Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}><Typography sx={{ fontWeight: 700 }}>{game.status === 'RESOLVED' ? 'Aufgelöster Tippstand' : 'Tippstand im Entwurf'}</Typography><Chip color={game.status === 'RESOLVED' ? 'success' : 'secondary'} label={game.status === 'RESOLVED' ? 'Schreibgeschützt' : 'Entwurf'} size="small" /></Stack>
      <Typography color="text.secondary" variant="body2">{assignments} von {eligibleEntryCount} Beiträgen zugeordnet · {unused} Teilnehmer noch ungenutzt{game.status === 'RESOLVED' && !allActualAssignmentsKnown ? ' · tatsächliche Zuordnung wurde später wieder geändert' : ''}</Typography>
    </Stack>
    {game.status === 'RESOLVED'
      ? <Button color="warning" disabled={saving} onClick={onReopen} variant="outlined">Tippstand wieder öffnen</Button>
      : <Tooltip title={allActualAssignmentsKnown ? 'Echte Einreichungszuordnungen sind vollständig gepflegt.' : 'Alle erfassten Beiträge benötigen vor der Auflösung ihre tatsächliche Zuordnung.'}><span><Button color="success" disabled={saving || !game.persisted || !allActualAssignmentsKnown} onClick={onResolve} startIcon={<CheckIcon aria-hidden="true" />} variant="contained">Auflösen</Button></span></Tooltip>}
  </Stack></Paper>
}

function TipsEntryCard({ entry, participants, participantsById, gameStatus, editable, onAssign, onSaveMetadata, onFocusParticipant }: {
  entry: TipsEntry, participants: TipsParticipant[], participantsById: Map<number, TipsParticipant>, gameStatus: TipsGameStatus,
  editable: boolean, onAssign: (entryId: number, participationId: number | null) => void,
  onSaveMetadata: (entryId: number, patch: Pick<TipsAssignment, 'confidence' | 'note'>) => void, onFocusParticipant: (participationId: number) => void,
}) {
  const guessed = entry.tip === null ? null : participantsById.get(entry.tip.guessedParticipationId) ?? null
  const outcome = outcomeFor(entry, gameStatus)
  return <Droppable droppableId={`tips-entry-${entry.id}`} isDropDisabled={!editable || entry.ownEntry}>
    {(provided, snapshot) => <Card ref={provided.innerRef} {...provided.droppableProps} sx={{ border: 1, borderColor: snapshot.isDraggingOver ? 'secondary.main' : outcome === 'CORRECT' ? 'success.main' : outcome === 'INCORRECT' ? 'error.main' : 'divider' }}>
      <CardContent><Stack spacing={1.25}>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ justifyContent: 'space-between' }}>
          <Box sx={{ minWidth: 0 }}><Typography color="text.secondary" variant="overline">{entry.ownEntry ? 'Eigene tatsächliche Einreichung' : 'Anonymer Beitrag'}</Typography><Typography sx={{ overflowWrap: 'anywhere' }} variant="h6">{entry.title}</Typography><Typography color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>{entry.artist}</Typography>{entry.ownEntry && <Chip color="warning" label="Eigene Einreichung · kein Tipp" size="small" sx={{ mt: 0.75 }} />}</Box>
          {outcome !== null && <Chip color={outcome === 'CORRECT' ? 'success' : outcome === 'INCORRECT' ? 'error' : 'warning'} label={outcome === 'CORRECT' ? 'Korrekt' : outcome === 'INCORRECT' ? 'Falsch' : 'Nicht getippt'} size="small" sx={{ alignSelf: { xs: 'start', sm: 'center' } }} />}
        </Stack>
        {editable && !entry.ownEntry && <FormControl fullWidth size="small"><InputLabel id={`tip-select-label-${entry.id}`}>Vermuteter Teilnehmer</InputLabel><Select label="Vermuteter Teilnehmer" labelId={`tip-select-label-${entry.id}`} onChange={(event) => onAssign(entry.id, String(event.target.value) === '' ? null : Number(event.target.value))} value={entry.tip?.guessedParticipationId ?? ''}><MenuItem value="">Kein Tipp</MenuItem>{participants.map((participant) => <MenuItem key={participant.participationId} value={participant.participationId}>{participant.displayName} · {participant.countryName}{participant.active ? '' : ' (inaktiv)'}</MenuItem>)}</Select></FormControl>}
        {editable && !entry.ownEntry && snapshot.isDraggingOver && <Alert severity="info">Teilnehmer hier zuordnen</Alert>}
        {guessed !== null && <ParticipantSummary label="Tipp" participant={guessed} onFocus={() => onFocusParticipant(guessed.participationId)} />}
        {gameStatus === 'RESOLVED' && !entry.ownEntry && <ActualSummary actual={entry.actualAssignment} />}
        {entry.tip !== null && editable && <TipMetadataEditor assignment={entry.tip} key={`${entry.id}-${entry.tip.confidence ?? ''}-${entry.tip.note ?? ''}`} onSave={(patch) => onSaveMetadata(entry.id, patch)} />}
        {entry.youtubeUrl !== null && <Button component="a" href={entry.youtubeUrl} rel="noreferrer" size="small" sx={{ alignSelf: 'start' }} target="_blank">Quelle öffnen</Button>}
        {provided.placeholder}
      </Stack></CardContent>
    </Card>}
  </Droppable>
}

function ParticipantSummary({ participant, label, onFocus }: { participant: TipsParticipant, label: string, onFocus: () => void }) {
  return <Stack direction="row" spacing={1} sx={{ alignItems: 'center', backgroundColor: 'action.hover', borderRadius: 1, p: 1 }}><CountryFlag code={participant.countryCode} countryName={participant.countryName} size={22} /><Box sx={{ flex: 1, minWidth: 0 }}><Typography color="text.secondary" variant="caption">{label}</Typography><Button onClick={onFocus} size="small" sx={{ display: 'block', justifyContent: 'start', maxWidth: '100%', minWidth: 0, overflow: 'hidden', px: 0, textOverflow: 'ellipsis', textTransform: 'none', whiteSpace: 'nowrap' }}>{participant.displayName} · {participant.countryName}</Button></Box></Stack>
}

function ActualSummary({ actual }: { actual: TipsEntry['actualAssignment'] }) {
  return actual === null
    ? <Alert severity="warning">Die tatsächliche Zuordnung ist derzeit nicht mehr vollständig gepflegt.</Alert>
    : <Stack direction="row" spacing={1} sx={{ alignItems: 'center', backgroundColor: 'success.dark', borderRadius: 1, p: 1 }}><CountryFlag code={actual.countryCode} countryName={actual.countryName} size={22} /><Box><Typography color="success.contrastText" variant="caption">Tatsächlich</Typography><Typography color="success.contrastText" variant="body2">{actual.displayName} · {actual.countryName}</Typography></Box></Stack>
}

function TipMetadataEditor({ assignment, onSave }: { assignment: TipsAssignment, onSave: (patch: Pick<TipsAssignment, 'confidence' | 'note'>) => void }) {
  const [confidence, setConfidence] = useState<TipsConfidence | ''>(assignment.confidence ?? '')
  const [note, setNote] = useState(assignment.note ?? '')
  return <Stack spacing={1}><Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}><FormControl size="small" sx={{ minWidth: 180 }}><InputLabel id={`confidence-${assignment.entryId}`}>Sicherheit</InputLabel><Select label="Sicherheit" labelId={`confidence-${assignment.entryId}`} onChange={(event) => setConfidence(event.target.value as TipsConfidence | '')} value={confidence}><MenuItem value="">Nicht gesetzt</MenuItem><MenuItem value="LOW">Niedrig</MenuItem><MenuItem value="MEDIUM">Mittel</MenuItem><MenuItem value="HIGH">Hoch</MenuItem></Select></FormControl><Button onClick={() => onSave({ confidence: confidence === '' ? null : confidence, note: note.trim() === '' ? null : note })} variant="outlined">Tippdetails speichern</Button></Stack><TextField fullWidth label="Notiz (optional)" maxRows={6} minRows={2} multiline onChange={(event) => setNote(event.target.value)} value={note} /></Stack>
}

function ParticipantDragPanel({ participants, unusedParticipants, editable, onFocus }: { participants: TipsParticipant[], unusedParticipants: TipsParticipant[], editable: boolean, onFocus: (participationId: number) => void }) {
  const unusedIds = new Set(unusedParticipants.map((participant) => participant.participationId))
  return <Paper component="aside" sx={{ p: 2 }}><Stack spacing={1.25}><Box><Typography component="h2" variant="h6">Teilnehmer</Typography><Typography color="text.secondary" variant="body2">{unusedParticipants.length} noch ungenutzt. Über den Griff auf einen Song ziehen oder für Recherche auswählen.</Typography></Box><Droppable droppableId="tips-participants">{(provided) => <Stack ref={provided.innerRef} {...provided.droppableProps} spacing={0.75}>{participants.map((participant, index) => <Draggable draggableId={`tips-participant-${participant.participationId}`} index={index} isDragDisabled={!editable} key={participant.participationId}>{(dragProvided, snapshot) => <Paper ref={dragProvided.innerRef} {...dragProvided.draggableProps} sx={{ border: 1, borderColor: snapshot.isDragging ? 'secondary.main' : unusedIds.has(participant.participationId) ? 'divider' : 'action.disabled', opacity: unusedIds.has(participant.participationId) ? 1 : 0.68, p: 0.75 }}><Stack direction="row" spacing={0.75} sx={{ alignItems: 'center' }}><Tooltip title={editable ? 'Teilnehmer auf einen Song ziehen' : 'Aufgelöste Tippstände sind schreibgeschützt'}><Box {...(dragProvided.dragHandleProps ?? {})} aria-label={`${participant.displayName} ziehen`} sx={{ color: editable ? 'text.secondary' : 'action.disabled', cursor: editable ? 'grab' : 'default', display: 'inline-flex', p: 0.5 }}><DragIcon aria-hidden="true" fontSize="small" /></Box></Tooltip><CountryFlag code={participant.countryCode} countryName={participant.countryName} size={22} /><Button onClick={() => onFocus(participant.participationId)} size="small" sx={{ flex: 1, justifyContent: 'start', minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', textTransform: 'none', whiteSpace: 'nowrap' }}>{participant.displayName}</Button></Stack></Paper>}</Draggable>)}{provided.placeholder}</Stack>}</Droppable></Stack></Paper>
}

function ParticipantHistoryPanel({ participation, history, loading, error, search, onSearch }: { participation: TipsParticipant | null, history: TipsHistory | null, loading: boolean, error: TipsGameApiError | null, search: string, onSearch: (search: string) => void }) {
  const normalizedSearch = search.trim().toLocaleLowerCase('de-DE')
  const visibleEntries = history?.entries.filter((entry) => `${entry.contestName} ${entry.showName} ${entry.countryName} ${entry.artist} ${entry.title}`.toLocaleLowerCase('de-DE').includes(normalizedSearch)) ?? []
  const visibleBotbSelections = history?.botbSelections.filter((selection) => `BOTB #${selection.editionNumber} ${selection.editionNumber} ${selection.artist} ${selection.knownSince ?? ''}`.toLocaleLowerCase('de-DE').includes(normalizedSearch)) ?? []
  const noSearchResults = history !== null && normalizedSearch !== '' && visibleEntries.length === 0 && visibleBotbSelections.length === 0
  return <Paper component="aside" sx={{ p: 2 }}><Stack spacing={1.25}><Box><Typography component="h2" variant="h6">Teilnehmerhistorie</Typography><Typography color="text.secondary" variant="body2">{participation === null ? 'Einen Teilnehmer auswählen, um gepflegte frühere Einreichungen derselben Identität zu sehen.' : `${participation.displayName} · ${participation.countryName}`}</Typography></Box>{participation !== null && <TextField fullWidth label="Historie durchsuchen" onChange={(event) => onSearch(event.target.value)} size="small" value={search} />}{loading && <Skeleton height={80} variant="rounded" />}{!loading && error !== null && <Alert severity="error">{error.apiError.message}</Alert>}{!loading && participation !== null && history === null && error === null && <Alert severity="info">Historie wird geladen …</Alert>}{!loading && participation !== null && history !== null && <>{noSearchResults && <Alert severity="info">Keine Historieneinträge für diese Suche gefunden.</Alert>}<HistorySection title={`Historische CSC-Einreichungen (${history.entries.length})`} empty="Keine gepflegten früheren CSC-Einreichungen gefunden." showEmpty={normalizedSearch === '' && visibleEntries.length === 0}>{visibleEntries.map((entry) => <Box key={entry.entryId} sx={{ borderLeft: 2, borderColor: 'secondary.main', pl: 1 }}><Typography color="text.secondary" variant="caption">{entry.contestName} · Show {entry.showNumber} · {entry.countryName}</Typography><Typography variant="body2">{entry.artist} – {entry.title}</Typography><Stack direction="row" spacing={1}><Button component={RouterLink} size="small" to={entry.currentContest ? `/shows/${entry.showId}/voting` : `/historical-shows/${entry.showId}`}>Archiveintrag öffnen</Button>{entry.youtubeUrl !== null && <Button component="a" href={entry.youtubeUrl} rel="noreferrer" size="small" target="_blank">Quelle öffnen</Button>}</Stack></Box>)}</HistorySection><HistorySection title={`BOTB-Interpreten (${history.botbSelections.length})`} empty="Keine BOTB-Interpreten erfasst." showEmpty={normalizedSearch === '' && visibleBotbSelections.length === 0}>{visibleBotbSelections.map((selection) => <Box key={selection.id} sx={{ borderLeft: 2, borderColor: 'info.main', pl: 1 }}><Typography variant="body2">BOTB #{selection.editionNumber} · {selection.artist}</Typography>{selection.knownSince !== null && <Typography color="text.secondary" variant="caption">bekannt seit {selection.knownSince}</Typography>}</Box>)}</HistorySection></>}</Stack></Paper>
}

function HistorySection({ title, empty, showEmpty, children }: { title: string, empty: string, showEmpty: boolean, children: ReactNode }) {
  return <Stack spacing={0.75}><Typography component="h3" sx={{ fontWeight: 700 }} variant="subtitle2">{title}</Typography>{showEmpty ? <Alert severity="info">{empty}</Alert> : <Stack spacing={1} sx={{ maxHeight: 280, overflowY: 'auto', pr: 0.5 }}>{children}</Stack>}</Stack>
}

function TipsStatistics({ statistics }: { statistics: NonNullable<TipsGame['statistics']> }) {
  return <Paper component="section" sx={{ p: 2 }}><Stack spacing={1}><Typography component="h2" variant="h6">Persönliche Auswertung</Typography><Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap' }}><Chip color="success" label={`${statistics.correct} korrekt`} /><Chip color="error" label={`${statistics.incorrect} falsch`} /><Chip color="warning" label={`${statistics.missing} nicht getippt`} /><Chip label={statistics.hitRate === null ? 'Trefferquote: keine Tipps' : `Trefferquote: ${statistics.hitRate.toFixed(1)} %`} /></Stack>{statistics.confidence.length > 0 && <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap' }}>{statistics.confidence.map((row) => <Chip key={row.confidence} label={`${confidenceLabel(row.confidence)}: ${row.correct} / ${row.tipsSubmitted}${row.hitRate === null ? '' : ` · ${row.hitRate.toFixed(1)} %`}`} variant="outlined" />)}</Stack>}</Stack></Paper>
}

function visible(entry: TipsEntry, participants: Map<number, TipsParticipant>, search: string, filter: TipsFilter, status: TipsGameStatus) {
  const guessed = entry.tip === null ? null : participants.get(entry.tip.guessedParticipationId)?.displayName ?? ''
  const actual = status === 'RESOLVED' ? entry.actualAssignment?.displayName ?? '' : ''
  const match = `${entry.artist} ${entry.title} ${guessed} ${actual}`.toLocaleLowerCase('de-DE').includes(search.trim().toLocaleLowerCase('de-DE'))
  if (!match) return false
  if (filter === 'UNASSIGNED') return !entry.ownEntry && entry.tip === null
  if (filter === 'LOW') return entry.tip?.confidence === 'LOW'
  const outcome = outcomeFor(entry, status)
  return filter === 'CORRECT' ? outcome === 'CORRECT' : filter === 'INCORRECT' ? outcome === 'INCORRECT' : true
}

function outcomeFor(entry: TipsEntry, status: TipsGameStatus): 'CORRECT' | 'INCORRECT' | 'MISSING' | null {
  if (entry.ownEntry || status !== 'RESOLVED' || entry.actualAssignment === null) return null
  if (entry.tip === null) return 'MISSING'
  return entry.tip.guessedParticipationId === entry.actualAssignment.participationId ? 'CORRECT' : 'INCORRECT'
}

function confidenceLabel(value: TipsConfidence) { return ({ LOW: 'Niedrig', MEDIUM: 'Mittel', HIGH: 'Hoch' })[value] }
function TipsLoading() { return <Stack spacing={1}><Skeleton height={80} variant="rounded" /><Skeleton height={120} variant="rounded" /><Skeleton height={120} variant="rounded" /></Stack> }
function asTipsError(error: unknown, path: string) { return error instanceof TipsGameApiError ? error : new TipsGameApiError({ timestamp: new Date().toISOString(), status: 0, code: 'NETWORK_ERROR', message: 'Der Tippstand konnte nicht verarbeitet werden.', path }) }
