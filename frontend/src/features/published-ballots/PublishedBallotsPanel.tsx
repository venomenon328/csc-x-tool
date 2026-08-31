import { Alert, Box, Button, Checkbox, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, MenuItem, Paper, Select, Stack, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material'
import { useCallback, useEffect, useState } from 'react'
import { ApiErrorNotice } from '../../components/ApiErrorNotice'
import { ClipboardImportArea } from '../entries/ClipboardImportArea'
import type { ContestEntry } from '../entries/api'
import type { Participant } from '../participants/api'
import { fetchPublishedBallotDetail, fetchPublishedBallotOverview, importPublishedBallots, previewPublishedBallots, setPublishedBallotStatus, PublishedBallotApiError, type BallotPreviewBlock, type PublishedBallotDetail, type PublishedBallotOverview } from './api'
import { voterSelectionPatch } from './voterSelection'

type EditableBlock = BallotPreviewBlock & { included: boolean, replaceExisting: boolean }

export function PublishedBallotsPanel({ showId, entries, participants, headingLevel = 'h2' }: { showId: number, entries: ContestEntry[], participants: Participant[], headingLevel?: 'h2' | 'h3' }) {
  const [overview, setOverview] = useState<PublishedBallotOverview | null>(null)
  const [detail, setDetail] = useState<PublishedBallotDetail | null>(null)
  const [preview, setPreview] = useState<EditableBlock[] | null>(null)
  const [error, setError] = useState<PublishedBallotApiError | null>(null)
  const [saving, setSaving] = useState(false)
  const [statusAction, setStatusAction] = useState<{ participationId: number, status: 'UNERFASST' | 'NICHT_ABGESTIMMT', name: string } | null>(null)

  const load = useCallback(async () => {
    try { setOverview(await fetchPublishedBallotOverview(showId)); setError(null) }
    catch (caught) { setError(asError(caught)); setOverview(null) }
  }, [showId])
  useEffect(() => {
    let cancelled = false
    void fetchPublishedBallotOverview(showId)
      .then((loaded) => { if (!cancelled) { setOverview(loaded); setError(null) } })
      .catch((caught: unknown) => { if (!cancelled) { setError(asError(caught)); setOverview(null) } })
    return () => { cancelled = true }
  }, [showId])
  async function showDetail(participationId: number) {
    try { setDetail(await fetchPublishedBallotDetail(showId, participationId)); setError(null) } catch (caught) { setError(asError(caught)) }
  }
  async function previewPaste(html: string, text: string) {
    try {
      const blocks = await previewPublishedBallots(showId, html, text)
      setPreview(blocks.map((block) => ({ ...block, included: true, replaceExisting: false }))); setError(null)
    } catch (caught) { setError(asError(caught)) }
  }
  async function importSelected() {
    if (preview === null) return
    const selected = preview.filter((block) => block.included)
    if (selected.some((block) => !validBlock(block, entries))) return
    setSaving(true)
    try {
      await importPublishedBallots(showId, selected.map((block) => ({ participationId: block.participationId ?? -1, replaceExisting: block.replaceExisting, positions: block.positions.map((position) => ({ entryId: position.entryId ?? -1, rank: position.rank })) })))
      setPreview(null); setDetail(null); await load()
    } catch (caught) { setError(asError(caught)) } finally { setSaving(false) }
  }
  async function confirmStatus() {
    if (statusAction === null) return
    setSaving(true)
    try { await setPublishedBallotStatus(showId, statusAction.participationId, statusAction.status); setStatusAction(null); setDetail(null); await load() }
    catch (caught) { setError(asError(caught)) } finally { setSaving(false) }
  }
  function updateBlock(position: number, patch: Partial<EditableBlock>) { setPreview((current) => current?.map((block) => block.sourcePosition === position ? { ...block, ...patch } : block) ?? null) }
  function updatePosition(blockPosition: number, rank: number, entryId: number | null) {
    setPreview((current) => current?.map((block) => block.sourcePosition !== blockPosition ? block : {
      ...block, positions: block.positions.map((position) => position.rank === rank ? { ...position, entryId } : position),
    }) ?? null)
  }
  if (overview === null) return error === null ? <Typography color="text.secondary">Einzelwertungen werden geladen …</Typography> : <ApiErrorNotice error={error.apiError} />
  const existingBallotParticipationIds = new Set(overview.participants.filter((participant) => participant.ballotExists).map((participant) => participant.participationId))
  return <Stack component="section" spacing={2}><Box><Typography component={headingLevel} variant="h5">Einzelwertungen</Typography><Typography color="text.secondary">Veröffentlichte Top 15 werden atomar als Ränge gespeichert. Punkte werden nur aus diesen Rängen abgeleitet; Gesamtwertungen werden nicht berechnet.</Typography></Box>
    {error !== null && <ApiErrorNotice error={error.apiError} />}
    {!overview.entryListReady ? <Alert severity="warning">Die vollständige Songliste einschließlich aller Einreichenden muss vor der Stimmzettelerfassung feststehen.</Alert> : <>
      <Paper sx={{ p: 2 }}><Stack direction={{ xs: 'column', md: 'row' }} spacing={2}><Typography><strong>{overview.votedCount}</strong> abgestimmt</Typography><Typography><strong>{overview.notVotedCount}</strong> nicht abgestimmt</Typography><Typography><strong>{overview.unrecordedCount}</strong> unerfasst</Typography></Stack></Paper>
      <ClipboardImportArea onPasteData={previewPaste} />
      {preview !== null && <ImportPreview blocks={preview} entries={entries} existingBallotParticipationIds={existingBallotParticipationIds} participants={participants} saving={saving} onCancel={() => setPreview(null)} onChangeBlock={updateBlock} onChangePosition={updatePosition} onImport={importSelected} />}
    </>}
    <Paper sx={{ overflowX: 'auto' }}><Table aria-label="Stimmzettelstatus"><TableHead><TableRow><TableCell>Teilnehmer</TableCell><TableCell>Land</TableCell><TableCell>Status</TableCell><TableCell align="right">Aktionen</TableCell></TableRow></TableHead><TableBody>{overview.participants.map((participant) => <TableRow key={participant.participationId}><TableCell>{participant.displayName}</TableCell><TableCell>{participant.countryName}</TableCell><TableCell>{participant.status}</TableCell><TableCell align="right"><Button onClick={() => void showDetail(participant.participationId)}>Details</Button>{overview.entryListReady && <><Button color="warning" onClick={() => setStatusAction({ participationId: participant.participationId, status: 'NICHT_ABGESTIMMT', name: participant.displayName })}>Nicht abgestimmt</Button>{participant.status !== 'UNERFASST' && <Button onClick={() => setStatusAction({ participationId: participant.participationId, status: 'UNERFASST', name: participant.displayName })}>Zurücksetzen</Button>}</>}</TableCell></TableRow>)}</TableBody></Table></Paper>
    {detail !== null && <Detail detail={detail} onClose={() => setDetail(null)} />}
    <Dialog onClose={() => !saving && setStatusAction(null)} open={statusAction !== null}><DialogTitle>Status bewusst ändern?</DialogTitle><DialogContent><Typography>{statusAction?.status === 'NICHT_ABGESTIMMT' ? `${statusAction.name} wird ausdrücklich als nicht abgestimmt gespeichert.` : `Der Stimmzettelstatus von ${statusAction?.name} wird entfernt und wieder als unerfasst behandelt.`}</Typography></DialogContent><DialogActions><Button disabled={saving} onClick={() => setStatusAction(null)}>Abbrechen</Button><Button color="warning" disabled={saving} onClick={() => void confirmStatus()} variant="contained">Bewusst bestätigen</Button></DialogActions></Dialog>
  </Stack>
}

function ImportPreview({ blocks, entries, existingBallotParticipationIds, participants, saving, onCancel, onChangeBlock, onChangePosition, onImport }: { blocks: EditableBlock[], entries: ContestEntry[], existingBallotParticipationIds: ReadonlySet<number>, participants: Participant[], saving: boolean, onCancel: () => void, onChangeBlock: (position: number, patch: Partial<EditableBlock>) => void, onChangePosition: (blockPosition: number, rank: number, entryId: number | null) => void, onImport: () => void }) {
  const selected = blocks.filter((block) => block.included)
  const invalid = selected.filter((block) => !validBlock(block, entries))
  return <Paper component="section" sx={{ p: 2 }}><Stack spacing={2}><Box><Typography component="h3" variant="h6">Importvorschau Einzelwertungen</Typography><Typography color="text.secondary" variant="body2">Die Vorschau wird nicht gespeichert. Die erste Songzeile erhält Rang 15, die letzte Rang 1; das Punktewort wird nicht ausgewertet.</Typography></Box>{blocks.map((block) => <Paper key={block.sourcePosition} sx={{ p: 2 }} variant="outlined"><Stack spacing={1}><FormControlLabel control={<Checkbox checked={block.included} onChange={(event) => onChangeBlock(block.sourcePosition, { included: event.target.checked })} />} label={`Block ${block.sourcePosition} importieren`} /><Select displayEmpty value={block.participationId ?? ''} onChange={(event) => { const participationId = String(event.target.value) === '' ? null : Number(event.target.value); onChangeBlock(block.sourcePosition, voterSelectionPatch(participationId, participants, existingBallotParticipationIds, block.warnings)) }}><MenuItem value="">Abstimmenden manuell wählen</MenuItem>{participants.map((participant) => <MenuItem key={participant.participationId} value={participant.participationId}>{participant.displayName} · {participant.countryName}</MenuItem>)}</Select>{block.existingBallot && <FormControlLabel control={<Checkbox checked={block.replaceExisting} onChange={(event) => onChangeBlock(block.sourcePosition, { replaceExisting: event.target.checked })} />} label="Vorhandenen Stimmzettel bewusst ersetzen" />}{block.warnings.map((warning, index) => <Alert key={`${warning.code}-${index}`} severity="warning">{warning.message}</Alert>)}<Table size="small"><TableHead><TableRow><TableCell>Rang</TableCell><TableCell>Quellzeile</TableCell><TableCell>Beitrag</TableCell></TableRow></TableHead><TableBody>{block.positions.slice().sort((left, right) => right.rank - left.rank).map((position) => <TableRow key={position.rank}><TableCell>{position.rank}</TableCell><TableCell>{position.sourceText}</TableCell><TableCell><Select displayEmpty fullWidth value={position.entryId ?? ''} onChange={(event) => onChangePosition(block.sourcePosition, position.rank, String(event.target.value) === '' ? null : Number(event.target.value))}><MenuItem value="">Beitrag manuell wählen</MenuItem>{entries.map((entry) => <MenuItem key={entry.id} value={entry.id}>{entry.artist} – {entry.title}</MenuItem>)}</Select>{position.warnings.map((warning, index) => <Alert key={`${warning.code}-${index}`} severity="warning">{warning.message}</Alert>)}</TableCell></TableRow>)}</TableBody></Table></Stack></Paper>)}{invalid.length > 0 && <Alert severity="warning">Ausgewählte Blöcke benötigen einen eindeutigen Abstimmenden, genau 15 unterschiedliche wählbare Beiträge und gegebenenfalls eine Ersatzbestätigung.</Alert>}<Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}><Button disabled={saving} onClick={onCancel}>Vorschau verwerfen</Button><Button disabled={saving || selected.length === 0 || invalid.length > 0} onClick={onImport} variant="contained">{saving ? 'Importiert …' : `${selected.length} Stimmzettel atomar importieren`}</Button></Stack></Stack></Paper>
}

