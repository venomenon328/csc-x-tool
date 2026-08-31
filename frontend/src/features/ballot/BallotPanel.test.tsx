import { DragDropContext } from '@hello-pangea/dnd'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { ContestEntry } from '../entries/api'
import type { Ballot } from './api'
import { BallotPanel } from './BallotPanel'

const entries: ContestEntry[] = Array.from({ length: 16 }, (_, index) => ({
  id: index + 1, mottoShowId: 1, artist: `Artist ${index + 1}`, title: `Song ${index + 1}`,
  youtubeUrl: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', comment: null, assessment: null, assessmentConfidence: null,
  poolPosition: index + 1, rankingPosition: index + 1, participantId: null, createdAt: '', updatedAt: '',
}))

function renderPanel(ballot: Ballot = { ballotClosedAt: null, currentSnapshot: null, snapshots: [], renderedText: null }) {
  return render(<DragDropContext onDragEnd={vi.fn()}><BallotPanel
    activeEntryId={null} ballot={ballot} entries={entries} onApplySuggestion={vi.fn()} onClose={vi.fn()} onRemove={vi.fn()} onReopen={vi.fn()}
    onSelect={vi.fn()} reordering={false} showId={1}
  /></DragDropContext>)
}

describe('BallotPanel', () => {
  it('renders one compact ranking surface with a real append drop target', () => {
    renderPanel()

    expect(screen.getByText('Außerhalb der Top 15')).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Persönliche Rangliste' })).toBeVisible()
    expect(screen.queryByRole('heading', { name: 'Noch nicht gerankt' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Abstimmung abschließen' })).toBeEnabled()
    expect(screen.getByLabelText('Am Ende der Rangliste anhängen')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Artist 1 – Song 1 aus Ranking entfernen' })).toBeVisible()
  })

  it('uses a non-native handle with a representation-specific draggable id', () => {
    renderPanel()

    expect(screen.getByLabelText('Artist 1 verschieben').tagName).toBe('DIV')
    expect(screen.getByLabelText('Persönliche Rangliste')).toBeVisible()
  })

  it('keeps reopening in the locked compact ranking surface', () => {
    renderPanel({ ballotClosedAt: '2026-08-27T12:00:00Z', currentSnapshot: null, snapshots: [], renderedText: null })

    expect(screen.getByRole('heading', { name: 'Rangliste (gesperrt)' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Abstimmung wieder öffnen' })).toBeVisible()
    expect(screen.getByText(/Die Top 15 ist abgeschlossen/)).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Artist 1 – Song 1 aus Ranking entfernen' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Ranglistenvorschlag anwenden' })).not.toBeInTheDocument()
  })

  it('keeps snapshot output and the matching text-file endpoint available without participant assignments', () => {
    const snapshotText = '1. Snapshot Artist - Snapshot Title'
    renderPanel({
      ballotClosedAt: '2026-08-27T12:00:00Z',
      currentSnapshot: { id: 1, snapshotNumber: 1, createdAt: '2026-08-27T12:00:00Z', current: true, items: [] },
      snapshots: [{ id: 1, snapshotNumber: 1, createdAt: '2026-08-27T12:00:00Z', current: true, items: [] }],
      renderedText: snapshotText,
    })

    expect(screen.getByRole('textbox')).toHaveValue(snapshotText)
    expect(screen.getByRole('button', { name: 'Top 15 kopieren' })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Textdatei herunterladen' })).toHaveAttribute('href', '/api/shows/1/ballot/export')
    expect(screen.queryByText(/Teilnehmer und damit Länder zugeordnet/)).not.toBeInTheDocument()
  })

  it('only applies a ranking suggestion after confirmation when a ranking already exists', async () => {
    const user = userEvent.setup()
    const onApplySuggestion = vi.fn()
    const assessed = entries.map((entry, index) => ({ ...entry, assessment: index === 0 ? 5 : 3, assessmentConfidence: 5 }))
    render(<DragDropContext onDragEnd={vi.fn()}><BallotPanel
      activeEntryId={null} ballot={{ ballotClosedAt: null, currentSnapshot: null, snapshots: [], renderedText: null }} entries={assessed}
      onApplySuggestion={onApplySuggestion} onClose={vi.fn()} onRemove={vi.fn()} onReopen={vi.fn()} onSelect={vi.fn()} reordering={false} showId={1}
    /></DragDropContext>)

    await user.click(screen.getByRole('button', { name: 'Ranglistenvorschlag anwenden' }))
    expect(onApplySuggestion).not.toHaveBeenCalled()
    expect(screen.getByRole('heading', { name: 'Bestehende Rangliste überschreiben?' })).toBeVisible()
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Ranglistenvorschlag anwenden' }))

    expect(onApplySuggestion).toHaveBeenCalledWith({
      rankedEntryIds: [1, ...Array.from({ length: 15 }, (_, index) => index + 2)],
      unrankedEntryIds: [],
    })
  })

  it('shows completion warnings as hints without disabling the close action', async () => {
    const user = userEvent.setup()
    renderPanel()

    await user.click(screen.getByRole('button', { name: 'Abstimmung abschließen' }))
    expect(screen.getByText('Hinweise vor dem Abschluss')).toBeVisible()
    expect(screen.getByText('16 Beiträge haben noch keine Einschätzung.')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Abstimmung abschließen' })).toBeEnabled()
  })
})
