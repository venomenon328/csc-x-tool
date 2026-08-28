import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Candidate } from './api'
import { YoutubePlayerPanel } from '../songs/YoutubePlayerPanel'

const candidate: Candidate = {
  id: 1, mottoShowId: 1, artist: 'Interpret', title: 'Titel', comment: null, status: 'OFFEN', manualPosition: 1,
  youtubeUrl: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42', createdAt: '2026-08-27T00:00:00Z', updatedAt: '2026-08-27T00:00:00Z',
}

describe('YoutubePlayerPanel', () => {
  it('renders a compact non-alert empty state', () => {
    render(<YoutubePlayerPanel contextLabel="Aktuell ausgewählter Kandidat" emptyMessage="Leer" song={null} />)

    expect(screen.getByRole('status', { name: 'Kein Song ausgewählt' })).toHaveTextContent('Leer')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('uses the privacy-enhanced embed with the requested start time while retaining the external link', () => {
    render(<YoutubePlayerPanel contextLabel="Aktuell ausgewählter Kandidat" emptyMessage="Leer" song={candidate} />)
    expect(screen.getByRole('heading', { name: 'Titel', level: 2 })).toBeVisible()
    expect(screen.getByText('Interpret')).toBeVisible()
    expect(screen.getByTitle('YouTube: Interpret – Titel')).toHaveAttribute('src', 'https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?start=42')
    expect(screen.getByRole('link', { name: 'Auf YouTube öffnen' })).toHaveAttribute('href', candidate.youtubeUrl)
  })

  it('shows a fallback only for the candidate whose embedded player failed', async () => {
    const { rerender } = render(<YoutubePlayerPanel contextLabel="Aktuell ausgewählter Kandidat" emptyMessage="Leer" song={candidate} />)
    fireEvent(screen.getByTitle('YouTube: Interpret – Titel'), new Event('error', { bubbles: true }))

    await waitFor(() => expect(screen.getByText('Der eingebettete Player konnte lokal nicht geladen werden. Der externe Link bleibt verfügbar.')).toBeVisible())
    expect(screen.getByRole('link', { name: 'Auf YouTube öffnen' })).toHaveAttribute('href', candidate.youtubeUrl)

    const otherCandidate = { ...candidate, id: 2, artist: 'Andere', title: 'Kandidatin' }
    rerender(<YoutubePlayerPanel contextLabel="Aktuell ausgewählter Kandidat" emptyMessage="Leer" song={otherCandidate} />)

    expect(screen.getByTitle('YouTube: Andere – Kandidatin')).toBeVisible()
    expect(screen.queryByText('Der eingebettete Player konnte lokal nicht geladen werden. Der externe Link bleibt verfügbar.')).not.toBeInTheDocument()
  })
})
