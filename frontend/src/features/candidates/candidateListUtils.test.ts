import { describe, expect, it } from 'vitest'
import type { Candidate } from './api'
import { moveCandidate, visibleCandidates } from './candidateListUtils'

const candidates: Candidate[] = [
  { id: 1, mottoShowId: 1, artist: 'Zara', title: 'Alpha', youtubeUrl: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', comment: null, status: 'FINALIST', manualPosition: 1, createdAt: '2026-08-01T00:00:00Z', updatedAt: '2026-08-01T00:00:00Z' },
  { id: 2, mottoShowId: 1, artist: 'Anna', title: 'Zulu', youtubeUrl: 'https://www.youtube.com/watch?v=9bZkp7q19f0', comment: null, status: 'OFFEN', manualPosition: 2, createdAt: '2026-08-02T00:00:00Z', updatedAt: '2026-08-02T00:00:00Z' },
  { id: 3, mottoShowId: 1, artist: 'Berta', title: 'Beta', youtubeUrl: 'https://www.youtube.com/watch?v=3JZ_D3ELwOQ', comment: null, status: 'VERWORFEN', manualPosition: 3, createdAt: '2026-08-03T00:00:00Z', updatedAt: '2026-08-03T00:00:00Z' },
]

describe('candidate list presentation', () => {
  it('combines case-insensitive search, status and rejected filters without changing manual order', () => {
    expect(visibleCandidates(candidates, 'ANNA', 'ALL', true, 'MANUAL').map(({ id }) => id)).toEqual([2])
    expect(visibleCandidates(candidates, '', 'OFFEN', true, 'MANUAL').map(({ id }) => id)).toEqual([2])
    expect(visibleCandidates(candidates, '', 'ALL', false, 'MANUAL').map(({ id }) => id)).toEqual([1, 2])
    expect(visibleCandidates(candidates, '', 'ALL', true, 'MANUAL').map(({ id }) => id)).toEqual([1, 2, 3])
  })

  it('uses the domain status order for temporary sorting and restores manual order', () => {
    expect(visibleCandidates(candidates, '', 'ALL', true, 'STATUS').map(({ id }) => id)).toEqual([2, 1, 3])
    expect(visibleCandidates(candidates, '', 'ALL', true, 'ARTIST').map(({ id }) => id)).toEqual([2, 3, 1])
    expect(visibleCandidates(candidates, '', 'ALL', true, 'MANUAL').map(({ id }) => id)).toEqual([1, 2, 3])
  })

  it('makes a full optimistic order with new 1-based positions and leaves its input untouched', () => {
    const reordered = moveCandidate(candidates, 2, 0)
    expect(reordered.map(({ id, manualPosition }) => [id, manualPosition])).toEqual([[3, 1], [1, 2], [2, 3]])
    expect(candidates.map(({ id, manualPosition }) => [id, manualPosition])).toEqual([[1, 1], [2, 2], [3, 3]])
  })
})
