import { describe, expect, it, vi } from 'vitest'
import type { ContestEntry } from './api'
import { movePoolEntry, persistDroppedPoolOrder, visiblePoolEntries } from './entryPoolOrder'

const entries: ContestEntry[] = [
  { id: 1, mottoShowId: 1, artist: 'Ärzte', title: 'Zwei', youtubeUrl: '', comment: null, assessment: 4, assessmentConfidence: 2, poolPosition: 2, rankingPosition: 2, participantId: null, createdAt: '2026-01-02T00:00:00Z', updatedAt: '' },
  { id: 2, mottoShowId: 1, artist: 'Björk', title: 'Eins', youtubeUrl: '', comment: null, assessment: null, assessmentConfidence: null, poolPosition: 1, rankingPosition: null, participantId: null, createdAt: '2026-01-01T00:00:00Z', updatedAt: '' },
  { id: 3, mottoShowId: 1, artist: 'Ärzte', title: 'Drei', youtubeUrl: '', comment: null, assessment: 4, assessmentConfidence: 4, poolPosition: 3, rankingPosition: 1, participantId: null, createdAt: '2026-01-03T00:00:00Z', updatedAt: '' },
]

const noFilters = { search: '', onlyUnassessed: false, onlyUncertain: false, onlyUnranked: false, onlyWithoutParticipant: false }

describe('entry pool ordering', () => {
  it('uses pool positions for manual order and as the stable tie-breaker for other sorts', () => {
    expect(visiblePoolEntries(entries, noFilters, 'MANUAL').map((entry) => entry.id)).toEqual([2, 1, 3])
    expect(visiblePoolEntries(entries, noFilters, 'ARTIST').map((entry) => entry.id)).toEqual([1, 3, 2])
    expect(visiblePoolEntries(entries, noFilters, 'ASSESSMENT').map((entry) => entry.id)).toEqual([3, 1, 2])
    expect(visiblePoolEntries(entries, noFilters, 'CONFIDENCE').map((entry) => entry.id)).toEqual([2, 1, 3])
    expect(visiblePoolEntries(entries, noFilters, 'RANK').map((entry) => entry.id)).toEqual([3, 1, 2])
  })

  it('filters unassessed entries and low-confidence assessments without changing pool order', () => {
    expect(visiblePoolEntries(entries, { ...noFilters, onlyUnassessed: true }, 'MANUAL').map((entry) => entry.id)).toEqual([2])
    expect(visiblePoolEntries(entries, { ...noFilters, onlyUncertain: true }, 'MANUAL').map((entry) => entry.id)).toEqual([1])
  })

  it('keeps pool reorder independent from ranking positions', () => {
    const moved = movePoolEntry(visiblePoolEntries(entries, noFilters, 'MANUAL'), 0, 2)

    expect(moved.map((entry) => [entry.id, entry.poolPosition, entry.rankingPosition])).toEqual([[1, 1, 2], [3, 2, 1], [2, 3, null]])
  })

  it('rolls back only the pool order when the separate pool endpoint rejects it', async () => {
    const confirmed = visiblePoolEntries(entries, noFilters, 'MANUAL')
    const failure = new Error('conflict')
    const save = vi.fn().mockRejectedValue(failure)
    const onOptimisticChange = vi.fn()
    const onConfirmedChange = vi.fn()
    const result = {
      draggableId: 'pool-entry-2', type: 'DEFAULT', source: { droppableId: 'entry-pool', index: 0 }, destination: { droppableId: 'entry-pool', index: 2 }, reason: 'DROP' as const, mode: 'FLUID' as const, combine: null,
    }

    await expect(persistDroppedPoolOrder({ result, confirmedEntries: confirmed, save, onOptimisticChange, onConfirmedChange })).rejects.toBe(failure)

    expect(save).toHaveBeenCalledWith([1, 3, 2])
    expect(onOptimisticChange).toHaveBeenCalledWith(expect.arrayContaining([expect.objectContaining({ id: 2, poolPosition: 3, rankingPosition: null })]))
    expect(onConfirmedChange).toHaveBeenLastCalledWith(confirmed)
  })
})
