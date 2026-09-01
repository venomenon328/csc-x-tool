import { Alert, Button, Paper, Stack, Typography } from '@mui/material'
import { useState } from 'react'
import { apiFetch } from '../../api/request'

export function TipsExportPanel({ showId, ready }: { showId: number, ready: boolean }) {
  const [copying, setCopying] = useState(false)
  const [notice, setNotice] = useState<{ severity: 'success' | 'error', message: string } | null>(null)
  const exportPath = `/api/shows/${showId}/tips/export`

  async function copyExport() {
    setCopying(true)
    setNotice(null)
    try {
      const response = await apiFetch(exportPath)
      if (!response.ok) throw new Error('Export nicht verfügbar')
      const text = await response.text()
      await navigator.clipboard.writeText(text)
      setNotice({ severity: 'success', message: 'Die alphabetische Tippspiel-Zuordnung wurde in die Zwischenablage kopiert.' })
    } catch {
      setNotice({ severity: 'error', message: 'Der Tippspiel-Export ist noch nicht verfügbar oder konnte nicht kopiert werden.' })
    } finally {
      setCopying(false)
    }
  }

  return <Paper component="section" sx={{ p: 2 }}>
    <Stack spacing={1.25}>
      <BoxHeader />
      {!ready
        ? <Alert severity="info">Sobald alle tippbaren Beiträge gespeichert zugeordnet sind, kannst du die vollständige alphabetische Abgabeliste exportieren.</Alert>
        : <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
          <Button disabled={copying} onClick={() => void copyExport()} variant="contained">Zuordnungen kopieren</Button>
          <Button component="a" download="tippspiel-zuordnungen.txt" href={exportPath} variant="outlined">Textdatei herunterladen</Button>
        </Stack>}
      {notice !== null && <Alert severity={notice.severity} onClose={() => setNotice(null)}>{notice.message}</Alert>}
    </Stack>
  </Paper>
}

function BoxHeader() {
  return <Stack spacing={0.25}>
    <Typography component="h2" variant="h6">Tippspiel-Abgabeexport</Typography>
    <Typography color="text.secondary" variant="body2">Format: Interpret - Titel [Land/User] · alphabetisch nach Interpret.</Typography>
  </Stack>
}
