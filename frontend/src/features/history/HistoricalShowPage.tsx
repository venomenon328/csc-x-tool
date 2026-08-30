import { Alert, Box, Button, Checkbox, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, MenuItem, Paper, Select, Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography } from '@mui/material'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { ApiErrorNotice } from '../../components/ApiErrorNotice'
import { ClipboardImportArea } from '../entries/ClipboardImportArea'
import { completeHistoricalEntryList, createEntry, deleteEntry, fetchEntries, importHistoricalEntries, previewHistoricalImport, reopenHistoricalEntryList, updateEntry, type ContestEntry, type ContestEntryInput, type EntryApiError, EntryApiError as EntryError, type HistoricalImportPreviewLine } from '../entries/api'
import { fetchParticipants, type Participant } from '../participants/api'
import { fetchShow, type MottoShow } from '../shows/api'

type EditablePreviewLine = HistoricalImportPreviewLine & { included: boolean, replaceExisting: boolean }
type EntryDraft = ContestEntryInput & { participantId: number | null }
const emptyDraft: EntryDraft = { artist: '', title: '', youtubeUrl: '', comment: null, participantId: null }

export function HistoricalShowPage() {
  const showId = Number(useParams().showId)
  const [show, setShow] = useState<MottoShow | null>(null)
  const [entries, setEntries] = useState<ContestEntry[]>([])
  const [participants, setParticipants] = useState<Participant[]>([])
  const [error, setError] = useState<EntryApiError | null>(null)
  const [search, setSearch] = useState('')
  const [onlyUnassigned, setOnlyUnassigned] = useState(false)
  const [editor, setEditor] = useState<ContestEntry | null | undefined>(undefined)
  const [preview, setPreview] = useState<EditablePreviewLine[] | null>(null)
  const [importing, setImporting] = useState(false)
  const [confirmAction, setConfirmAction] = useState<'complete' | 'reopen' | null>(null)

  const load = useCallback(async () => {
    try {
      const loadedShow = await fetchShow(showId)
      const [loadedEntries, loadedParticipants] = await Promise.all([
        fetchEntries(showId), fetchParticipants({ contestId: loadedShow.contestId, includeInactive: true }),
      ])
      setShow(loadedShow); setEntries(loadedEntries); setParticipants(loadedParticipants); setError(null)
    } catch (caught) { setError(asEntryError(caught)); setShow(null) }
  }, [showId])
  useEffect(() => {
    if (!Number.isFinite(showId)) return
    let cancelled = false
    void fetchShow(showId)
      .then(async (loadedShow) => {
        const [loadedEntries, loadedParticipants] = await Promise.all([
          fetchEntries(showId), fetchParticipants({ contestId: loadedShow.contestId, includeInactive: true }),
        ])
        if (!cancelled) { setShow(loadedShow); setEntries(loadedEntries); setParticipants(loadedParticipants); setError(null) }
      })
      .catch((caught: unknown) => { if (!cancelled) { setError(asEntryError(caught)); setShow(null) } })
    return () => { cancelled = true }
  }, [showId])

  const participantsById = useMemo(() => new Map(participants.map((participant) => [participant.id, participant])), [participants])
  const visibleEntries = useMemo(() => entries.filter((entry) => {
    const participant = entry.participantId === null ? null : participantsById.get(entry.participantId)
    const haystack = `${entry.artist} ${entry.title} ${participant?.displayName ?? ''}`.toLocaleLowerCase()
    return (!onlyUnassigned || entry.participantId === null) && haystack.includes(search.toLocaleLowerCase())
  }), [entries, onlyUnassigned, participantsById, search])

  async function saveEntry(draft: EntryDraft) {
    try {
      if (editor === null) await createEntry(showId, draft)
      else if (editor !== undefined) await updateEntry(showId, { ...editor, ...draft, participantId: draft.participantId })
      setEditor(undefined); await load()
    } catch (caught) { setError(asEntryError(caught)) }
  }

  async function remove(entry: ContestEntry) { try { await deleteEntry(showId, entry.id); await load() } catch (caught) { setError(asEntryError(caught)) } }
  async function previewPaste(html: string, text: string) {
    try {
      const lines = await previewHistoricalImport(showId, html, text)
      setPreview(lines.map((line) => ({ ...line, included: line.status !== 'INCOMPLETE', replaceExisting: false })))
    } catch (caught) { setError(asEntryError(caught)) }
  }
  async function importSelected() {
    if (preview === null) return
    const chosen = preview.filter((line) => line.included)
    if (chosen.some((line) => !validPreviewLine(line))) return
    setImporting(true)
    try {
      await importHistoricalEntries(showId, chosen.map((line) => ({ artist: line.artist ?? '', title: line.title ?? '', youtubeUrl: emptyToNull(line.youtubeUrl), comment: null, participantId: line.participantId ?? -1, replaceEntryId: line.replaceExisting ? line.replaceEntryId : null })))
      setPreview(null); await load()
    } catch (caught) { setError(asEntryError(caught)) } finally { setImporting(false) }
  }
  async function confirm() {
    try {
      if (confirmAction === 'complete') await completeHistoricalEntryList(showId)
      if (confirmAction === 'reopen') await reopenHistoricalEntryList(showId)
      setConfirmAction(null); await load()
    } catch (caught) { setError(asEntryError(caught)); setConfirmAction(null) }
  }

  if (show === null) return error === null ? <Typography>Archivshow wird geladen …</Typography> : <ApiErrorNotice error={error.apiError} />
  const complete = show.entryListComplete
  return <Stack spacing={3}>
    <Box><Typography component="h1" variant="h4">Show {show.showNumber} · {show.name}</Typography><Typography color="text.secondary">Historische vollständige Songliste mit Einreichenden. Wertungen, Ranglisten und Ergebnisse gehören nicht zu dieser Ansicht.</Typography></Box>
    {error !== null && <ApiErrorNotice error={error.apiError} />}
    <Paper sx={{ border: 1, borderColor: complete ? 'success.main' : 'divider', p: 2 }}><Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ alignItems: { sm: 'center' }, justifyContent: 'space-between' }}><Box><Typography sx={{ fontWeight: 700 }}>Songliste: {complete ? 'vollständig bestätigt' : 'offen'}</Typography><Typography color="text.secondary" variant="body2">{complete ? 'Korrekturen erfordern ein bewusstes Wiederöffnen.' : 'Alle Einreichenden zuordnen und anschließend bewusst bestätigen.'}</Typography></Box><Button color={complete ? 'warning' : 'success'} onClick={() => setConfirmAction(complete ? 'reopen' : 'complete')} variant="contained">{complete ? 'Songliste wieder öffnen' : 'Vollständigkeit bestätigen'}</Button></Stack></Paper>
    {!complete && <Stack spacing={2}><Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}><Button onClick={() => setEditor(null)} variant="outlined">Beitrag manuell anlegen</Button></Stack><ClipboardImportArea onPasteData={previewPaste} />{preview !== null && <HistoricalImportPreview importing={importing} lines={preview} participants={participants} onCancel={() => setPreview(null)} onChange={setPreview} onImport={importSelected} />}</Stack>}
    <Paper sx={{ p: 2 }}><Stack direction={{ xs: 'column', md: 'row' }} spacing={1}><TextField fullWidth label="Song oder Teilnehmer suchen" onChange={(event) => setSearch(event.target.value)} value={search} /><FormControlLabel control={<Checkbox checked={onlyUnassigned} onChange={(event) => setOnlyUnassigned(event.target.checked)} />} label="Nur ohne Einreichenden" /></Stack></Paper>
    {visibleEntries.length === 0 ? <Alert severity="info">{entries.length === 0 ? 'Noch keine Beiträge erfasst.' : 'Keine Beiträge für diesen Filter.'}</Alert> : <Paper sx={{ overflowX: 'auto' }}><Table aria-label="Historische Songliste"><TableHead><TableRow><TableCell>Interpret</TableCell><TableCell>Titel</TableCell><TableCell>Einreichender</TableCell><TableCell>Land</TableCell><TableCell>Quelle</TableCell>{!complete && <TableCell align="right">Aktionen</TableCell>}</TableRow></TableHead><TableBody>{visibleEntries.map((entry) => { const participant = entry.participantId === null ? null : participantsById.get(entry.participantId); return <TableRow key={entry.id}><TableCell>{entry.artist}</TableCell><TableCell>{entry.title}</TableCell><TableCell>{participant?.displayName ?? <Typography color="warning.main">Unzugeordnet</Typography>}</TableCell><TableCell>{participant?.countryName ?? '—'}</TableCell><TableCell>{entry.youtubeUrl ? <a href={entry.youtubeUrl} rel="noreferrer" target="_blank">Link öffnen</a> : '—'}</TableCell>{!complete && <TableCell align="right"><Button onClick={() => setEditor(entry)}>Bearbeiten</Button><Button color="error" onClick={() => void remove(entry)}>Löschen</Button></TableCell>}</TableRow> })}</TableBody></Table></Paper>}
    <EntryEditor key={editor === null ? 'new' : editor?.id ?? 'closed'} entry={editor} onClose={() => setEditor(undefined)} onSave={saveEntry} participants={participants} />
    <Dialog onClose={() => setConfirmAction(null)} open={confirmAction !== null}><DialogTitle>{confirmAction === 'complete' ? 'Songliste vollständig bestätigen?' : 'Songliste wieder öffnen?'}</DialogTitle><DialogContent><Typography>{confirmAction === 'complete' ? 'Damit bestätigen Sie, dass die vollständige Songliste der Quellmaterialien erfasst ist. Es wird keine Teilnahme für fehlende Teilnehmer erfunden.' : 'Danach sind Korrekturen an den historischen Beiträgen wieder möglich.'}</Typography></DialogContent><DialogActions><Button onClick={() => setConfirmAction(null)}>Abbrechen</Button><Button color={confirmAction === 'complete' ? 'success' : 'warning'} onClick={() => void confirm()} variant="contained">Bewusst bestätigen</Button></DialogActions></Dialog>
  </Stack>
}

