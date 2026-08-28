import {
  Alert,
  Box,
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControlLabel,
  List,
  ListItemButton,
  ListItemText,
  Paper,
  Skeleton,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link as RouterLink, useParams } from 'react-router-dom'
import type { DropResult } from '@hello-pangea/dnd'
import { ApiErrorNotice } from '../../components/ApiErrorNotice'
import { BallotPanel } from '../ballot/BallotPanel'
import { BallotApiError, closeBallot, fetchBallot, reopenBallot, reorderBallot, type Ballot } from '../ballot/api'
import { combineBallotLists, persistDroppedBallot, splitBallotEntries } from '../ballot/ballotReorder'
import { fetchShows, type MottoShow } from '../shows/api'
import { fetchParticipants, type Participant } from '../participants/api'
import { ParticipantSelect } from '../participants/ParticipantSelect'
import { YoutubePlayerPanel } from '../songs/YoutubePlayerPanel'
import { ClipboardImportArea } from './ClipboardImportArea'
import { ImportPreview, type EditableImportLine } from './ImportPreview'
import {
  createEntry,
  deleteEntry,
  EntryApiError,
  fetchEntries,
  importEntries,
  previewImport,
  updateParticipantAssignment,
  updateEntry,
  type ContestEntry,
  type ContestEntryInput,
} from './api'

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
  const [previewLines, setPreviewLines] = useState<EditableImportLine[] | null>(null)
  const [importing, setImporting] = useState(false)
  const [editing, setEditing] = useState<ContestEntry | null>(null)
  const [creating, setCreating] = useState(false)
  const [savingNewEntry, setSavingNewEntry] = useState(false)
  const [entryPendingDeletion, setEntryPendingDeletion] = useState<ContestEntry | null>(null)
  const [reordering, setReordering] = useState(false)

  const show = shows?.find((item) => item.id === showId) ?? null
  const participantAssignmentOpen = ballot?.ballotClosedAt !== null && ballot?.ballotClosedAt !== undefined
  const visibleEntries = useMemo(() => (entries ?? []).filter((entry) => {
    const needle = search.trim().toLocaleLowerCase()
    return (!needle || `${entry.artist} ${entry.title}`.toLocaleLowerCase().includes(needle))
      && (!onlyUnlistened || !entry.listened)
      && (!onlyRelisten || entry.relisten)
      && (!onlyUnranked || entry.rankingPosition === null)
      && (!onlyWithoutParticipant || entry.participantId === null)
  }), [entries, onlyRelisten, onlyUnlistened, onlyUnranked, onlyWithoutParticipant, search])
  const activeEntry = activeEntryId === null ? null : visibleEntries.find((entry) => entry.id === activeEntryId) ?? null
  const activeIndex = activeEntry === null ? -1 : visibleEntries.findIndex((entry) => entry.id === activeEntry.id)

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
    try {
      setShows(await fetchShows())
    } catch (caught) {
      setError(asEntryApiError(caught, '/api/shows'))
    }
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
      setEntries((current) => current === null ? [created] : [...current, created])
      setEditing(null)
      setCreating(false)
      setActiveEntryId(created.id)
      void reloadShows()
    } catch (caught) {
      setError(asEntryApiError(caught, `/api/shows/${showId}/entries`))
    } finally {
      setSavingNewEntry(false)
    }
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
    } catch (caught) {
      setError(asEntryApiError(caught, `/api/shows/${showId}/entries/${entry.id}`))
    }
  }

  async function pasteForPreview(html: string, text: string) {
    if (showId === null) return
    setError(null)
    try {
      const lines = await previewImport(showId, html, text)
      setPreviewLines(lines.map((line) => ({ ...line, included: true })))
    } catch (caught) {
      setError(asEntryApiError(caught, `/api/shows/${showId}/entries/import-preview`))
    }
  }

  async function confirmImport() {
    if (showId === null || previewLines === null) return
    setError(null)
    setImporting(true)
    try {
      const confirmed = await importEntries(showId, previewLines.filter((line) => line.included).map((line) => ({
        artist: line.artist ?? '', title: line.title ?? '', youtubeUrl: line.youtubeUrl ?? '', comment: null,
      })))
      setEntries(confirmed)
      setPreviewLines(null)
      void reloadShows()
    } catch (caught) {
      setError(asEntryApiError(caught, `/api/shows/${showId}/entries/import`))
    } finally {
      setImporting(false)
    }
  }

  async function reorderEntries(result: DropResult) {
    if (showId === null || entries === null) return
    const confirmed = splitBallotEntries(entries)
    setError(null)
    setReordering(true)
    try {
      await persistDroppedBallot({
        result,
        confirmed,
        save: (ranking) => reorderBallot(showId, ranking),
        onOptimisticChange: (lists) => setEntries(combineBallotLists(lists)),
        onConfirmedChange: (lists) => setEntries(combineBallotLists(lists)),
      })
      void reloadShows()
    } catch (caught) {
      setError(asEntryApiError(caught, `/api/shows/${showId}/ballot/reorder`))
    } finally {
      setReordering(false)
    }
  }

  async function closeRanking() {
    if (showId === null) return
    setError(null)
    try {
      const closedBallot = await closeBallot(showId)
      setBallot(closedBallot)
      setParticipants(await fetchParticipants({ includeInactive: true }))
      void reloadShows()
    } catch (caught) {
      setError(asEntryApiError(caught, `/api/shows/${showId}/ballot/close`))
    }
  }

  async function reopenRanking() {
    if (showId === null) return
    setError(null)
    try {
      setBallot(await reopenBallot(showId))
      setParticipants(null)
      void reloadShows()
    } catch (caught) {
      setError(asEntryApiError(caught, `/api/shows/${showId}/ballot/reopen`))
    }
  }

  async function saveParticipantAssignment(entry: ContestEntry, participant: Participant | null) {
    if (showId === null) return
    setError(null)
    try {
      const updated = await updateParticipantAssignment(showId, entry.id, participant?.id ?? null)
      setEntries((current) => current?.map((item) => item.id === updated.id ? updated : item) ?? null)
      setBallot(await fetchBallot(showId))
      void reloadShows()
    } catch (caught) {
      setError(asEntryApiError(caught, `/api/shows/${showId}/entries/${entry.id}/participant`))
    }
  }

  if (showId === null) return <Alert severity="error">Die Mottoshow-ID ist ungültig.</Alert>

  return (
    <Stack spacing={3}>
      <Button component={RouterLink} sx={{ alignSelf: 'flex-start' }} to="/">Zur Übersicht</Button>
      {show === null && shows !== null && <Alert severity="error">Die Mottoshow wurde nicht gefunden.</Alert>}
      {show !== null && (
        <Box>
          <Typography color="secondary" variant="overline">Show {show.showNumber}</Typography>
          <Typography component="h1" variant="h4">{show.name} – Abstimmung</Typography>
          <Typography color="text.secondary" sx={{ mt: 1 }}>
            Wettbewerbsbeiträge importieren, anhören, bewerten und als eindeutige Top 15 abschließen.
          </Typography>
          {show.ballotClosedAt != null && <Stack spacing={0.5} sx={{ mt: 1 }}>
            <Typography color="text.secondary" variant="body2">
              Teilnehmerzuordnung: {show.assignedEntryCount}/{show.contestEntryCount} · Ergebnis: {show.resultsClosedAt === null ? `${show.knownActiveResultCount}/${show.activeParticipantCount} aktive Teilnehmer erfasst` : 'abgeschlossen'}
            </Typography>
            <Typography color="text.secondary" variant="body2">
              Berechnet: {show.calculatedTotalPoints ?? 0} Punkte{show.officialTotalPoints != null ? ` · Offiziell: ${show.officialTotalPoints} Punkte` : ''}
            </Typography>
            {show.officialTotalDifference != null && show.officialTotalDifference !== 0 && <Alert severity="warning">Die offizielle Summe weicht um {Math.abs(show.officialTotalDifference)} Punkte ab.</Alert>}
            {show.finalPlace != null && <Typography color="text.secondary" variant="body2">Endplatzierung: {show.finalPlace}. Platz{show.finalPlaceTied ? ' (geteilt)' : ''}</Typography>}
          </Stack>}
        </Box>
      )}
      {error !== null && <ApiErrorNotice error={error.apiError} />}
      {entries === null && error === null && <EntryLoading />}
      {entries !== null && show !== null && (
        <>
          <ClipboardImportArea onPasteData={pasteForPreview} />
          {previewLines !== null && <ImportPreview importing={importing} lines={previewLines} onCancel={() => setPreviewLines(null)} onChange={setPreviewLines} onImport={() => void confirmImport()} />}
          {ballot !== null && <BallotPanel ballot={ballot} entries={entries} onClose={() => void closeRanking()} onDrop={(result) => void reorderEntries(result)} onReopen={() => void reopenRanking()} reordering={reordering} showId={showId} />}
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={3}>
            <Paper aria-label="Beitragspool" component="section" sx={{ flex: 1, minWidth: 0, p: 2 }}>
              <Stack spacing={2}>
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} sx={{ alignItems: { md: 'center' }, justifyContent: 'space-between' }}>
                  <Typography component="h2" variant="h6">Beitragspool ({visibleEntries.length})</Typography>
                  <Button onClick={() => { setEditing(null); setCreating(true) }} variant="outlined">Beitrag manuell anlegen</Button>
                </Stack>
                <TextField fullWidth label="Interpret oder Titel suchen" onChange={(event) => setSearch(event.target.value)} value={search} />
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
                  <FormControlLabel control={<Checkbox checked={onlyUnlistened} onChange={(event) => setOnlyUnlistened(event.target.checked)} />} label="Ungehört" />
                  <FormControlLabel control={<Checkbox checked={onlyRelisten} onChange={(event) => setOnlyRelisten(event.target.checked)} />} label="Erneut anhören" />
                  <FormControlLabel control={<Checkbox checked={onlyUnranked} onChange={(event) => setOnlyUnranked(event.target.checked)} />} label="Noch nicht eingeordnet" />
                  {participantAssignmentOpen && <FormControlLabel control={<Checkbox checked={onlyWithoutParticipant} onChange={(event) => setOnlyWithoutParticipant(event.target.checked)} />} label="Ohne Teilnehmer" />}
                </Stack>
                {entries.length === 0 && <Alert severity="info">Noch keine Wettbewerbsbeiträge. Lege einen Beitrag an oder füge einen CSC-Beitragsblock ein.</Alert>}
                {entries.length > 0 && visibleEntries.length === 0 && <Alert severity="info">Kein Beitrag erfüllt die aktuelle Suche und Filterkombination.</Alert>}
                {visibleEntries.length > 0 && (
                  <List aria-label="Wettbewerbsbeiträge" disablePadding sx={{ border: 1, borderColor: 'divider' }}>
                    {visibleEntries.map((entry, index) => (
                      <Box key={entry.id}>
                        {index > 0 && <Divider />}
                        <ListItemButton aria-current={activeEntry?.id === entry.id ? 'true' : undefined} onClick={() => setActiveEntryId(entry.id)} selected={activeEntry?.id === entry.id}>
                          <ListItemText
                            primary={`${entry.artist} – ${entry.title}`}
                            secondary={`${entry.listened ? 'Gehört' : 'Ungelesen'} · ${entry.relisten ? 'erneut anhören' : 'keine Wiedervorlage'}${entry.rankingPosition === null ? ' · noch nicht eingeordnet' : ` · Rang ${entry.rankingPosition}`}${participantAssignmentOpen ? ` · ${entry.participantId === null ? 'ohne Teilnehmer' : participantLabel(entry.participantId, participants)}` : ''}`}
                          />
                        </ListItemButton>
                      </Box>
                    ))}
                  </List>
                )}
              </Stack>
            </Paper>
            <Paper component="aside" sx={{ flex: 1, minWidth: 0, p: 2 }}>
              <Stack spacing={2}>
                <YoutubePlayerPanel contextLabel="Aktuell ausgewählter Wettbewerbsbeitrag" emptyMessage="Wähle einen Wettbewerbsbeitrag aus, um ihn hier anzuhören." song={activeEntry} />
                {activeEntry !== null && (
                  <>
                    <Stack direction="row" spacing={1}>
                      <Button disabled={activeIndex <= 0} onClick={() => setActiveEntryId(visibleEntries[activeIndex - 1]?.id ?? null)}>Vorheriger</Button>
                      <Button disabled={activeIndex < 0 || activeIndex >= visibleEntries.length - 1} onClick={() => setActiveEntryId(visibleEntries[activeIndex + 1]?.id ?? null)}>Nächster</Button>
                    </Stack>
                    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
                      <FormControlLabel control={<Checkbox checked={activeEntry.listened} onChange={(event) => void saveEntry({ ...activeEntry, listened: event.target.checked })} />} label="Gehört" />
                      <FormControlLabel control={<Checkbox checked={activeEntry.relisten} onChange={(event) => void saveEntry({ ...activeEntry, relisten: event.target.checked })} />} label="Erneut anhören" />
                    </Stack>
                    <TextField defaultValue={activeEntry.comment ?? ''} fullWidth key={activeEntry.id} label="Kommentar / Hörnotiz" minRows={3} multiline onBlur={(event) => {
                      if (event.target.value !== (activeEntry.comment ?? '')) void saveEntry({ ...activeEntry, comment: event.target.value })
                    }} />
                    <Stack direction="row" spacing={1}>
                      <Button onClick={() => setEditing(activeEntry)}>Bearbeiten</Button>
                      <Button color="error" onClick={() => setEntryPendingDeletion(activeEntry)}>Löschen</Button>
                    </Stack>
                    {participantAssignmentOpen && (
                      <Stack spacing={1}>
                        <Typography component="h2" variant="subtitle1">Teilnehmerzuordnung</Typography>
                        {participants === null ? <Skeleton height={56} variant="rounded" /> : (
                          <ParticipantSelect
                            label="Teilnehmer dieses Beitrags"
                            onChange={(participant) => void saveParticipantAssignment(activeEntry, participant)}
                            options={participants}
                            value={participants.find((participant) => participant.id === activeEntry.participantId) ?? null}
                          />
                        )}
                        {activeEntry.participantId === null && <Alert severity="warning">Diesem Beitrag ist noch kein Teilnehmer zugeordnet.</Alert>}
                      </Stack>
                    )}
                  </>
                )}
              </Stack>
            </Paper>
          </Stack>
        </>
      )}
      <EntryDialog creating={creating} entry={editing} key={`${creating}-${editing?.id ?? 'none'}`} onClose={() => { setEditing(null); setCreating(false) }} onCreate={(input) => void saveNewEntry(input)} onSave={(entry) => void saveEntry(entry)} saving={savingNewEntry} />
      <DeleteEntryDialog entry={entryPendingDeletion} onClose={() => setEntryPendingDeletion(null)} onConfirm={() => entryPendingDeletion !== null && void removeEntry(entryPendingDeletion)} />
    </Stack>
  )
}

