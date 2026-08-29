import { useEffect, useRef, useState } from 'react'
import {
  Alert, Box, Button, Card, CardActions, CardContent, Chip, Dialog, DialogActions, DialogContent,
  DialogTitle, Divider, List, ListItem, ListItemText, Skeleton, Stack, Table, TableBody,
  TableCell, TableHead, TableRow, Typography,
} from '@mui/material'
import { ApiErrorNotice } from '../../components/ApiErrorNotice'
import {
  confirmRestore, createManualBackup, fetchDataOverview, previewBackup, previewUpload,
  DataApiError, type BackupOverview, type BackupSummary, type RestorePreview,
} from './api'

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
          <ExportCard title="Ergebnisse" detail="CSV mit Abstimmungsstatus, Punkten und Abschlussdaten." href="/api/data/export/results.csv" />
        </Stack>
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

function RestoreDialog({ preview, restoring, onClose, onRestore }: { preview: RestorePreview | null, restoring: boolean, onClose: () => void, onRestore: () => void }) {
  return <Dialog fullWidth maxWidth="sm" onClose={onClose} open={preview !== null}><DialogTitle>Wiederherstellung bestätigen</DialogTitle>{preview !== null && <><DialogContent dividers><Stack spacing={2}><Alert severity="warning">Die Wiederherstellung ersetzt den aktuellen Datenstand. Unmittelbar davor wird automatisch eine separate Sicherheitskopie erstellt.</Alert><Typography><strong>{preview.sourceType}:</strong> {preview.sourceName}</Typography><Typography color="text.secondary">Erstellt: {displayTime(preview.createdAt)} · Anwendung: {preview.applicationVersion} · Schema: {preview.schemaVersion}</Typography><Divider /><Stack direction="row" sx={{ flexWrap: 'wrap', '& > *': { mb: 1, mr: 1 } }}><Chip label={`${preview.counts.mottoShows} Shows`} /><Chip label={`${preview.counts.candidates} Kandidaten`} /><Chip label={`${preview.counts.participants} Teilnehmer`} /><Chip label={`${preview.counts.contestEntries} Beiträge`} /><Chip label={`${preview.counts.ballotSnapshots} Snapshots`} /><Chip label={`${preview.counts.receivedScores} Wertungen`} /></Stack></Stack></DialogContent><DialogActions><Button disabled={restoring} onClick={onClose}>Abbrechen</Button><Button color="warning" disabled={restoring || !preview.compatible} onClick={onRestore} variant="contained">{restoring ? 'Wiederherstellung läuft …' : 'Daten endgültig wiederherstellen'}</Button></DialogActions></>}</Dialog>
}

function DataLoading() { return <Stack aria-label="Sicherungen werden geladen" spacing={1}><Skeleton height={56} /><Skeleton height={180} /><Skeleton height={180} /></Stack> }
function reason(value: BackupSummary['reason']) { return ({ STARTUP: 'Anwendungsstart', PRE_MIGRATION: 'Vor Migration', MANUAL: 'Manuell', PRE_RESTORE: 'Vor Wiederherstellung' })[value] }
function asDataError(error: unknown) { return error instanceof DataApiError ? error : new DataApiError({ timestamp: new Date().toISOString(), status: 0, code: 'NETWORK_ERROR', message: 'Die Datenoperation konnte nicht erreicht werden.', path: '/api/data' }) }
