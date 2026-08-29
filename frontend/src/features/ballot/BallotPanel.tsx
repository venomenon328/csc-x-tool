import { DragDropContext, Draggable, Droppable, type DraggableProvidedDragHandleProps, type DropResult } from '@hello-pangea/dnd'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Paper,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import { useState, type ReactNode } from 'react'
import { DragIcon } from '../../components/AppIcons'
import type { ContestEntry } from '../entries/api'
import { splitBallotEntries } from './ballotReorder'
import type { Ballot } from './api'

export function BallotPanel({
  ballot,
  entries,
  showId,
  reordering,
  onDrop,
  onClose,
  onReopen,
}: {
  ballot: Ballot
  entries: ContestEntry[]
  showId: number
  reordering: boolean
  onDrop: (result: DropResult) => void
  onClose: () => void
  onReopen: () => void
}) {
  const [confirmingClose, setConfirmingClose] = useState(false)
  const [confirmingReopen, setConfirmingReopen] = useState(false)
  const [clipboardNotice, setClipboardNotice] = useState<string | null>(null)
  const { ranked, unranked } = splitBallotEntries(entries)
  const closed = ballot.ballotClosedAt !== null
  const canClose = ranked.length >= 15

  async function copySnapshot() {
    if (ballot.renderedText === null) return
    try {
      await navigator.clipboard.writeText(ballot.renderedText)
      setClipboardNotice('Die aktuelle Top 15 wurde in die Zwischenablage kopiert.')
    } catch {
      setClipboardNotice('Die Zwischenablage konnte nicht beschrieben werden. Nutze bei Bedarf die Textdatei.')
    }
  }

  return (
    <Stack spacing={2.5}>
      {closed
        ? <ClosedRanking lists={{ ranked, unranked }} onReopen={() => setConfirmingReopen(true)} />
        : <DragDropContext onDragEnd={onDrop}>
          <Stack direction={{ xs: 'column', lg: 'row' }} spacing={2.5}>
            <EntryDropZone entries={unranked} title="Noch nicht gerankt" droppableId="unranked-entries" reordering={reordering} />
            <EntryDropZone
              action={<Button disabled={!canClose || reordering} onClick={() => setConfirmingClose(true)} size="small" variant="contained">Abstimmung abschließen</Button>}
              entries={ranked}
              notice={!canClose ? `Für den Abschluss fehlen noch ${15 - ranked.length} gerankte Beiträge.` : undefined}
              droppableId="ranked-entries"
              ranked
              reordering={reordering}
              title="Rangliste"
            />
          </Stack>
        </DragDropContext>}

      {ballot.currentSnapshot !== null && ballot.renderedText === null && (
        <Alert severity="info">
          Die Top-15-Ausgabe wird verfügbar, sobald allen 15 Snapshot-Beiträgen Teilnehmer und damit Länder zugeordnet sind.
        </Alert>
      )}

      {ballot.currentSnapshot !== null && ballot.renderedText !== null && (
        <Paper component="section" elevation={0} sx={{ border: 1, borderColor: 'success.main', p: 2.5 }}>
          <Stack spacing={1.5}>
            <Typography component="h2" variant="h6">Abgeschlossene Top 15 · Snapshot {ballot.currentSnapshot.snapshotNumber}</Typography>
            <Typography color="text.secondary" variant="body2">
              Rang, Interpret und Titel stammen aus dem gespeicherten Snapshot; das Land wird aus der nach Abschluss vorgenommenen Teilnehmerzuordnung ergänzt. Vorschau, Zwischenablage und Textdatei verwenden dieselbe Ausgabe.
            </Typography>
            <TextField aria-label="Top-15-Textvorschau" fullWidth multiline minRows={15} slotProps={{ htmlInput: { readOnly: true } }} value={ballot.renderedText} />
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
              <Button onClick={() => void copySnapshot()}>Top 15 kopieren</Button>
              <Button component="a" href={`/api/shows/${showId}/ballot/export`} variant="outlined">Textdatei herunterladen</Button>
            </Stack>
            {clipboardNotice !== null && <Alert severity={clipboardNotice.startsWith('Die aktuelle') ? 'success' : 'warning'}>{clipboardNotice}</Alert>}
          </Stack>
        </Paper>
      )}

      {ballot.snapshots.length > 1 && (
        <Typography color="text.secondary" variant="body2">
          Historie: {ballot.snapshots.filter((snapshot) => !snapshot.current).map((snapshot) => `Snapshot ${snapshot.snapshotNumber}`).join(', ')} bleibt erhalten.
        </Typography>
      )}

      <ConfirmationDialog
        open={confirmingClose}
        title="Top 15 bewusst abschließen?"
        message="Die ersten 15 Ränge werden als unveränderlicher Snapshot gespeichert. Weitere Rangänderungen erfordern danach eine bewusste Wiederöffnung."
        confirmLabel="Abstimmung abschließen"
        onCancel={() => setConfirmingClose(false)}
        onConfirm={() => { setConfirmingClose(false); onClose() }}
      />
      <ConfirmationDialog
        open={confirmingReopen}
        title="Abstimmung wieder öffnen?"
        message="Der aktuelle Snapshot bleibt historisch erhalten. Erst danach können Rangplätze wieder verändert werden."
        confirmLabel="Wieder öffnen"
        onCancel={() => setConfirmingReopen(false)}
        onConfirm={() => { setConfirmingReopen(false); onReopen() }}
      />
    </Stack>
  )
}

