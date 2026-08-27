import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Candidate } from './api'
import { YoutubePlayerPanel } from './YoutubePlayerPanel'

const candidate: Candidate = {
  id: 1, mottoShowId: 1, artist: 'Interpret', title: 'Titel', comment: null, status: 'OFFEN', manualPosition: 1,
  youtubeUrl: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42', createdAt: '2026-08-27T00:00:00Z', updatedAt: '2026-08-27T00:00:00Z',
}

describe('YoutubePlayerPanel', () => {
  it('uses the privacy-enhanced embed while retaining the external fallback link', () => {
    render(<YoutubePlayerPanel candidate={candidate} />)
    expect(screen.getByTitle('YouTube: Interpret – Titel')).toHaveAttribute('src', 'https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ')
    expect(screen.getByRole('link', { name: 'Auf YouTube öffnen' })).toHaveAttribute('href', candidate.youtubeUrl)
  })
})
