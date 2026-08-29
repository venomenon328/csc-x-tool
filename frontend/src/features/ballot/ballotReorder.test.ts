import type { DropResult } from '@hello-pangea/dnd'
import { describe, expect, it, vi } from 'vitest'
import type { ContestEntry } from '../entries/api'
import { ENTRY_POOL_DROPPABLE_ID, RANKING_DROPPABLE_ID, applyRankingDrop, persistDroppedBallot } from './ballotReorder'

const entries: ContestEntry[] = [
  { id: 1, mottoShowId: 1, artist: 'One', title: 'A', youtubeUrl: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', comment: null, assessment: null, assessmentConfidence: null, poolPosition: 2, rankingPosition: 1, participantId: null, createdAt: '', updatedAt: '' },
  { id: 2, mottoShowId: 1, artist: 'Two', title: 'B', youtubeUrl: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', comment: null, assessment: null, assessmentConfidence: null, poolPosition: 3, rankingPosition: 2, participantId: null, createdAt: '', updatedAt: '' },
  { id: 3, mottoShowId: 1, artist: 'Three', title: 'C', youtubeUrl: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', comment: null, assessment: null, assessmentConfidence: null, poolPosition: 1, rankingPosition: null, participantId: null, createdAt: '', updatedAt: '' },
]

function drop(draggableId: string, sourceList: string, sourceIndex: number, destinationList: string, destinationIndex: number): DropResult {
  return { draggableId, type: 'DEFAULT', source: { droppableId: sourceList, index: sourceIndex }, destination: { droppableId: destinationList, index: destinationIndex }, reason: 'DROP', mode: 'FLUID', combine: null }
}

describe('ballot ranking interactions', () => {
  it('adds an unranked pool entry to the ranking without changing pool positions', () => {
    const moved = applyRankingDrop(entries, drop('pool-entry-3', ENTRY_POOL_DROPPABLE_ID, 0, RANKING_DROPPABLE_ID, 1))

    expect(moved.filter((entry) => entry.rankingPosition !== null).sort((left, right) => left.rankingPosition! - right.rankingPosition!).map((entry) => entry.id)).toEqual([1, 3, 2])
    expect(moved.map((entry) => [entry.id, entry.poolPosition])).toEqual([[1, 2], [2, 3], [3, 1]])
  })

  it('moves an already ranked pool entry instead of duplicating it', () => {
    const moved = applyRankingDrop(entries, drop('pool-entry-1', ENTRY_POOL_DROPPABLE_ID, 0, RANKING_DROPPABLE_ID, 2))

    expect(moved.filter((entry) => entry.rankingPosition !== null).map((entry) => [entry.id, entry.rankingPosition])).toEqual([[1, 2], [2, 1]])
    expect(moved.filter((entry) => entry.id === 1)).toHaveLength(1)
  })

  it('accounts for the existing destination representation when a ranked pool entry moves downward', () => {
    const threeRanked = entries.map((entry) => entry.id === 3 ? { ...entry, rankingPosition: 3 } : entry)
    const moved = applyRankingDrop(threeRanked, drop('pool-entry-1', ENTRY_POOL_DROPPABLE_ID, 1, RANKING_DROPPABLE_ID, 2))

    expect(moved.filter((entry) => entry.rankingPosition !== null).sort((left, right) => left.rankingPosition! - right.rankingPosition!).map((entry) => entry.id)).toEqual([2, 1, 3])
  })

  it('reorders directly inside the compact ranking list', () => {
    const threeRanked = entries.map((entry) => entry.id === 3 ? { ...entry, rankingPosition: 3 } : entry)
    const moved = applyRankingDrop(threeRanked, drop('ranking-entry-1', RANKING_DROPPABLE_ID, 0, RANKING_DROPPABLE_ID, 2))

    expect(moved.filter((entry) => entry.rankingPosition !== null).sort((left, right) => left.rankingPosition! - right.rankingPosition!).map((entry) => entry.id)).toEqual([2, 3, 1])
  })

  it('removes only a ranking position when a ranking entry is dropped into the pool', () => {
    const moved = applyRankingDrop(entries, drop('ranking-entry-1', RANKING_DROPPABLE_ID, 0, ENTRY_POOL_DROPPABLE_ID, 0))

    expect(moved.map((entry) => [entry.id, entry.poolPosition, entry.rankingPosition])).toEqual([[1, 2, null], [2, 3, 1], [3, 1, null]])
  })

  it('normalizes a destination below the generous ranking end zone to append', () => {
    const moved = applyRankingDrop(entries, drop('pool-entry-3', ENTRY_POOL_DROPPABLE_ID, 0, RANKING_DROPPABLE_ID, 99))

    expect(moved.filter((entry) => entry.rankingPosition !== null).sort((left, right) => left.rankingPosition! - right.rankingPosition!).map((entry) => entry.id)).toEqual([1, 2, 3])
  })

  it('sends a complete ranking payload and rolls back only ranking positions when persistence fails', async () => {
    const failure = new Error('conflict')
    const save = vi.fn().mockRejectedValue(failure)
    const onOptimisticChange = vi.fn()
    const onConfirmedChange = vi.fn()

    await expect(persistDroppedBallot({
      result: drop('pool-entry-3', ENTRY_POOL_DROPPABLE_ID, 0, RANKING_DROPPABLE_ID, 1),
      confirmedEntries: entries,
      save,
      onOptimisticChange,
      onConfirmedChange,
    })).rejects.toBe(failure)

    expect(save).toHaveBeenCalledWith({ rankedEntryIds: [1, 3, 2], unrankedEntryIds: [] })
    expect(onOptimisticChange).toHaveBeenCalledWith(expect.arrayContaining([expect.objectContaining({ id: 3, rankingPosition: 2, poolPosition: 1 })]))
    expect(onConfirmedChange).toHaveBeenLastCalledWith(entries)
  })
})
