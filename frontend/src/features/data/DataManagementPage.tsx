import { useEffect, useRef, useState } from 'react'
import {
  Alert, Box, Button, Card, CardActions, CardContent, Checkbox, Chip, Dialog, DialogActions, DialogContent,
  DialogTitle, Divider, FormControl, FormControlLabel, FormLabel, List, ListItem, ListItemText, MenuItem, Radio,
  RadioGroup, Select, Skeleton, Stack, Table, TableBody, TableCell, TableHead, TableRow, Typography,
} from '@mui/material'
import { ApiErrorNotice } from '../../components/ApiErrorNotice'
import {
  confirmRestore, createAnalysisExport, createManualBackup, fetchDataOverview, previewAnalysisExport, previewBackup, previewUpload,
  DataApiError, type AnalysisExportPreview, type BackupOverview, type BackupSummary, type RestorePreview,
} from './api'
import { fetchContests, type Contest } from '../contests/api'
import { fetchShows, type MottoShow } from '../shows/api'

function displayTime(value: string) {
  return new Intl.DateTimeFormat('de-DE', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function bytes(value: number) {
  return new Intl.NumberFormat('de-DE', { style: 'unit', unit: 'byte', unitDisplay: 'narrow' }).format(value)
}

export function DataManagementPage() {
  const [overview, setOverview] = useState<BackupOverview | null>(null)
  const [error, setError] = useState<DataApiError | null>(null)
  const [creating, setCreating] = useState(false)
  const [preview, setPreview] = useState<RestorePreview | null>(null)
  const [preparing, setPreparing] = useState(false)
  const [restoring, setRestoring] = useState(false)
  const [success, setSuccess] = useState<string | null>(null)
  const [analysisContests, setAnalysisContests] = useState<Contest[]>([])
  const [analysisShows, setAnalysisShows] = useState<MottoShow[]>([])
  const [analysisScope, setAnalysisScope] = useState<'FULL_ARCHIVE' | 'SELECTED'>('FULL_ARCHIVE')
  const [selectedContestIds, setSelectedContestIds] = useState<number[]>([])
  const [selectedShowIds, setSelectedShowIds] = useState<number[]>([])
  const [includeCandidates, setIncludeCandidates] = useState(false)
  const [candidateShowId, setCandidateShowId] = useState<number | null>(null)
  const [analysisPreview, setAnalysisPreview] = useState<AnalysisExportPreview | null>(null)
  const [preparingAnalysis, setPreparingAnalysis] = useState(false)
  const [creatingAnalysis, setCreatingAnalysis] = useState(false)
  const [analysisDownload, setAnalysisDownload] = useState<{ filename: string, href: string } | null>(null)
  const fileInput = useRef<HTMLInputElement>(null)

  const load = async () => {
    setError(null)
    try { setOverview(await fetchDataOverview()) } catch (caught) { setError(asDataError(caught)); setOverview(null) }
  }
  useEffect(() => {
    let disposed = false
    void fetchDataOverview().then((loaded) => {
      if (disposed) return
      setError(null)
      setOverview(loaded)
    }).catch((caught: unknown) => {
      if (disposed) return
      setError(asDataError(caught))
      setOverview(null)
    })
    return () => { disposed = true }
  }, [])
  useEffect(() => {
    let disposed = false
    void Promise.all([fetchContests(), fetchShows()]).then(([contests, shows]) => {
      if (disposed) return
      setAnalysisContests(contests)
      setAnalysisShows(shows)
      const firstCurrentShow = shows.find((show) => contests.some((contest) => contest.id === show.contestId && contest.current))
      if (firstCurrentShow !== undefined) setCandidateShowId(firstCurrentShow.id)
    }).catch((caught: unknown) => {
      if (!disposed) setError(asDataError(caught))
    })
    return () => { disposed = true }
  }, [])

  const createBackup = async () => {
    setCreating(true); setError(null); setSuccess(null)
    try { await createManualBackup(); await load(); setSuccess('Die manuelle Sicherung wurde erfolgreich erstellt.') } catch (caught) { setError(asDataError(caught)) } finally { setCreating(false) }
  }
  const prepareKnownBackup = async (backup: BackupSummary) => {
    setPreparing(true); setError(null); setSuccess(null)
    try { setPreview(await previewBackup(backup.id)) } catch (caught) { setError(asDataError(caught)) } finally { setPreparing(false) }
  }
  const onUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (file === undefined) return
    setPreparing(true); setError(null); setSuccess(null)
    try { setPreview(await previewUpload(file)) } catch (caught) { setError(asDataError(caught)) } finally { setPreparing(false); event.target.value = '' }
  }
  const restore = async () => {
    if (preview === null) return
    setRestoring(true); setError(null)
    try {
      const result = await confirmRestore(preview.token)
      setPreview(null); setSuccess(`${result.message} Eine Sicherheitskopie wurde erstellt.`); await load()
    } catch (caught) { setError(asDataError(caught)) } finally { setRestoring(false) }
  }
  const exportRequest = () => ({
    contestIds: analysisScope === 'FULL_ARCHIVE' ? [] : selectedContestIds,
    showIds: analysisScope === 'FULL_ARCHIVE' ? [] : selectedShowIds,
    candidateShowId: includeCandidates ? candidateShowId : null,
  })
  const previewAnalysis = async () => {
    setPreparingAnalysis(true); setError(null); setSuccess(null); setAnalysisDownload(null)
    try { setAnalysisPreview(await previewAnalysisExport(exportRequest())) } catch (caught) { setError(asDataError(caught)) } finally { setPreparingAnalysis(false) }
  }
  const createAnalysis = async () => {
    setCreatingAnalysis(true); setError(null); setSuccess(null)
    try {
      const result = await createAnalysisExport(exportRequest())
      setAnalysisPreview(result.preview)
      setAnalysisDownload({ filename: result.filename, href: result.downloadUrl })
      setSuccess('Das Analysepaket wurde vollstaendig erstellt und kann jetzt heruntergeladen werden.')
    } catch (caught) { setError(asDataError(caught)) } finally { setCreatingAnalysis(false) }
  }
  const toggle = (ids: number[], id: number) => ids.includes(id) ? ids.filter((value) => value !== id) : [...ids, id]
  const currentCandidateShows = analysisShows.filter((show) => analysisContests.some((contest) => contest.id === show.contestId && contest.current))

  return (
    <Stack spacing={3}>
      <Box>
        <Typography component="h1" variant="h4">Daten und Sicherungen</Typography>
        <Typography color="text.secondary" sx={{ mt: 1 }}>Sicherungen werden als geprüfte lokale SQLite-Snapshots gespeichert. Eine Wiederherstellung ersetzt den aktuellen Stand erst nach einer Vorschau und ausdrücklichen Bestätigung.</Typography>
      </Box>
      {error !== null && <ApiErrorNotice error={error.apiError} />}
      {success !== null && <Alert severity="success">{success}</Alert>}
      {overview === null && error === null && <DataLoading />}
      {overview !== null && <>
        <Card><CardContent><Typography variant="h6">Speicherorte</Typography><List dense>
          <ListItem><ListItemText primary="Datenbank" secondary={overview.databaseLocation} /></ListItem>
          <ListItem><ListItemText primary="Automatische Sicherungen" secondary={overview.automaticBackupsLocation} /></ListItem>
          <ListItem><ListItemText primary="Manuelle Sicherungen und Restore-Sicherheitskopien" secondary={overview.manualBackupsLocation} /></ListItem>
          <ListItem><ListItemText primary="Exporte" secondary={overview.exportsLocation} /></ListItem>
        </List><Typography color="text.secondary" variant="body2">Letzte Sicherung: {overview.lastBackup === null ? 'noch keine' : `${displayTime(overview.lastBackup.createdAt)} (${reason(overview.lastBackup.reason)})`}</Typography></CardContent>
          <CardActions><Button disabled={creating} onClick={() => void createBackup()} variant="contained">{creating ? 'Sicherung wird erstellt …' : 'Manuelle Sicherung erstellen'}</Button></CardActions></Card>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
          <ExportCard title="Vollständiger JSON-Export" detail="Alle fachlichen Daten, Beziehungen und historischen Snapshots." href="/api/data/export/full" />
          <ExportCard title="Kandidaten" detail="CSV mit Showbezug, Status und Reihenfolge." href="/api/data/export/candidates.csv" />
          <ExportCard title="Wettbewerbsbeiträge" detail="CSV mit Einschätzung, Sicherheit, Rang und Zuordnung." href="/api/data/export/contest-entries.csv" />
          <ExportCard title="Teilnehmer" detail="CSV mit Land, Aktivstatus und Aliasnamen." href="/api/data/export/participants.csv" />
          <ExportCard title="Eigene Ergebnisse" detail="Aus vollständigen Einzelwertungen abgeleitete Zustände, Ränge und Punkte." href="/api/data/export/results.csv" />
          <ExportCard title="Legacy-Ergebnisse" detail="Getrennter Read-only-Export alter isolierter Punktwerte und offizieller Angaben." href="/api/data/export/legacy-results.csv" />
          <ExportCard title="Einzelwertungen" detail="CSV im Long-Format mit Stimmzettelstatus, Rang und abgeleiteten Punkten." href="/api/data/export/published-ballots.csv" />
          <ExportCard title="Tippspiel" detail="CSV mit Tipp, tatsächlicher Zuordnung, Sicherheit, Notiz und Trefferstatus." href="/api/data/export/tips-game.csv" />
        </Stack>
        <AnalysisExportCard
          candidateShowId={candidateShowId}
          candidateShows={currentCandidateShows}
          creating={creatingAnalysis}
          download={analysisDownload}
          includeCandidates={includeCandidates}
          onCandidateShowChange={setCandidateShowId}
          onCreate={() => void createAnalysis()}
          onIncludeCandidatesChange={setIncludeCandidates}
          onPreview={() => void previewAnalysis()}
          onScopeChange={setAnalysisScope}
          onToggleContest={(id) => setSelectedContestIds(toggle(selectedContestIds, id))}
          onToggleShow={(id) => setSelectedShowIds(toggle(selectedShowIds, id))}
          preview={analysisPreview}
          preparing={preparingAnalysis}
          scope={analysisScope}
          selectedContestIds={selectedContestIds}
          selectedShowIds={selectedShowIds}
          contests={analysisContests}
          shows={analysisShows}
        />
        <Card><CardContent><Typography variant="h6">Wiederherstellung vorbereiten</Typography><Typography color="text.secondary" sx={{ mt: 1 }}>Wählen Sie eine vorhandene Sicherung oder eine kompatible .cscbackup- beziehungsweise JSON-Exportdatei. Die aktuelle Datenbank bleibt bis zur Bestätigung unverändert.</Typography></CardContent>
          <CardActions><Button disabled={preparing} onClick={() => fileInput.current?.click()}>{preparing ? 'Datei wird geprüft …' : 'Datei prüfen'}</Button><input accept=".cscbackup,.json,application/json" aria-label="Wiederherstellungsdatei" hidden onChange={(event) => void onUpload(event)} ref={fileInput} type="file" /></CardActions></Card>
        <BackupTable backups={overview.automaticBackups} onRestore={prepareKnownBackup} preparing={preparing} title="Automatische Sicherungen" />
        <BackupTable backups={overview.manualBackups} onRestore={prepareKnownBackup} preparing={preparing} title="Manuelle Sicherungen und Restore-Sicherheitskopien" />
      </>}
      <RestoreDialog preview={preview} restoring={restoring} onClose={() => !restoring && setPreview(null)} onRestore={() => void restore()} />
    </Stack>
  )
}

function BackupTable({ backups, title, onRestore, preparing }: { backups: BackupSummary[], title: string, onRestore: (backup: BackupSummary) => void, preparing: boolean }) {
  return <Card><CardContent><Typography variant="h6">{title}</Typography>{backups.length === 0 ? <Typography color="text.secondary" sx={{ mt: 1 }}>Noch keine Sicherungen vorhanden.</Typography> : <Table aria-label={title} size="small"><TableHead><TableRow><TableCell>Zeitpunkt</TableCell><TableCell>Grund</TableCell><TableCell>Schema</TableCell><TableCell>Größe</TableCell><TableCell /></TableRow></TableHead><TableBody>{backups.map((backup) => <TableRow key={backup.id}><TableCell>{displayTime(backup.createdAt)}</TableCell><TableCell>{reason(backup.reason)}</TableCell><TableCell>{backup.schemaVersion}</TableCell><TableCell>{bytes(backup.sizeBytes)}</TableCell><TableCell><Stack direction="row" spacing={1}><Button component="a" href={`/api/data/backups/${encodeURIComponent(backup.id)}/download`} size="small">Download</Button><Button disabled={preparing} onClick={() => onRestore(backup)} size="small">Wiederherstellen</Button></Stack></TableCell></TableRow>)}</TableBody></Table>}</CardContent></Card>
}

function ExportCard({ title, detail, href }: { title: string, detail: string, href: string }) {
  return <Card sx={{ flex: 1, minWidth: 220 }}><CardContent><Typography variant="h6">{title}</Typography><Typography color="text.secondary" sx={{ mt: 1 }} variant="body2">{detail}</Typography></CardContent><CardActions><Button component="a" href={href}>Herunterladen</Button></CardActions></Card>
}

type AnalysisExportCardProps = {
  contests: Contest[]
  shows: MottoShow[]
  candidateShows: MottoShow[]
  scope: 'FULL_ARCHIVE' | 'SELECTED'
  selectedContestIds: number[]
  selectedShowIds: number[]
  includeCandidates: boolean
  candidateShowId: number | null
  preview: AnalysisExportPreview | null
  preparing: boolean
  creating: boolean
  download: { filename: string, href: string } | null
  onScopeChange: (scope: 'FULL_ARCHIVE' | 'SELECTED') => void
  onToggleContest: (id: number) => void
  onToggleShow: (id: number) => void
  onIncludeCandidatesChange: (value: boolean) => void
  onCandidateShowChange: (id: number | null) => void
  onPreview: () => void
  onCreate: () => void
}

function AnalysisExportCard(props: AnalysisExportCardProps) {
  const selectionMissing = props.scope === 'SELECTED' && props.selectedContestIds.length === 0 && props.selectedShowIds.length === 0
  return <Card><CardContent><Typography variant="h6">Analyseexport</Typography><Typography color="text.secondary" sx={{ mt: 1 }}>Erstellt ein eigenstaendiges, versioniertes ZIP-Paket mit JSON, Markdown und CSV. Es ist kein Wiederherstellungsformat und enthaelt weder Ausschlussliste noch KI- oder Drive-Anbindung.</Typography>
    <Alert severity="info" sx={{ mt: 2 }}>Ausserhalb der Top 15, eigene Einreichung, nicht abgegebener Stimmzettel und unbekannter Stimmzettel bleiben im Paket getrennte Zustaende.</Alert>
    <FormControl sx={{ mt: 2 }}><FormLabel id="analysis-scope-label">Archivumfang</FormLabel><RadioGroup aria-labelledby="analysis-scope-label" onChange={(event) => props.onScopeChange(event.target.value as 'FULL_ARCHIVE' | 'SELECTED')} value={props.scope}>
      <FormControlLabel control={<Radio />} label="Vollstaendiges Archiv" value="FULL_ARCHIVE" />
      <FormControlLabel control={<Radio />} label="Ausgewaehlte CSC-Ausgaben und/oder Shows" value="SELECTED" />
    </RadioGroup></FormControl>
    {props.scope === 'SELECTED' && <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ mt: 1 }}>
      <Box sx={{ minWidth: 240 }}><Typography variant="subtitle2">CSC-Ausgaben</Typography>{props.contests.map((contest) => <FormControlLabel key={contest.id} control={<Checkbox checked={props.selectedContestIds.includes(contest.id)} onChange={() => props.onToggleContest(contest.id)} />} label={contest.name} />)}</Box>
      <Box><Typography variant="subtitle2">Mottoshows</Typography>{props.shows.map((show) => <FormControlLabel key={show.id} control={<Checkbox checked={props.selectedShowIds.includes(show.id)} onChange={() => props.onToggleShow(show.id)} />} label={`${contestName(props.contests, show.contestId)} - ${show.showNumber}: ${show.name}`} />)}</Box>
    </Stack>}
    <FormControlLabel sx={{ display: 'block', mt: 2 }} control={<Checkbox checked={props.includeCandidates} disabled={props.candidateShows.length === 0} onChange={(event) => props.onIncludeCandidatesChange(event.target.checked)} />} label="Kandidaten einer aktuellen Show getrennt als Prognosegegenstand einbeziehen" />
    {props.includeCandidates && <FormControl fullWidth size="small" sx={{ maxWidth: 440 }}><FormLabel id="candidate-show-label">Aktuelle Kandidaten-Show</FormLabel><Select aria-labelledby="candidate-show-label" onChange={(event) => props.onCandidateShowChange(Number(event.target.value))} value={props.candidateShowId ?? ''}>{props.candidateShows.map((show) => <MenuItem key={show.id} value={show.id}>{`${show.showNumber}: ${show.name}`}</MenuItem>)}</Select></FormControl>}
    {selectionMissing && <Typography color="warning.main" sx={{ mt: 1 }} variant="body2">Bitte mindestens eine CSC-Ausgabe oder Mottoshow auswaehlen.</Typography>}
    {props.preview !== null && <Stack aria-label="Analyseexport-Vorschau" direction="row" spacing={1} sx={{ flexWrap: 'wrap', mt: 2 }}><Chip label={`${props.preview.participants} Teilnehmer`} /><Chip label={`${props.preview.participations} Teilnahmen`} /><Chip label={`${props.preview.shows} Shows`} /><Chip label={`${props.preview.entries} Einreichungen`} /><Chip label={`${props.preview.votedBallots} abgestimmt`} /><Chip label={`${props.preview.noBallots} nicht abgestimmt`} /><Chip label={`${props.preview.unknownBallots} unerfasst`} /><Chip label={`${props.preview.candidates} Kandidaten`} /></Stack>}
  </CardContent><CardActions><Button disabled={props.preparing || selectionMissing || (props.includeCandidates && props.candidateShowId === null)} onClick={props.onPreview}>{props.preparing ? 'Vorschau wird erstellt ...' : 'Umfang vorschauen'}</Button><Button disabled={props.creating || selectionMissing || (props.includeCandidates && props.candidateShowId === null)} onClick={props.onCreate} variant="contained">{props.creating ? 'Paket wird erstellt ...' : 'Analysepaket erstellen'}</Button>{props.download !== null && <Button component="a" href={props.download.href}>{props.download.filename} herunterladen</Button>}</CardActions></Card>
}

