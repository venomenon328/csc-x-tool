import { DragDropContext } from '@hello-pangea/dnd'
import { render, screen } from '@testing-library/react'
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
    activeEntryId={null} ballot={ballot} entries={entries} onClose={vi.fn()} onRemove={vi.fn()} onReopen={vi.fn()}
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
  })

  it('keeps snapshot output and the matching text-file endpoint available', () => {
    const snapshotText = 'Platz #1 - Deutschland: Snapshot Artist - Snapshot Title'
    renderPanel({
      ballotClosedAt: '2026-08-27T12:00:00Z',
      currentSnapshot: { id: 1, snapshotNumber: 1, createdAt: '2026-08-27T12:00:00Z', current: true, items: [] },
      snapshots: [{ id: 1, snapshotNumber: 1, createdAt: '2026-08-27T12:00:00Z', current: true, items: [] }],
      renderedText: snapshotText,
    })

    expect(screen.getByRole('textbox')).toHaveValue(snapshotText)
    expect(screen.getByRole('link', { name: 'Textdatei herunterladen' })).toHaveAttribute('href', '/api/shows/1/ballot/export')
  })
})