function EntryDialog({ creating, entry, saving, onClose, onCreate, onSave }: {
  creating: boolean
  entry: ContestEntry | null
  saving: boolean
  onClose: () => void
  onCreate: (input: ContestEntryInput) => void
  onSave: (entry: ContestEntry) => void
}) {
  const [draft, setDraft] = useState<ContestEntryInput | null>(null)
  const initial = draft ?? (entry === null ? emptyEntryInput : entry)
  const open = creating || entry !== null
  return (
    <Dialog fullWidth maxWidth="sm" onClose={onClose} open={open}>
      <DialogTitle>{creating ? 'Wettbewerbsbeitrag anlegen' : 'Wettbewerbsbeitrag bearbeiten'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <TextField fullWidth label="Interpret" onChange={(event) => setDraft({ ...initial, artist: event.target.value })} value={initial.artist} />
          <TextField fullWidth label="Titel" onChange={(event) => setDraft({ ...initial, title: event.target.value })} value={initial.title} />
          <TextField fullWidth label="YouTube-Link" onChange={(event) => setDraft({ ...initial, youtubeUrl: event.target.value })} value={initial.youtubeUrl} />
          <TextField fullWidth label="Kommentar / Hörnotiz" minRows={3} multiline onChange={(event) => setDraft({ ...initial, comment: event.target.value })} value={initial.comment ?? ''} />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button disabled={saving} onClick={onClose}>Abbrechen</Button>
        <Button disabled={saving} onClick={() => creating ? onCreate(initial) : entry !== null && onSave({ ...entry, ...initial })} variant="contained">Speichern</Button>
      </DialogActions>
    </Dialog>
  )
}

function DeleteEntryDialog({ entry, onClose, onConfirm }: { entry: ContestEntry | null, onClose: () => void, onConfirm: () => void }) {
  return (
    <Dialog onClose={onClose} open={entry !== null}>
      <DialogTitle>Wettbewerbsbeitrag löschen?</DialogTitle>
      <DialogContent><Typography>{entry === null ? '' : `${entry.artist} – ${entry.title}`} wird dauerhaft entfernt.</Typography></DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Abbrechen</Button>
        <Button color="error" onClick={onConfirm} variant="contained">Beitrag löschen</Button>
      </DialogActions>
    </Dialog>
  )
}

function EntryLoading() {
  return <Stack spacing={1}>{Array.from({ length: 3 }, (_, index) => <Skeleton height={100} key={index} variant="rounded" />)}</Stack>
}

function asEntryApiError(error: unknown, path: string): EntryApiError {
  if (error instanceof EntryApiError) return error
  if (error instanceof BallotApiError) return new EntryApiError(error.apiError)
  return new EntryApiError({
    timestamp: new Date().toISOString(), status: 0, code: 'NETWORK_ERROR',
    message: 'Die Wettbewerbsbeiträge konnten nicht verarbeitet werden.', path,
  })
}

function participantLabel(participantId: number, participants: Participant[] | null): string {
  const participant = participants?.find((item) => item.id === participantId)
  return participant === undefined ? 'Teilnehmer wird geladen' : participant.displayName
}