function ClosedRanking({ lists, onReopen }: { lists: { ranked: ContestEntry[], unranked: ContestEntry[] }, onReopen: () => void }) {
  return (
    <Stack direction={{ xs: 'column', lg: 'row' }} spacing={2.5}>
      <ReadOnlyEntryList entries={lists.unranked} title="Noch nicht gerankt" />
      <ReadOnlyEntryList
        action={<Button color="warning" onClick={onReopen} size="small" variant="outlined">Abstimmung wieder öffnen</Button>}
        entries={lists.ranked}
        notice="Die Top 15 ist abgeschlossen. Für Rangänderungen bitte bewusst wieder öffnen."
        ranked
        title="Rangliste (gesperrt)"
      />
    </Stack>
  )
}

function BallotListHeader({ title, count, action }: { title: string, count: number, action?: ReactNode }) {
  return (
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ alignItems: { sm: 'center' }, justifyContent: 'space-between' }}>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', minWidth: 0 }}>
        <Typography component="h3" variant="h6">{title}</Typography>
        <Chip aria-label={`${count} Beiträge`} label={count} size="small" />
      </Stack>
      {action}
    </Stack>
  )
}

function ReadOnlyEntryList({ entries, title, ranked = false, action, notice }: {
  entries: ContestEntry[]
  title: string
  ranked?: boolean
  action?: ReactNode
  notice?: string
}) {
  return (
    <Paper component="section" elevation={0} sx={{ border: 1, borderColor: ranked ? 'secondary.main' : 'divider', flex: 1, minWidth: 0, p: 2 }}>
      <BallotListHeader action={action} count={entries.length} title={title} />
      {notice !== undefined && <Alert severity="success" sx={{ mt: 1.5 }}>{notice}</Alert>}
      <Stack spacing={1} sx={{ maxHeight: '58vh', mt: 1.5, overflowY: 'auto', pr: 0.5 }}>
        {entries.length === 0 && <Alert severity="info">Keine Beiträge in dieser Liste.</Alert>}
        {entries.map((entry, index) => (
          <Box key={entry.id}>
            {ranked && index === 15 && <TopFifteenBoundary />}
            <Card elevation={0} sx={{ border: 1, borderColor: 'divider' }}>
              <CardContent sx={{ py: '12px !important' }}><EntryCard entry={entry} index={index} ranked={ranked} /></CardContent>
            </Card>
          </Box>
        ))}
      </Stack>
    </Paper>
  )
}

