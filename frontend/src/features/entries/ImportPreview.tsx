import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  FormControlLabel,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import type { ImportPreviewLine } from './api'

export type EditableImportLine = ImportPreviewLine & { included: boolean }

function hasImportFields(line: EditableImportLine) {
  return line.artist?.trim() !== '' && line.title?.trim() !== ''
    && /^https?:\/\/(?:www\.)?(?:youtube\.com|m\.youtube\.com|music\.youtube\.com|youtu\.be)\//i.test(line.youtubeUrl?.trim() ?? '')
}

export function ImportPreview({ lines, importing, onChange, onCancel, onImport }: {
  lines: EditableImportLine[]
  importing: boolean
  onChange: (lines: EditableImportLine[]) => void
  onCancel: () => void
  onImport: () => void
}) {
  const selected = lines.filter((line) => line.included)
  const invalidSelected = selected.filter((line) => !hasImportFields(line))

  function updateLine(sourcePosition: number, patch: Partial<EditableImportLine>) {
    onChange(lines.map((line) => line.sourcePosition === sourcePosition ? { ...line, ...patch } : line))
  }

  return (
    <Paper component="section" aria-label="Importvorschau" sx={{ border: 1, borderColor: 'divider', p: 2 }}>
      <Stack spacing={2}>
        <Box>
          <Typography component="h2" variant="h6">Importvorschau</Typography>
          <Typography color="text.secondary" variant="body2">
            Nur ausgewählte und vollständige Zeilen werden gemeinsam importiert. Warnungen dürfen bewusst übernommen werden.
          </Typography>
        </Box>
        {lines.map((line) => (
          <Paper key={line.sourcePosition} sx={{ border: 1, borderColor: line.included && !hasImportFields(line) ? 'warning.main' : 'divider', p: 2 }} variant="outlined">
            <Stack spacing={1.25}>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center', justifyContent: 'space-between' }}>
                <FormControlLabel
                  control={<Checkbox checked={line.included} onChange={(event) => updateLine(line.sourcePosition, { included: event.target.checked })} />}
                  label={`Zeile ${line.sourcePosition} importieren`}
                />
                <Chip color={line.status === 'READY' ? 'success' : line.status === 'INCOMPLETE' ? 'warning' : 'default'} label={line.status} size="small" />
              </Stack>
              <Typography color="text.secondary" variant="body2">
                {line.sourceType}: {line.sourceText || 'Keine sichtbare Beschriftung'}
              </Typography>
              <Stack direction={{ xs: 'column', md: 'row' }} spacing={1}>
                <TextField fullWidth label="Interpret" onChange={(event) => updateLine(line.sourcePosition, { artist: event.target.value })} value={line.artist ?? ''} />
                <TextField fullWidth label="Titel" onChange={(event) => updateLine(line.sourcePosition, { title: event.target.value })} value={line.title ?? ''} />
              </Stack>
              <TextField fullWidth label="YouTube-Link" onChange={(event) => updateLine(line.sourcePosition, { youtubeUrl: event.target.value })} value={line.youtubeUrl ?? ''} />
              {line.warnings.map((warning) => <Alert key={warning.code} severity="warning">{warning.message}</Alert>)}
            </Stack>
          </Paper>
        ))}
        {invalidSelected.length > 0 && <Alert severity="warning">{invalidSelected.length} ausgewählte Zeile(n) sind noch unvollständig oder besitzen keinen unterstützten YouTube-Link.</Alert>}
        <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
          <Button disabled={importing} onClick={onCancel}>Vorschau verwerfen</Button>
          <Button disabled={importing || selected.length === 0 || invalidSelected.length > 0} onClick={onImport} variant="contained">
            {importing ? 'Importiert …' : `${selected.length} Beitrag${selected.length === 1 ? '' : 'e'} importieren`}
          </Button>
        </Stack>
      </Stack>
    </Paper>
  )
}
