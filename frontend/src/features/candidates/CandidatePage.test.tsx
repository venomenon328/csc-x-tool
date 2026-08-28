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
    expect(screen.getByRole('button', { name: 'Erfassung einklappen' })).toHaveAttribute('aria-expanded', 'true')
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
    expect(await screen.findByRole('heading', { name: 'Song', level: 3 })).toBeVisible()
    expect(screen.getByText('Neu')).toBeVisible()
    expect(fetchMock).toHaveBeenCalledWith('/api/shows/1/candidates', expect.objectContaining({
      method: 'POST', body: JSON.stringify({ artist: 'Neu', title: 'Song', youtubeUrl: 'https://youtu.be/dQw4w9WgXcQ', comment: '' }),
    }))
  })

  it('starts quick entry collapsed for existing candidates and keeps a draft when toggled', async () => {
    const user = userEvent.setup()
    fetchMock.mockImplementation(async (input) => {
      const path = String(input)
      if (path === '/api/shows') return jsonResponse([{ id: 1, showNumber: 1, name: 'Show Eins', candidateCount: 1, selectedCandidate: null }])
      if (path === '/api/shows/1/candidates') return jsonResponse([candidate])
      throw new Error(`Unexpected request ${path}`)
    })
    render(<App />)

    await screen.findByRole('heading', { name: 'Titel', level: 3 })
    expect(screen.getByText('Original')).toBeVisible()
    const addButton = screen.getByRole('button', { name: 'Kandidat hinzufügen' })
    expect(addButton).toHaveAttribute('aria-expanded', 'false')
    await user.click(addButton)
    await waitFor(() => expect(addButton).toHaveAttribute('aria-expanded', 'true'))
    const quickEntry = screen.getByRole('heading', { name: 'Kandidaten schnell erfassen' }).closest('section')
    if (quickEntry === null) throw new Error('Quick entry section missing')
    const [artist] = await within(quickEntry).findAllByRole('textbox')
    await user.type(artist, 'Entwurf')
    await user.click(screen.getByRole('button', { name: 'Erfassung einklappen' }))
    await user.click(screen.getByRole('button', { name: 'Kandidat hinzufügen' }))
    const [reopenedArtist] = await within(quickEntry).findAllByRole('textbox')
    expect(reopenedArtist).toHaveValue('Entwurf')
  })

  it('presents the submission as a compact surface and keeps copy, open, replace and clear workflows', async () => {
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

    await screen.findByRole('heading', { name: 'Titel', level: 3 })
    const submission = screen.getByRole('region', { name: 'Eigene Einreichung' })
    expect(within(submission).getByRole('heading', { name: 'Beitrag', level: 2 })).toBeVisible()
    expect(within(submission).getByText('Alt')).toBeVisible()
    expect(within(submission).getByRole('button', { name: 'Interpret & Titel kopieren' })).toBeVisible()
    expect(within(submission).getByRole('button', { name: 'Link kopieren' })).toBeVisible()
    expect(within(submission).getByRole('link', { name: 'Auf YouTube öffnen' })).toHaveAttribute('href', candidate.youtubeUrl)

    await user.click(screen.getByRole('button', { name: 'Weitere Aktionen für Original – Titel' }))
    await user.click(screen.getByRole('menuitem', { name: 'In andere Show kopieren' }))
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

  it('uses direct submission selection when no submission exists yet', async () => {
    const user = userEvent.setup()
    const shows = [{ id: 1, showNumber: 1, name: 'Show Eins', candidateCount: 1, selectedCandidate: null }]
    fetchMock.mockImplementation(async (input, init) => {
      const path = String(input)
      if (path === '/api/shows') return jsonResponse(shows)
      if (path === '/api/shows/1/candidates') return jsonResponse([candidate])
      if (path === '/api/shows/1/submission' && init?.method === 'PUT') return jsonResponse(candidate)
      throw new Error(`Unexpected request ${path}`)
    })
    render(<App />)

    await screen.findByRole('heading', { name: 'Titel', level: 3 })
    expect(screen.getByRole('region', { name: 'Eigene Einreichung' })).toHaveTextContent('Noch nicht festgelegt')
    await user.click(screen.getByRole('button', { name: 'Als Einreichung wählen' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/shows/1/submission', expect.objectContaining({
      method: 'PUT', body: JSON.stringify({ candidateId: 1, confirmReplacement: false }),
    })))
    expect(screen.queryByRole('dialog', { name: 'Einreichung bewusst ersetzen?' })).not.toBeInTheDocument()
  })

  it('hides rejected candidates by default and shows them through the compact filter toggle', async () => {
    const user = userEvent.setup()
    const rejectedCandidate = { ...candidate, id: 2, artist: 'Verworfen', title: 'Verworfen Song', status: 'VERWORFEN' as const, manualPosition: 2 }
    fetchMock.mockImplementation(async (input) => {
      const path = String(input)
      if (path === '/api/shows') return jsonResponse([{ id: 1, showNumber: 1, name: 'Show Eins', candidateCount: 2, selectedCandidate: null }])
      if (path === '/api/shows/1/candidates') return jsonResponse([candidate, rejectedCandidate])
      throw new Error(`Unexpected request ${path}`)
    })
    render(<App />)

    await screen.findByRole('heading', { name: 'Titel', level: 3 })
    const rejectedToggle = screen.getByRole('button', { name: 'Verworfene anzeigen' })
    expect(rejectedToggle).toHaveAttribute('aria-pressed', 'false')
    expect(screen.queryByRole('heading', { name: 'Verworfen Song', level: 3 })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Original verschieben' })).toBeEnabled()

    await user.click(rejectedToggle)
    expect(rejectedToggle).toHaveAttribute('aria-pressed', 'true')
    expect(await screen.findByRole('heading', { name: 'Verworfen Song', level: 3 })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Verworfen verschieben' })).toBeEnabled()
  })

  it('keeps drag-and-drop enabled for a fresh manual list without rejected candidates', async () => {
    fetchMock.mockImplementation(async (input) => {
      const path = String(input)
      if (path === '/api/shows') return jsonResponse([{ id: 1, showNumber: 1, name: 'Show Eins', candidateCount: 1, selectedCandidate: null }])
      if (path === '/api/shows/1/candidates') return jsonResponse([candidate])
      throw new Error(`Unexpected request ${path}`)
    })
    render(<App />)

    await screen.findByRole('heading', { name: 'Titel', level: 3 })
    expect(screen.getByRole('button', { name: 'Original verschieben' })).toBeEnabled()
    expect(screen.queryByText(/Drag-and-drop ist nur bei manueller Reihenfolge/)).not.toBeInTheDocument()
  })

  it('marks the currently played candidate textually as well as visually', async () => {
    const user = userEvent.setup()
    fetchMock.mockImplementation(async (input) => {
      const path = String(input)
      if (path === '/api/shows') return jsonResponse([{ id: 1, showNumber: 1, name: 'Show Eins', candidateCount: 1, selectedCandidate: null }])
      if (path === '/api/shows/1/candidates') return jsonResponse([candidate])
      throw new Error(`Unexpected request ${path}`)
    })
    render(<App />)

    await screen.findByRole('heading', { name: 'Titel', level: 3 })
    await user.click(screen.getByRole('button', { name: 'Original – Titel anhören' }))
    expect(await screen.findByText('Wird angehört')).toBeVisible()
    expect(screen.getByLabelText('YouTube-Player')).toBeVisible()
  })

  it('requires clearing the active submission before the overflow delete action becomes available', async () => {
    const user = userEvent.setup()
    const selectedCandidate = { id: candidate.id, artist: candidate.artist, title: candidate.title, youtubeUrl: candidate.youtubeUrl }
    let deletionAllowed = false
    fetchMock.mockImplementation(async (input, init) => {
      const path = String(input)
      if (path === '/api/shows') return jsonResponse([{ id: 1, showNumber: 1, name: 'Show Eins', candidateCount: 1, selectedCandidate: deletionAllowed ? null : selectedCandidate }])
      if (path === '/api/shows/1/candidates') return jsonResponse([candidate])
      if (path === '/api/shows/1/submission' && init?.method === 'DELETE') {
        deletionAllowed = true
        return new Response(null, { status: 204 })
      }
      if (path === '/api/shows/1/candidates/1' && init?.method === 'DELETE') return new Response(null, { status: 204 })
      throw new Error(`Unexpected request ${path}`)
    })
    render(<App />)

    await screen.findByRole('heading', { name: 'Titel', level: 3 })
    const overflow = screen.getByRole('button', { name: 'Weitere Aktionen für Original – Titel' })
    await user.click(overflow)
    expect(screen.getByRole('menuitem', { name: 'Löschen' })).toHaveAttribute('aria-disabled', 'true')
    expect(fetchMock).not.toHaveBeenCalledWith('/api/shows/1/candidates/1', expect.objectContaining({ method: 'DELETE' }))
    await user.keyboard('{Escape}')

    await user.click(screen.getByRole('button', { name: 'Aufheben' }))
    await user.click(screen.getByRole('button', { name: 'Einreichung aufheben' }))
    await waitFor(() => expect(screen.getByRole('region', { name: 'Eigene Einreichung' })).toHaveTextContent('Noch nicht festgelegt'))

    await user.click(screen.getByRole('button', { name: 'Weitere Aktionen für Original – Titel' }))
    expect(screen.getByRole('menuitem', { name: 'Löschen' })).not.toHaveAttribute('aria-disabled', 'true')
  })

  it('does not delete a non-selected candidate before its overflow confirmation', async () => {
    const user = userEvent.setup()
    fetchMock.mockImplementation(async (input, init) => {
      const path = String(input)
      if (path === '/api/shows') return jsonResponse([{ id: 1, showNumber: 1, name: 'Show Eins', candidateCount: 1, selectedCandidate: null }])
      if (path === '/api/shows/1/candidates') return jsonResponse([candidate])
      if (path === '/api/shows/1/candidates/1' && init?.method === 'DELETE') return new Response(null, { status: 204 })
      throw new Error(`Unexpected request ${path}`)
    })
    render(<App />)

    await screen.findByRole('heading', { name: 'Titel', level: 3 })
    await user.click(screen.getByRole('button', { name: 'Weitere Aktionen für Original – Titel' }))
    await user.click(screen.getByRole('menuitem', { name: 'Löschen' }))
    expect(screen.getByRole('heading', { name: 'Kandidat löschen?' })).toBeVisible()
    expect(fetchMock).not.toHaveBeenCalledWith('/api/shows/1/candidates/1', expect.objectContaining({ method: 'DELETE' }))

    await user.click(screen.getByRole('button', { name: 'Kandidat löschen' }))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/shows/1/candidates/1', { method: 'DELETE' }))
  })
})
