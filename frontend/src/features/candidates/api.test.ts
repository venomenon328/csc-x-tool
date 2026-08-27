import { afterEach, describe, expect, it, vi } from 'vitest'
import { reorderCandidates } from './api'

describe('candidate API client', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('sends exactly one complete ordered ID list after a drop', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response('[]', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await reorderCandidates(7, [33, 11, 22])

    expect(fetchMock).toHaveBeenCalledOnce()
    expect(fetchMock).toHaveBeenCalledWith('/api/shows/7/candidates/reorder', expect.objectContaining({
      method: 'PUT', body: JSON.stringify({ candidateIds: [33, 11, 22] }),
    }))
  })

  it('keeps structured reorder errors available to the caller for a rollback', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({
      timestamp: '2026-08-27T00:00:00Z', status: 409, code: 'CANDIDATE_REORDER_CONFLICT',
      message: 'Die Reihenfolge ist veraltet.', path: '/api/shows/7/candidates/reorder',
    }), { status: 409, headers: { 'Content-Type': 'application/json' } })))

    await expect(reorderCandidates(7, [33, 11, 22])).rejects.toMatchObject({
      apiError: expect.objectContaining({ code: 'CANDIDATE_REORDER_CONFLICT' }),
    })
  })
})
