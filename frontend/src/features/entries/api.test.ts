import { afterEach, describe, expect, it, vi } from 'vitest'
import { updateEntry, updateHistoricalEntry, type ContestEntry } from './api'

const assignedEntry: ContestEntry = {
  id: 17,
  mottoShowId: 3,
  artist: 'Artist',
  title: 'Title',
  youtubeUrl: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ',
  comment: 'Comment',
  assessment: null,
  assessmentConfidence: null,
  poolPosition: 1,
  rankingPosition: null,
  participantId: 42,
  createdAt: '2026-08-30T00:00:00Z',
  updatedAt: '2026-08-30T00:00:00Z',
}

describe('entry API client', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('does not resend an existing participant assignment during current metadata updates', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify(assignedEntry), {
      status: 200, headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)

    await updateEntry(3, assignedEntry)

    expect(fetchMock).toHaveBeenCalledWith('/api/shows/3/entries/17', expect.objectContaining({
      method: 'PATCH',
      body: JSON.stringify({
        artist: 'Artist', title: 'Title', youtubeUrl: assignedEntry.youtubeUrl, comment: 'Comment',
      }),
    }))
  })

  it('includes the participant assignment only for historical metadata updates', async () => {
    const historical = { ...assignedEntry, youtubeUrl: null }
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify(historical), {
      status: 200, headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)

    await updateHistoricalEntry(30, historical)

    expect(fetchMock).toHaveBeenCalledWith('/api/shows/30/entries/17', expect.objectContaining({
      method: 'PATCH',
      body: JSON.stringify({
        artist: 'Artist', title: 'Title', youtubeUrl: null, comment: 'Comment', participantId: 42,
      }),
    }))
  })
})
