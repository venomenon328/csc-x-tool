import { Alert, Button, Checkbox, FormControlLabel, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material'
import type { Participant } from '../participants/api'
import type { AssignmentImportPreviewLine, ContestEntry } from './api'
import { ClipboardImportArea } from './ClipboardImportArea'

export type EditableAssignmentLine = AssignmentImportPreviewLine & {
  included: boolean
  warningsReviewed: boolean
  confirmReplacement: boolean
}

export function AssignmentImportPanel({ entries, participants, ownParticipationId = null, lines, busy, onPaste, onChange, onCancel, onImport }: {
  entries: ContestEntry[]
  participants: Participant[]
  ownParticipationId?: number | null
  lines: EditableAssignmentLine[] | null
  busy: boolean
  onPaste: (html: string, text: string) => Promise<void>
  onChange: (lines: EditableAssignmentLine[]) => void
  onCancel: () => void
  onImport: () => void
}) {
  const selected = lines?.filter((line) => line.included) ?? []
  const finalAssignments = new Map(entries.map((entry) => [entry.id, entry.contestParticipationId ?? null]))
  selected.forEach((line) => { if (line.entryId != null) finalAssignments.set(line.entryId, line.participationId) })
  const assigned = [...finalAssignments.values()].filter((id): id is number => id != null)
  const duplicate = new Set(assigned).size !== assigned.length || new Set(selected.map((line) => line.entryId)).size !== selected.length
  const invalid = selected.some((line) => {
    const entry = entries.find((item) => item.id === line.entryId)
    const participant = participants.find((item) => item.participationId === line.participationId)
    return !entry || !participant || (!participant.active && entry.contestParticipationId !== participant.participationId)
      || (entry.ownEntry === true && entry.contestParticipationId !== participant.participationId)
      || (participant.participationId === ownParticipationId && entry.contestParticipationId !== participant.participationId)
      || (line.warnings.length > 0 && !line.warningsReviewed)
      || (entry.contestParticipationId != null && entry.contestParticipationId !== line.participationId && !line.confirmReplacement)
  })

  function change(position: number, patch: Partial<EditableAssignmentLine>) {
    onChange((lines ?? []).map((line) => line.sourcePosition === position ? { ...line, ...patch } : line))
  }

  return <Paper aria-label="Einreichende zuordnen" component="section" sx={{ border: 1, borderColor: 'secondary.main', p: 2 }}>
    <Stack spacing={2}>
      <Typography component="h2" variant="h5">Einreichende zuordnen</Typography>
      <Typography>{entries.filter((entry) => entry.participantId != null).length} / {entries.length} Beiträge zugeordnet</Typography>
      <Typography color="text.secondary" variant="body2">Veröffentlichten Block einfügen oder manuell: Beitrag auswählen → Teilnehmer zuordnen. Der Import ändert nur Zuordnungen vorhandener Beiträge.</Typography>
      <ClipboardImportArea assignmentMode onPasteData={onPaste} />
      {lines !== null && <Stack aria-label="Zuordnungsvorschau" spacing={2}>
        <Typography component="h3" variant="h6">Vorschau</Typography>
        {lines.length === 0 && <Alert severity="warning">Keine Zuordnungszeile erkannt. Bitte den kopierten Block prüfen.</Alert>}
        {lines.map((line) => {
          const entry = entries.find((item) => item.id === line.entryId)
          const previous = participants.find((item) => item.participationId === entry?.contestParticipationId)
          const next = participants.find((item) => item.participationId === line.participationId)
          const action = !entry || !next ? 'Nacharbeit erforderlich' : entry.contestParticipationId == null
            ? 'Neu zuordnen' : entry.contestParticipationId === line.participationId ? 'Unverändert' : 'Bestehende Zuordnung ersetzen'
          const ready = entry != null && next != null
            && (next.active || entry.contestParticipationId === next.participationId)
            && (entry.ownEntry !== true || entry.contestParticipationId === next.participationId)
            && (next.participationId !== ownParticipationId || entry.contestParticipationId === next.participationId)
            && (line.warnings.length === 0 || line.warningsReviewed)
            && (entry.contestParticipationId == null || entry.contestParticipationId === next.participationId || line.confirmReplacement)
          return <Paper key={line.sourcePosition} sx={{ border: 1, borderColor: ready ? 'divider' : 'warning.main', p: 2 }} variant="outlined">
            <Stack spacing={1.5}>
              <Typography variant="subtitle2">Quellzeile {line.sourcePosition}: {line.sourceText}</Typography>
              <Typography variant="body2">Erkannt: {line.artist ?? '?'} – {line.title ?? '?'} · Einreichender: {line.participantToken ?? '?'} · Land: {line.countryToken ?? '?'}</Typography>
              {line.youtubeUrl && <Typography sx={{ overflowWrap: 'anywhere' }} variant="body2">Quelle: {line.youtubeUrl}</Typography>}
              <TextField label="Vorhandener Beitrag" onChange={(event) => {
                const chosen = entries.find((item) => item.id === Number(event.target.value))
                change(line.sourcePosition, { entryId: chosen?.id ?? null, previousParticipationId: chosen?.contestParticipationId ?? null, confirmReplacement: false })
              }} select size="small" value={line.entryId ?? ''}>
                <MenuItem value="">Bitte wählen</MenuItem>
                {entries.map((item) => <MenuItem key={item.id} value={item.id}>{item.artist} – {item.title}</MenuItem>)}
              </TextField>
              <TextField label="Einreichender" onChange={(event) => change(line.sourcePosition, { participationId: Number(event.target.value) || null, confirmReplacement: false })} select size="small" value={line.participationId ?? ''}>
                <MenuItem value="">Bitte wählen</MenuItem>
                {participants.map((item) => <MenuItem key={item.participationId} value={item.participationId}>{item.displayName} · {item.countryName}{!item.active ? ' (inaktiv)' : ''}</MenuItem>)}
              </TextField>
              <Typography variant="body2">Bisher: {previous?.displayName ?? 'Ohne Teilnehmer'} · Vorgesehen: {next?.displayName ?? 'Offen'} · Aktion: {action}</Typography>
              {line.warnings.map((warning) => <Alert key={warning.code} severity="warning">{warning.message}</Alert>)}
              {next && !next.active && entry?.contestParticipationId !== next.participationId && <Alert severity="error">Inaktive Teilnehmer können nicht neu zugeordnet werden.</Alert>}
              {entry && next && ((entry.ownEntry === true && entry.contestParticipationId !== next.participationId)
                || (next.participationId === ownParticipationId && entry.contestParticipationId !== next.participationId))
                && <Alert severity="error">Die eigene Einreichung kann hier nicht geändert werden. Öffne dafür die Abstimmung bewusst wieder.</Alert>}
              {line.warnings.length > 0 && <FormControlLabel control={<Checkbox checked={line.warningsReviewed} onChange={(event) => change(line.sourcePosition, { warningsReviewed: event.target.checked })} />} label="Quellwarnungen geprüft und Auswahl manuell bestätigt" />}
              {entry?.contestParticipationId != null && entry.contestParticipationId !== line.participationId && <FormControlLabel control={<Checkbox checked={line.confirmReplacement} onChange={(event) => change(line.sourcePosition, { confirmReplacement: event.target.checked })} />} label="Bestehende Zuordnung ausdrücklich ersetzen" />}
              <Typography color={ready ? 'success.main' : 'warning.main'} variant="body2">{ready ? 'Importierbar' : 'Nacharbeit erforderlich'}</Typography>
              <FormControlLabel control={<Checkbox checked={line.included} onChange={(event) => change(line.sourcePosition, { included: event.target.checked })} />} label="Diese Zeile importieren" />
            </Stack>
          </Paper>
        })}
        {duplicate && <Alert severity="error">Ein Beitrag oder Einreichender wäre mehrfach zugeordnet. Bitte die Auswahl korrigieren.</Alert>}
        <Stack direction="row" spacing={1}>
          <Button disabled={busy || selected.length === 0 || invalid || duplicate} onClick={onImport} variant="contained">{busy ? 'Importiert …' : `${selected.length} Zuordnung${selected.length === 1 ? '' : 'en'} bestätigen`}</Button>
          <Button disabled={busy} onClick={onCancel}>Vorschau verwerfen</Button>
        </Stack>
      </Stack>}
    </Stack>
  </Paper>
}
