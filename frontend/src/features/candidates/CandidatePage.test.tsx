import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '../../app/App'
import type { Candidate } from './api'

const candidate: Candidate = {
  id: 1, mottoShowId: 1, artist: 'Original', title: 'Titel', youtubeUrl: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ',
  comment: null, status: 'OFFEN', manualPosition: 1, createdAt: '2026-08-27T00:00:00Z', updatedAt: '2026-08-27T00:00:00Z',
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('CandidatePage', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    window.history.pushState({}, '', '/shows/1/candidates')
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockReset()
  })

  afterEach(() => vi.unstubAllGlobals())

  it('creates a candidate and displays server-side validation errors', async () => {
    const user = userEvent.setup()
    let postAttempts = 0
    fetchMock.mockImplementation(async (input, init) => {
      const path = String(input)
      if (path === '/api/shows') return jsonResponse([{ id: 1, showNumber: 1, name: 'Show Eins', candidateCount: 0, selectedCandidate: null }])
      if (path === '/api/shows/1/candidates' && init?.method === 'POST') {
        postAttempts += 1
        return postAttempts === 1
          ? jsonResponse({ timestamp: '2026-08-27T00:00:00Z', status: 400, code: 'INVALID_YOUTUBE_URL', message: 'Bitte gib einen gültigen YouTube-Video-Link an.', path }, 400)
          : jsonResponse({ ...candidate, artist: 'Neu', title: 'Song' }, 201)
      }
      if (path === '/api/shows/1/candidates') return jsonResponse([])
      throw new Error(`Unexpected request ${path}`)
    })
    render(<App />)

    await screen.findByRole('heading', { name: 'Show Eins' })
    const quickEntry = screen.getByRole('heading', { name: 'Kandidaten schnell erfassen' }).closest('section')
    if (quickEntry === null) throw new Error('Quick entry section missing')
    const [artist, title, youtubeUrl] = within(quickEntry).getAllByRole('textbox')
    await user.type(artist, 'Neu')
    await user.type(title, 'Song')
    await user.type(youtubeUrl, 'https://invalid.example/video')
    await user.click(screen.getByRole('button', { name: 'Kandidat anlegen' }))
    expect(await screen.findByText('Bitte gib einen gültigen YouTube-Video-Link an.')).toBeVisible()

    await user.clear(youtubeUrl)
    await user.type(youtubeUrl, 'https://youtu.be/dQw4w9WgXcQ')
    await user.click(screen.getByRole('button', { name: 'Kandidat anlegen' }))
    await screen.findByText('Neu – Song')
    expect(fetchMock).toHaveBeenCalledWith('/api/shows/1/candidates', expect.objectContaining({
      method: 'POST', body: JSON.stringify({ artist: 'Neu', title: 'Song', youtubeUrl: 'https://youtu.be/dQw4w9WgXcQ', comment: '' }),
    }))
  })

  it('offers multi-show copying and conscious submission replacement and clearing', async () => {
    const user = userEvent.setup()
    const shows = [
      { id: 1, showNumber: 1, name: 'Show Eins', candidateCount: 1, selectedCandidate: { id: 99, artist: 'Alt', title: 'Beitrag', youtubeUrl: candidate.youtubeUrl } },
      { id: 2, showNumber: 2, name: 'Show Zwei', candidateCount: 0, selectedCandidate: null },
    ]
    fetchMock.mockImplementation(async (input, init) => {
      const path = String(input)
      if (path === '/api/shows') return jsonResponse(shows)
      if (path === '/api/shows/1/candidates') return jsonResponse([candidate])
      if (path === '/api/shows/1/candidates/1/copy') return jsonResponse([], 201)
      if (path === '/api/shows/1/submission' && init?.method === 'PUT') return jsonResponse(candidate)
      if (path === '/api/shows/1/submission' && init?.method === 'DELETE') return new Response(null, { status: 204 })
      throw new Error(`Unexpected request ${path}`)
    })
    render(<App />)

    await screen.findByText('Original – Titel')
    await user.click(screen.getByRole('button', { name: 'In andere Show kopieren' }))
    expect(screen.getByRole('heading', { name: 'In andere Mottoshow kopieren' })).toBeVisible()
    await user.click(screen.getByLabelText('Show 2: Show Zwei'))
    await user.click(screen.getByRole('button', { name: 'Kopieren' }))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/shows/1/candidates/1/copy', expect.objectContaining({
      body: JSON.stringify({ targetShowIds: [2] }),
    })))
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'In andere Mottoshow kopieren' })).not.toBeInTheDocument())

    await user.click(screen.getByRole('button', { name: 'Als Einreichung wählen' }))
    expect(screen.getByRole('heading', { name: 'Einreichung bewusst ersetzen?' })).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Einreichung ersetzen' }))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/shows/1/submission', expect.objectContaining({
      method: 'PUT', body: JSON.stringify({ candidateId: 1, confirmReplacement: true }),
    })))
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Einreichung bewusst ersetzen?' })).not.toBeInTheDocument())

    await user.click(screen.getByRole('button', { name: 'Aufheben' }))
    expect(screen.getByRole('heading', { name: 'Einreichung aufheben?' })).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Einreichung aufheben' }))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/shows/1/submission', { method: 'DELETE' }))
  })
})
