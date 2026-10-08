import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, Paper, Stack, Typography } from '@mui/material'
import { useEffect, useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { changeResultClosure, closureLabels, fetchResultClosure, type ResultClosure } from './contestApi'

export function ResultClosurePanel({ showId, revision = 0 }: { showId: number, revision?: number }) {
  // Keyed state also hides a previous show's result before its replacement request finishes.
  const [result, setResult] = useState<ResultClosure | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [refresh, setRefresh] = useState(0)
  const [confirm, setConfirm] = useState<'close' | 'reopen' | null>(null)
  const [saving, setSaving] = useState(false)
  const current = result?.showId === showId ? result : null
  useEffect(() => {
    let cancelled = false
    void fetchResultClosure(showId).then((loaded) => {
      if (!cancelled && loaded.showId === showId) { setResult(loaded); setError(null) }
    }).catch((caught: unknown) => { if (!cancelled) setError(caught instanceof Error ? caught.message : 'Der Ergebnisstatus konnte nicht geladen werden.') })
    return () => { cancelled = true }
  }, [showId, revision, refresh])

  async function change() {
    if (confirm === null) return
    setSaving(true)
    try { setResult(await changeResultClosure(showId, confirm)); setError(null); setConfirm(null) }
    catch (caught) { setError(caught instanceof Error ? caught.message : 'Der Ergebnisabschluss konnte nicht geändert werden.'); setConfirm(null); setRefresh((n) => n + 1) }
    finally { setSaving(false) }
  }
  return <Paper component="section" sx={{ p: 2 }}>
    <Stack spacing={1.5}>
      <Typography component="h2" variant="h6">Showergebnis · {current ? closureLabels[current.status] : 'Wird geladen …'}</Typography>
      {error && <Alert severity="error">{error}</Alert>}
      {current?.closedAt && <Typography color="text.secondary">Abgeschlossen am {new Date(current.closedAt).toLocaleString('de-DE')}. Diese Show zählt in der Gesamtwertung.</Typography>}
      {current && !current.closedAt && <Typography color="text.secondary">Diese Show zählt erst nach bewusstem Ergebnisabschluss in der Gesamtwertung. Deine eigenen Top 15 bleiben davon unabhängig.</Typography>}
      {current && current.reasons.length > 0 && <Alert severity="info"><ul>{current.reasons.map((reason) => <li key={reason}>{reason}</li>)}</ul></Alert>}
      <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1 }}>
        <Button disabled={!current || current.status === 'IN_PROGRESS' || saving} variant="contained" color={current?.closedAt ? 'warning' : 'success'} onClick={() => setConfirm(current?.closedAt ? 'reopen' : 'close')}>
          {current?.closedAt ? 'Show wieder öffnen' : 'Showergebnis abschließen'}
        </Button>
        <Button disabled={saving} onClick={() => setRefresh((n) => n + 1)}>Status aktualisieren</Button>
        <Button component={RouterLink} to="/standings">Zur Gesamtwertung</Button>
      </Stack>
    </Stack>
    <Dialog open={confirm !== null} onClose={() => !saving && setConfirm(null)}>
      <DialogTitle>{confirm === 'reopen' ? 'Showergebnis wieder öffnen?' : 'Showergebnis abschließen?'}</DialogTitle>
      <DialogContent><Typography>{confirm === 'reopen'
        ? 'Diese Show entfällt sofort aus Gesamtwertung und Verlauf. Nach zulässigen Korrekturen musst du sie erneut abschließen. Persönliche Top-15-Snapshots und die bestehenden Songlistensperren bleiben erhalten.'
        : 'Der Server prüft Songliste, alle Contest-Teilnahmen und die vollständigen Stimmzettel erneut. Danach zählt die Show in der Gesamtwertung; Änderungen ihrer Wertungsgrundlagen erfordern eine bewusste Wiederöffnung.'}</Typography></DialogContent>
      <DialogActions><Button disabled={saving} onClick={() => setConfirm(null)}>Abbrechen</Button><Button disabled={saving} onClick={() => void change()} variant="contained">Bewusst bestätigen</Button></DialogActions>
    </Dialog>
  </Paper>
}