function contestName(contests: Contest[], contestId: number) { return contests.find((contest) => contest.id === contestId)?.name ?? `CSC ${contestId}` }

function RestoreDialog({ preview, restoring, onClose, onRestore }: { preview: RestorePreview | null, restoring: boolean, onClose: () => void, onRestore: () => void }) {
  return <Dialog fullWidth maxWidth="sm" onClose={onClose} open={preview !== null}><DialogTitle>Wiederherstellung bestätigen</DialogTitle>{preview !== null && <><DialogContent dividers><Stack spacing={2}><Alert severity="warning">Die Wiederherstellung ersetzt den aktuellen Datenstand. Unmittelbar davor wird automatisch eine separate Sicherheitskopie erstellt.</Alert><Typography><strong>{preview.sourceType}:</strong> {preview.sourceName}</Typography><Typography color="text.secondary">Erstellt: {displayTime(preview.createdAt)} · Anwendung: {preview.applicationVersion} · Schema: {preview.schemaVersion}</Typography><Divider /><Stack direction="row" sx={{ flexWrap: 'wrap', '& > *': { mb: 1, mr: 1 } }}><Chip label={`${preview.counts.mottoShows} Shows`} /><Chip label={`${preview.counts.candidates} Kandidaten`} /><Chip label={`${preview.counts.participants} Teilnehmer`} /><Chip label={`${preview.counts.contestEntries} Beiträge`} /><Chip label={`${preview.counts.botbSelections} BOTB-Auswahlen`} /><Chip label={`${preview.counts.ballotSnapshots} Snapshots`} /><Chip label={`${preview.counts.legacyReceivedScores} Legacy-Wertungen`} /></Stack></Stack></DialogContent><DialogActions><Button disabled={restoring} onClick={onClose}>Abbrechen</Button><Button color="warning" disabled={restoring || !preview.compatible} onClick={onRestore} variant="contained">{restoring ? 'Wiederherstellung läuft …' : 'Daten endgültig wiederherstellen'}</Button></DialogActions></>}</Dialog>
}

function DataLoading() { return <Stack aria-label="Sicherungen werden geladen" spacing={1}><Skeleton height={56} /><Skeleton height={180} /><Skeleton height={180} /></Stack> }
function reason(value: BackupSummary['reason']) { return ({ STARTUP: 'Anwendungsstart', PRE_MIGRATION: 'Vor Migration', MANUAL: 'Manuell', PRE_RESTORE: 'Vor Wiederherstellung' })[value] }
function asDataError(error: unknown) { return error instanceof DataApiError ? error : new DataApiError({ timestamp: new Date().toISOString(), status: 0, code: 'NETWORK_ERROR', message: 'Die Datenoperation konnte nicht erreicht werden.', path: '/api/data' }) }