function HistoricalImportPreview({ lines, participants, importing, onChange, onCancel, onImport }: { lines: EditablePreviewLine[], participants: Participant[], importing: boolean, onChange: (lines: EditablePreviewLine[]) => void, onCancel: () => void, onImport: () => void }) {
  const selected = lines.filter((line) => line.included)
  const invalid = selected.filter((line) => !validPreviewLine(line))
  const change = (position: number, patch: Partial<EditablePreviewLine>) => onChange(lines.map((line) => line.sourcePosition === position ? { ...line, ...patch } : line))
  return <Paper component="section" sx={{ border: 1, borderColor: 'divider', p: 2 }}><Stack spacing={2}><Box><Typography component="h2" variant="h6">Importvorschau</Typography><Typography color="text.secondary" variant="body2">Die Vorschau speichert nichts. Unbekannte Teilnehmer werden nicht angelegt; sie müssen vor dem Import im Teilnehmerfeld gepflegt oder hier eindeutig gewählt werden.</Typography></Box>{lines.map((line) => <Paper key={line.sourcePosition} sx={{ border: 1, borderColor: line.included && !validPreviewLine(line) ? 'warning.main' : 'divider', p: 2 }} variant="outlined"><Stack spacing={1}><FormControlLabel control={<Checkbox checked={line.included} onChange={(event) => change(line.sourcePosition, { included: event.target.checked })} />} label={`Zeile ${line.sourcePosition} importieren`} /><Typography color="text.secondary" variant="body2">{line.sourceText}</Typography><Stack direction={{ xs: 'column', md: 'row' }} spacing={1}><TextField fullWidth label="Interpret" onChange={(event) => change(line.sourcePosition, { artist: event.target.value })} value={line.artist ?? ''} /><TextField fullWidth label="Titel" onChange={(event) => change(line.sourcePosition, { title: event.target.value })} value={line.title ?? ''} /></Stack><TextField fullWidth label="Quell- oder YouTube-Link (optional)" onChange={(event) => change(line.sourcePosition, { youtubeUrl: event.target.value || null })} value={line.youtubeUrl ?? ''} /><Select displayEmpty onChange={(event) => { const id = String(event.target.value) === '' ? null : Number(event.target.value); const participant = participants.find((item) => item.id === id); change(line.sourcePosition, { participantId: id, participantDisplayName: participant?.displayName ?? null, replaceEntryId: null, replaceExisting: false }) }} value={line.participantId ?? ''}><MenuItem value="">Einreichenden manuell wählen</MenuItem>{participants.map((participant) => <MenuItem key={participant.id} value={participant.id}>{participant.displayName} · {participant.countryName}</MenuItem>)}</Select>{line.replaceEntryId !== null && <FormControlLabel control={<Checkbox checked={line.replaceExisting} onChange={(event) => change(line.sourcePosition, { replaceExisting: event.target.checked })} />} label="Bestehenden Beitrag dieses Teilnehmers bewusst ersetzen" />}{line.warnings.map((warning) => <Alert key={warning.code} severity="warning">{warning.message}</Alert>)}</Stack></Paper>)}{invalid.length > 0 && <Alert severity="warning">{invalid.length} ausgewählte Zeile(n) benötigen noch Interpret, Titel, einen Einreichenden und bei vorhandener Zuordnung eine bewusste Ersatzentscheidung.</Alert>}<Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}><Button disabled={importing} onClick={onCancel}>Vorschau verwerfen</Button><Button disabled={importing || selected.length === 0 || invalid.length > 0} onClick={onImport} variant="contained">{importing ? 'Importiert …' : `${selected.length} Beiträge atomar importieren`}</Button></Stack></Stack></Paper>
}

