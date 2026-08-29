import { Draggable, Droppable, type DraggableProvided, type DraggableProvidedDragHandleProps } from '@hello-pangea/dnd'
import {
  Alert,
  Box,
  Button,
  Card,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  Paper,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import { useState } from 'react'
import { CheckIcon, DragIcon, PlaylistRemoveIcon, ReplayIcon, SortIcon } from '../../components/AppIcons'
import type { ContestEntry } from '../entries/api'
import type { Ballot, BallotRanking } from './api'
import { RANKING_DROPPABLE_ID, rankedEntries } from './ballotReorder'
import { ballotWarningText, deriveBallotWarnings, suggestedBallotRanking, type BallotWarning } from './ballotSuggestion'

export function BallotPanel({
  ballot,
  entries,
  showId,
  reordering,
  activeEntryId,
  onClose,
  onApplySuggestion,
  onReopen,
  onRemove,
  onSelect,
}: {
  ballot: Ballot
  entries: ContestEntry[]
  showId: number
  reordering: boolean
  activeEntryId: number | null
  onClose: () => void
  onApplySuggestion: (ranking: BallotRanking) => void
  onReopen: () => void
  onRemove: (entry: ContestEntry) => void
  onSelect: (entry: ContestEntry) => void
}) {
  const [confirmingClose, setConfirmingClose] = useState(false)
  const [confirmingSuggestion, setConfirmingSuggestion] = useState(false)
  const [confirmingReopen, setConfirmingReopen] = useState(false)
  const [clipboardNotice, setClipboardNotice] = useState<string | null>(null)
  const ranked = rankedEntries(entries)
  const closed = ballot.ballotClosedAt !== null
  const canClose = ranked.length >= 15
  const suggestion = suggestedBallotRanking(entries)
  const warnings = deriveBallotWarnings(entries)

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
    <Stack spacing={2}>
      <RankingSurface
        activeEntryId={activeEntryId}
        closed={closed}
        entries={ranked}
        onClose={() => setConfirmingClose(true)}
        onApplySuggestion={() => ranked.length > 0 ? setConfirmingSuggestion(true) : onApplySuggestion(suggestion)}
        onRemove={onRemove}
        onReopen={() => setConfirmingReopen(true)}
        onSelect={onSelect}
        reordering={reordering}
        canClose={canClose}
        canSuggest={suggestion.rankedEntryIds.length > 0}
      />

      {ballot.currentSnapshot !== null && ballot.renderedText === null && (
        <Alert severity="info">
          Die Top-15-Ausgabe wird verfügbar, sobald allen 15 Snapshot-Beiträgen Teilnehmer und damit Länder zugeordnet sind.
        </Alert>
      )}

      {ballot.currentSnapshot !== null && ballot.renderedText !== null && (
        <Paper component="section" elevation={0} sx={{ border: 1, borderColor: 'success.main', p: 2 }}>
          <Stack spacing={1.25}>
            <Typography component="h2" variant="subtitle1">Abgeschlossene Top 15 · Snapshot {ballot.currentSnapshot.snapshotNumber}</Typography>
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
        warnings={warnings}
      />
      <ConfirmationDialog
        open={confirmingSuggestion}
        title="Bestehende Rangliste überschreiben?"
        message="Der Vorschlag ersetzt die aktuelle Rangliste einmalig anhand deiner Einschätzungen. Danach bleibt Drag-and-drop wieder vollständig maßgeblich."
        confirmLabel="Ranglistenvorschlag anwenden"
        onCancel={() => setConfirmingSuggestion(false)}
        onConfirm={() => { setConfirmingSuggestion(false); onApplySuggestion(suggestion) }}
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

function RankingSurface({ activeEntryId, closed, entries, reordering, canClose, canSuggest, onClose, onApplySuggestion, onReopen, onRemove, onSelect }: {
  activeEntryId: number | null
  closed: boolean
  entries: ContestEntry[]
  reordering: boolean
  canClose: boolean
  canSuggest: boolean
  onClose: () => void
  onApplySuggestion: () => void
  onReopen: () => void
  onRemove: (entry: ContestEntry) => void
  onSelect: (entry: ContestEntry) => void
}) {
  const completionAction = closed
    ? <Tooltip title="Abstimmung wieder öffnen">
      <IconButton aria-label="Abstimmung wieder öffnen" color="warning" onClick={onReopen} size="small" sx={{ border: 1, borderColor: 'warning.main' }}>
        <ReplayIcon aria-hidden="true" fontSize="small" />
      </IconButton>
    </Tooltip>
    : <Tooltip title={canClose ? 'Abstimmung abschließen' : 'Mindestens 15 Beiträge müssen gerankt sein.'}>
      <span><IconButton
        aria-label="Abstimmung abschließen"
        disabled={!canClose || reordering}
        onClick={onClose}
        size="small"
        sx={{ backgroundColor: 'primary.main', color: 'primary.contrastText', '&:hover': { backgroundColor: 'primary.dark' } }}
      ><CheckIcon aria-hidden="true" fontSize="small" /></IconButton></span>
    </Tooltip>
  const action = closed
    ? completionAction
    : <Stack direction="row" spacing={0.5} sx={{ flexShrink: 0 }}>
      <Tooltip title={canSuggest ? 'Ranglistenvorschlag aus Bewertung und Sicherheit anwenden' : 'Setze zuerst mindestens eine Einschätzung.'}>
        <span><IconButton
          aria-label="Ranglistenvorschlag anwenden"
          color="secondary"
          disabled={!canSuggest || reordering}
          onClick={onApplySuggestion}
          size="small"
          sx={{ border: 1, borderColor: canSuggest && !reordering ? 'secondary.main' : 'divider' }}
        ><SortIcon aria-hidden="true" fontSize="small" /></IconButton></span>
      </Tooltip>
      {completionAction}
    </Stack>
  const notice = closed
    ? 'Die Top 15 ist abgeschlossen. Für Rangänderungen bitte bewusst wieder öffnen.'
    : !canClose ? `Für den Abschluss fehlen noch ${15 - entries.length} gerankte Beiträge.` : undefined

  return (
    <Paper component="section" elevation={0} sx={{ border: 1, borderColor: closed ? 'divider' : 'secondary.main', p: 1.5 }}>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', justifyContent: 'space-between', minWidth: 0 }}>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flex: 1, minWidth: 0 }}>
          <Typography component="h2" noWrap sx={{ minWidth: 0 }} variant="h6">{closed ? 'Rangliste (gesperrt)' : 'Persönliche Rangliste'}</Typography>
          <Chip aria-label={`${entries.length} gerankte Beiträge`} label={entries.length} size="small" sx={{ flexShrink: 0 }} />
        </Stack>
        {action}
      </Stack>
      {notice !== undefined && <Alert severity={closed ? 'success' : 'info'} sx={{ mt: 1 }}>{notice}</Alert>}
      {closed
        ? <ReadOnlyRanking activeEntryId={activeEntryId} entries={entries} onSelect={onSelect} />
        : <Droppable droppableId={RANKING_DROPPABLE_ID} isDropDisabled={reordering}>
          {(provided, snapshot) => (
            <Box
              {...provided.droppableProps}
              aria-label="Persönliche Rangliste"
              ref={provided.innerRef}
              sx={{ maxHeight: { xs: '64vh', md: 900 }, minHeight: 200, mt: 1.25, overflowY: 'auto', pr: 0.5 }}
            >
              {entries.length === 0 && !snapshot.isDraggingOver && <Alert severity="info">Ziehe Beiträge hierher.</Alert>}
              <Stack spacing={0.5}>
                {entries.map((entry, index) => (
                  <Box key={entry.id}>
                    {index === 15 && <TopFifteenBoundary />}
                    <Draggable draggableId={`ranking-entry-${entry.id}`} index={index} isDragDisabled={reordering}>
                      {(dragProvided, dragSnapshot) => (
                        <RankingEntry
                          active={activeEntryId === entry.id}
                          dragHandleProps={dragProvided.dragHandleProps}
                          dragging={dragSnapshot.isDragging}
                          entry={entry}
                          index={index}
                          onRemove={() => onRemove(entry)}
                          onSelect={() => onSelect(entry)}
                          provided={dragProvided}
                        />
                      )}
                    </Draggable>
                  </Box>
                ))}
                {provided.placeholder}
                <Box
                  aria-label="Am Ende der Rangliste anhängen"
                  sx={{ alignItems: 'center', border: 1, borderColor: snapshot.isDraggingOver ? 'secondary.main' : 'transparent', borderRadius: 1, color: 'text.secondary', display: 'flex', justifyContent: 'center', minHeight: 96, px: 1, transition: 'border-color 120ms ease, background-color 120ms ease', backgroundColor: snapshot.isDraggingOver ? 'action.hover' : 'transparent' }}
                >
                  <Typography variant="body2">Hier ablegen, um am Ende anzuhängen</Typography>
                </Box>
              </Stack>
            </Box>
          )}
        </Droppable>}
    </Paper>
  )
}

function ReadOnlyRanking({ activeEntryId, entries, onSelect }: { activeEntryId: number | null, entries: ContestEntry[], onSelect: (entry: ContestEntry) => void }) {
  return (
    <Stack spacing={0.5} sx={{ maxHeight: { xs: '64vh', md: 900 }, mt: 1.25, overflowY: 'auto', pr: 0.5 }}>
      {entries.length === 0 && <Alert severity="info">Keine gerankten Beiträge.</Alert>}
      {entries.map((entry, index) => (
        <Box key={entry.id}>
          {index === 15 && <TopFifteenBoundary />}
          <CompactEntry active={activeEntryId === entry.id} entry={entry} index={index} onSelect={() => onSelect(entry)} />
        </Box>
      ))}
    </Stack>
  )
}

function RankingEntry({ active, dragHandleProps, dragging, entry, index, onRemove, onSelect, provided }: {
  active: boolean
  dragHandleProps: DraggableProvidedDragHandleProps | null | undefined
  dragging: boolean
  entry: ContestEntry
  index: number
  onRemove: () => void
  onSelect: () => void
  provided: DraggableProvided
}) {
  /* eslint-disable react-hooks/refs -- @hello-pangea/dnd requires forwarding these render-prop refs. */
  return (
    <Card
      {...provided.draggableProps}
      elevation={dragging ? 8 : 0}
      ref={provided.innerRef}
      sx={{ border: 1, borderColor: dragging || active ? 'secondary.main' : 'divider', outline: dragging ? '2px solid' : 'none', outlineColor: 'secondary.main' }}
    >
      <CompactEntry
        active={active}
        dragHandleProps={dragHandleProps}
        dragging={dragging}
        entry={entry}
        index={index}
        onRemove={onRemove}
        onSelect={onSelect}
      />
    </Card>
  )
  /* eslint-enable react-hooks/refs */
}

function CompactEntry({ active, entry, index, onSelect, dragHandleProps, dragging = false, onRemove }: {
  active: boolean
  entry: ContestEntry
  index: number
  onSelect: () => void
  dragHandleProps?: DraggableProvidedDragHandleProps | null
  dragging?: boolean
  onRemove?: () => void
}) {
  const dragEnabled = dragHandleProps !== null && dragHandleProps !== undefined
  return (
    <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', minHeight: 54, p: 0.75 }}>
      <Chip color={index < 15 ? 'secondary' : 'default'} label={index + 1} size="small" sx={{ flexShrink: 0, fontWeight: 700, minWidth: 34 }} />
      {dragHandleProps !== undefined && (
        <Tooltip title={dragEnabled ? 'Ziehen, um die Rangfolge zu ändern' : 'Drag-and-drop wird gespeichert'}>
          <Box
            {...(dragHandleProps ?? {})}
            aria-disabled={!dragEnabled}
            aria-label={`${entry.artist} verschieben`}
            sx={{ alignItems: 'center', borderRadius: 1, color: dragEnabled ? 'text.secondary' : 'action.disabled', cursor: dragging ? 'grabbing' : dragEnabled ? 'grab' : 'default', display: 'inline-flex', flexShrink: 0, justifyContent: 'center', minHeight: 36, minWidth: 32, '&:active': { cursor: 'grabbing' } }}
          >
            <DragIcon aria-hidden="true" fontSize="small" />
          </Box>
        </Tooltip>
      )}
      <Box
        aria-current={active ? 'true' : undefined}
        aria-label={`${entry.title} von ${entry.artist} auswählen`}
        onClick={onSelect}
        onKeyDown={(event) => {
          if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault()
            onSelect()
          }
        }}
        role="button"
        sx={{ cursor: 'pointer', flex: 1, minWidth: 0, outlineOffset: 2, '&:focus-visible': { outline: '2px solid', outlineColor: 'secondary.main' } }}
        tabIndex={0}
      >
        <Tooltip title={`${entry.artist} – ${entry.title}`}>
          <Typography noWrap sx={{ fontWeight: 650 }} variant="body2">{entry.title}</Typography>
        </Tooltip>
        <Typography color="text.secondary" noWrap variant="caption">{entry.artist}</Typography>
      </Box>
      {onRemove !== undefined && (
        <Tooltip title="Aus Ranking entfernen">
          <IconButton aria-label={`${entry.artist} – ${entry.title} aus Ranking entfernen`} color="primary" onClick={onRemove} size="small">
            <PlaylistRemoveIcon aria-hidden="true" fontSize="small" />
          </IconButton>
        </Tooltip>
      )}
    </Stack>
  )
}

function TopFifteenBoundary() {
  return <Typography color="secondary" sx={{ fontWeight: 700, my: 0.5 }} variant="caption">Außerhalb der Top 15</Typography>
}

function ConfirmationDialog({ open, title, message, confirmLabel, onCancel, onConfirm, warnings = [] }: {
  open: boolean
  title: string
  message: string
  confirmLabel: string
  onCancel: () => void
  onConfirm: () => void
  warnings?: BallotWarning[]
}) {
  return (
    <Dialog onClose={onCancel} open={open}>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent><Stack spacing={1.5}><Typography>{message}</Typography>
        {warnings.length > 0 && <Alert severity="warning">
          <Typography component="p" sx={{ fontWeight: 700 }} variant="body2">Hinweise vor dem Abschluss</Typography>
          <Box component="ul" sx={{ mb: 0, mt: 0.5, pl: 2.5 }}>
            {warnings.map((warning) => <li key={warning.code}>{ballotWarningText(warning)}</li>)}
          </Box>
        </Alert>}
      </Stack></DialogContent>
      <DialogActions>
        <Button onClick={onCancel}>Abbrechen</Button>
        <Button color="warning" onClick={onConfirm} variant="contained">{confirmLabel}</Button>
      </DialogActions>
    </Dialog>
  )
}