function Detail({ detail, onClose }: { detail: PublishedBallotDetail, onClose: () => void }) {
  return <Dialog fullWidth maxWidth="md" onClose={onClose} open><DialogTitle>Einzelwertung · {detail.displayName}</DialogTitle><DialogContent dividers><Stack spacing={2}><Typography>Status: {detail.status}</Typography>{detail.status === 'ABGESTIMMT' && <Table aria-label="Top 15"><TableHead><TableRow><TableCell>Rang</TableCell><TableCell>Punkte</TableCell><TableCell>Song</TableCell><TableCell>Einreichender</TableCell></TableRow></TableHead><TableBody>{detail.positions.map((position) => <TableRow key={position.rank}><TableCell>{position.rank}</TableCell><TableCell>{position.points}</TableCell><TableCell>{position.artist} – {position.title}</TableCell><TableCell>{position.submitterDisplayName ?? '—'}</TableCell></TableRow>)}</TableBody></Table>}<Table aria-label="Abgeleitete Bewertungszustände"><TableHead><TableRow><TableCell>Song</TableCell><TableCell>Zustand</TableCell><TableCell>Rang</TableCell><TableCell>Punkte</TableCell></TableRow></TableHead><TableBody>{detail.entries.map((entry) => <TableRow key={entry.entryId}><TableCell>{entry.artist} – {entry.title}</TableCell><TableCell>{entry.state}</TableCell><TableCell>{entry.rank ?? '—'}</TableCell><TableCell>{entry.points ?? '—'}</TableCell></TableRow>)}</TableBody></Table></Stack></DialogContent><DialogActions><Button onClick={onClose}>Schließen</Button></DialogActions></Dialog>
}

function validBlock(block: EditableBlock, entries: ContestEntry[]) {
  const ids = block.positions.map((position) => position.entryId)
  return block.participationId !== null && block.positions.length === 15 && ids.every((id) => id !== null) && new Set(ids).size === 15
    && (!block.existingBallot || block.replaceExisting)
    && !ids.some((id) => entries.find((entry) => entry.id === id)?.contestParticipationId === block.participationId)
}
function asError(error: unknown) { return error instanceof PublishedBallotApiError ? error : new PublishedBallotApiError({ timestamp: new Date().toISOString(), status: 0, code: 'NETWORK_ERROR', message: 'Die Einzelwertungen konnten nicht verarbeitet werden.', path: '/api/shows' }) }
