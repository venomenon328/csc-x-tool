import { render, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '../../app/App'

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), { headers: { 'Content-Type': 'application/json' } })
}

describe('HistoricalShowPage', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    window.history.pushState({}, '', '/historical-shows/1')
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockReset()
  })

  afterEach(() => vi.unstubAllGlobals())

  it('renders a confirmed historical song list without a player for a missing source URL', async () => {
    fetchMock.mockImplementation(async (input) => {
      const path = String(input)
      if (path === '/api/shows/1') return jsonResponse({
        id: 1, contestId: 2, showNumber: 3, name: 'Archivthema', entryListComplete: true,
      })
      if (path === '/api/shows/1/entries') return jsonResponse([{
        id: 9, mottoShowId: 1, artist: 'Ohne Link', title: 'Archivsong', youtubeUrl: null, comment: null,
        assessment: null, assessmentConfidence: null, poolPosition: 1, rankingPosition: null, participantId: 7,
        createdAt: '2026-08-30T00:00:00Z', updatedAt: '2026-08-30T00:00:00Z',
      }])
      if (path === '/api/contests/2/participants?includeInactive=true') return jsonResponse([{
        id: 7, participationId: 17, displayName: 'Cortez', countryCode: 'FI', countryName: 'Finnland', active: true,
        aliases: [], createdAt: '2026-08-30T00:00:00Z', updatedAt: '2026-08-30T00:00:00Z',
      }])
      if (path === '/api/contests') return jsonResponse([])
      return new Response(null, { status: 204 })
    })

    render(<App />)

    await screen.findByRole('heading', { name: 'Show 3 · Archivthema' })
    expect(screen.getByText('Songliste: vollständig bestätigt')).toBeVisible()
    const songList = screen.getByRole('table', { name: 'Historische Songliste' })
    expect(within(songList).getByText('Ohne Link')).toBeVisible()
    expect(within(songList).getByText('Archivsong')).toBeVisible()
    expect(within(songList).getByText('Cortez')).toBeVisible()
    expect(within(songList).getByText('—')).toBeVisible()
    expect(screen.queryByText('Link öffnen')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('YouTube-Player')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Songliste wieder öffnen' })).toBeVisible()
  })
})
