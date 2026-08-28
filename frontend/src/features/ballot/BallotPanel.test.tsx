import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { ContestEntry } from '../entries/api'
import { BallotPanel } from './BallotPanel'

const entries: ContestEntry[] = Array.from({ length: 16 }, (_, index) => ({
  id: index + 1,
  mottoShowId: 1,
  artist: `Artist ${index + 1}`,
  title: `Song ${index + 1}`,
  youtubeUrl: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ',
  comment: null,
  listened: false,
  relisten: false,
  rankingPosition: index + 1,
  participantId: null,
  createdAt: '',
  updatedAt: '',
}))

describe('BallotPanel', () => {
  it('visibly separates rank 16 from the highlighted top fifteen', () => {
    render(
      <BallotPanel
        ballot={{ ballotClosedAt: null, currentSnapshot: null, snapshots: [], renderedText: null }}
        entries={entries}
        onClose={vi.fn()}
        onDrop={vi.fn()}
        onReopen={vi.fn()}
        reordering={false}
        showId={1}
      />,
    )

    expect(screen.getByText('Außerhalb der Top 15')).toBeVisible()
    expect(screen.getByText('Rang 15 · Top 15')).toBeVisible()
    expect(screen.getByText('Rang 16')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Abstimmung abschließen' })).toBeEnabled()
  })

  it('waits for participant countries before exposing the CSC export', () => {
    render(
      <BallotPanel
        ballot={{
          ballotClosedAt: '2026-08-27T12:00:00Z',
          currentSnapshot: { id: 1, snapshotNumber: 1, createdAt: '2026-08-27T12:00:00Z', current: true, items: [] },
          snapshots: [{ id: 1, snapshotNumber: 1, createdAt: '2026-08-27T12:00:00Z', current: true, items: [] }],
          renderedText: null,
        }}
        entries={entries}
        onClose={vi.fn()}
        onDrop={vi.fn()}
        onReopen={vi.fn()}
        reordering={false}
        showId={1}
      />,
    )

    expect(screen.getByText(/sobald allen 15 Snapshot-Beiträgen Teilnehmer und damit Länder zugeordnet sind/)).toBeVisible()
    expect(screen.queryByRole('link', { name: 'Textdatei herunterladen' })).not.toBeInTheDocument()
  })

  it('renders the completed CSC output and exposes the matching text-file endpoint', () => {
    const snapshotText = 'Platz #1 - Deutschland: Snapshot Artist - Snapshot Title\nPlatz #2 - Schottland: … - …'
    render(
      <BallotPanel
        ballot={{
          ballotClosedAt: '2026-08-27T12:00:00Z',
          currentSnapshot: { id: 1, snapshotNumber: 1, createdAt: '2026-08-27T12:00:00Z', current: true, items: [] },
          snapshots: [{ id: 1, snapshotNumber: 1, createdAt: '2026-08-27T12:00:00Z', current: true, items: [] }],
          renderedText: snapshotText,
        }}
        entries={entries}
        onClose={vi.fn()}
        onDrop={vi.fn()}
        onReopen={vi.fn()}
        reordering={false}
        showId={1}
      />,
    )

    expect(screen.getByRole('textbox')).toHaveValue(snapshotText)
    expect(screen.getByRole('link', { name: 'Textdatei herunterladen' })).toHaveAttribute('href', '/api/shows/1/ballot/export')
  })
})
