import { render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '../../app/App'
import type { Candidate } from './api'

const youtubeUrl = 'https://www.youtube.com/watch?v=dQw4w9WgXcQ'

const selectedCandidate: Candidate = {
  id: 1,
  mottoShowId: 1,
  artist: 'VOLA',
  title: 'Break My Lying Tongue',
  youtubeUrl,
  comment: null,
  status: 'FINALIST',
  manualPosition: 1,
  createdAt: '2026-08-27T00:00:00Z',
  updatedAt: '2026-08-27T00:00:00Z',
}

const otherCandidate: Candidate = {
  ...selectedCandidate,
  id: 2,
  artist: 'Monster Magnet',
  title: 'Negasonic Teenage Warhead',
  status: 'ENGERE_AUSWAHL',
  manualPosition: 2,
}

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), { headers: { 'Content-Type': 'application/json' } })
}

describe('candidate alignment polish', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    window.history.pushState({}, '', '/shows/1/candidates')
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockReset()
  })

  afterEach(() => vi.unstubAllGlobals())

  it('keeps submission and status actions present in a stable candidate action rail', async () => {
    fetchMock.mockImplementation(async (input) => {
      const path = String(input)
      if (path === '/api/shows') {
        return jsonResponse([{
          id: 1,
          showNumber: 1,
          name: 'Super Men',
          candidateCount: 2,
          selectedCandidate: { id: 1, artist: selectedCandidate.artist, title: selectedCandidate.title, youtubeUrl },
        }])
      }
      if (path === '/api/shows/1/candidates') return jsonResponse([selectedCandidate, otherCandidate])
      throw new Error(`Unexpected request ${path}`)
    })

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Break My Lying Tongue', level: 3 })).toBeVisible()
    expect(screen.getByText('Einreichung')).toBeVisible()

    const selectedSubmissionAction = screen.getByRole('button', { name: 'Bereits als Einreichung gewählt' })
    expect(selectedSubmissionAction).toBeVisible()
    expect(selectedSubmissionAction).toBeDisabled()

    const availableSubmissionAction = screen.getByRole('button', { name: 'Als Einreichung wählen' })
    expect(availableSubmissionAction).toBeVisible()
    expect(availableSubmissionAction).toBeEnabled()

    expect(screen.getByLabelText('Status von VOLA')).toHaveTextContent('Finalist')
    expect(screen.getByLabelText('Status von Monster Magnet')).toHaveTextContent('Engere Auswahl')
  })
})