function EntryDropZone({ entries, title, droppableId, ranked = false, reordering, action, notice }: {
  entries: ContestEntry[]
  title: string
  droppableId: string
  ranked?: boolean
  reordering: boolean
  action?: ReactNode
  notice?: string
}) {
  return (
    <Paper component="section" elevation={0} sx={{ border: 1, borderColor: ranked ? 'secondary.main' : 'divider', flex: 1, minWidth: 0, p: 2 }}>
      <BallotListHeader action={action} count={entries.length} title={title} />
      {notice !== undefined && <Alert severity="info" sx={{ mt: 1.5 }}>{notice}</Alert>}
      <Droppable droppableId={droppableId} isDropDisabled={reordering}>
        {(provided, snapshot) => (
          <Box
            {...provided.droppableProps}
            aria-label={title}
            ref={provided.innerRef}
            sx={{ minHeight: 260, mt: 1.5, overflowY: 'auto', pr: 0.5, maxHeight: '58vh' }}
          >
            {snapshot.isDraggingOver && <Alert severity="info" sx={{ mb: 1 }}>Hier wird der Beitrag eingefügt.</Alert>}
            {entries.length === 0 && !snapshot.isDraggingOver && <Alert severity="info">Ziehe Beiträge hierher.</Alert>}
            <Stack spacing={1}>
              {entries.map((entry, index) => (
                <Box key={entry.id}>
                  {ranked && index === 15 && <TopFifteenBoundary />}
                  <Draggable draggableId={String(entry.id)} index={index} isDragDisabled={reordering}>
                    {(dragProvided, dragSnapshot) => (
                      <Card
                        {...dragProvided.draggableProps}
                        elevation={dragSnapshot.isDragging ? 8 : 0}
                        ref={dragProvided.innerRef}
                        sx={{ border: 1, borderColor: dragSnapshot.isDragging ? 'secondary.main' : 'divider', outline: dragSnapshot.isDragging ? '2px solid' : 'none', outlineColor: 'secondary.main' }}
                      >
                        <CardContent sx={{ py: '12px !important' }}>
                          <EntryCard dragging={dragSnapshot.isDragging} dragHandleProps={dragProvided.dragHandleProps} entry={entry} index={index} ranked={ranked} />
                        </CardContent>
                      </Card>
                    )}
                  </Draggable>
                </Box>
              ))}
              {provided.placeholder}
            </Stack>
          </Box>
        )}
      </Droppable>
    </Paper>
  )
}

function TopFifteenBoundary() {
  return <Divider sx={{ borderColor: 'secondary.main', borderWidth: 1, my: 0.75 }} textAlign="left"><Chip color="secondary" label="Außerhalb der Top 15" size="small" /></Divider>
}

function EntryCard({ entry, index, ranked, dragHandleProps, dragging = false }: {
  entry: ContestEntry
  index: number
  ranked: boolean
  dragHandleProps?: DraggableProvidedDragHandleProps | null
  dragging?: boolean
}) {
  const dragEnabled = dragHandleProps !== null && dragHandleProps !== undefined
  return (
    <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
      {dragHandleProps !== undefined && (
        <Tooltip title={dragEnabled ? 'Ziehen, um die Rangfolge zu ändern' : 'Drag-and-drop wird gespeichert'}>
          <Box
            {...(dragHandleProps ?? {})}
            aria-disabled={!dragEnabled}
            aria-label={`${entry.artist} verschieben`}
            sx={{ alignItems: 'center', borderRadius: 1, color: dragEnabled ? 'text.secondary' : 'action.disabled', cursor: dragging ? 'grabbing' : dragEnabled ? 'grab' : 'default', display: 'inline-flex', flexShrink: 0, justifyContent: 'center', minHeight: 40, minWidth: 40, '&:active': { cursor: 'grabbing' } }}
          >
            <DragIcon aria-hidden="true" />
          </Box>
        </Tooltip>
      )}
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Typography component="h4" sx={{ fontWeight: 650, overflowWrap: 'anywhere' }} variant="subtitle1">{entry.title}</Typography>
        <Typography color="text.secondary" sx={{ overflowWrap: 'anywhere' }} variant="body2">{entry.artist}</Typography>
        {ranked && <Typography color="text.secondary" variant="body2">Rang {index + 1}{index < 15 ? ' · Top 15' : ''}</Typography>}
      </Box>
      {ranked && index < 15 && <Chip color="secondary" label={index + 1} size="small" />}
    </Stack>
  )
}

function ConfirmationDialog({ open, title, message, confirmLabel, onCancel, onConfirm }: {
  open: boolean
  title: string
  message: string
  confirmLabel: string
  onCancel: () => void
  onConfirm: () => void
}) {
  return (
    <Dialog onClose={onCancel} open={open}>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent><Typography>{message}</Typography></DialogContent>
      <DialogActions>
        <Button onClick={onCancel}>Abbrechen</Button>
        <Button color="warning" onClick={onConfirm} variant="contained">{confirmLabel}</Button>
      </DialogActions>
    </Dialog>
  )
}