function EntryEditor({ entry, participants, onClose, onSave }: { entry: ContestEntry | null | undefined, participants: Participant[], onClose: () => void, onSave: (draft: EntryDraft) => void }) {
  const [draft, setDraft] = useState<EntryDraft>(() => entry === null ? emptyDraft : entry === undefined ? emptyDraft : { artist: entry.artist, title: entry.title, youtubeUrl: entry.youtubeUrl ?? '', comment: entry.comment, participantId: entry.participantId })
  return <Dialog fullWidth maxWidth="sm" onClose={onClose} open={entry !== undefined}><DialogTitle>{entry === null ? 'Historischen Beitrag anlegen' : 'Historischen Beitrag bearbeiten'}</DialogTitle><DialogContent><Stack spacing={2} sx={{ pt: 1 }}><TextField label="Interpret" onChange={(event) => setDraft({ ...draft, artist: event.target.value })} value={draft.artist} /><TextField label="Titel" onChange={(event) => setDraft({ ...draft, title: event.target.value })} value={draft.title} /><TextField label="Quell- oder YouTube-Link (optional)" onChange={(event) => setDraft({ ...draft, youtubeUrl: event.target.value })} value={draft.youtubeUrl} /><Select displayEmpty onChange={(event) => setDraft({ ...draft, participantId: String(event.target.value) === '' ? null : Number(event.target.value) })} value={draft.participantId ?? ''}><MenuItem value="">Einreichenden wählen</MenuItem>{participants.map((participant) => <MenuItem key={participant.id} value={participant.id}>{participant.displayName} · {participant.countryName}</MenuItem>)}</Select></Stack></DialogContent><DialogActions><Button onClick={onClose}>Abbrechen</Button><Button disabled={!draft.artist.trim() || !draft.title.trim() || draft.participantId === null} onClick={() => onSave(draft)} variant="contained">Speichern</Button></DialogActions></Dialog>
}

function validPreviewLine(line: EditablePreviewLine) { return !!line.artist?.trim() && !!line.title?.trim() && line.participantId !== null && (line.replaceEntryId === null || line.replaceExisting) }
function emptyToNull(value: string | null) { const trimmed = value?.trim() ?? ''; return trimmed === '' ? null : trimmed }
function asEntryError(error: unknown): EntryApiError { return error instanceof EntryError ? error : new EntryError({ timestamp: new Date().toISOString(), status: 0, code: 'NETWORK_ERROR', message: 'Die historische Songliste konnte nicht verarbeitet werden.', path: '/api/shows' }) }
