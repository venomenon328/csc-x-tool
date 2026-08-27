import type { DropResult } from '@hello-pangea/dnd'
import { describe, expect, it, vi } from 'vitest'
import type { ContestEntry } from '../entries/api'
import { applyBallotDrop, persistDroppedBallot, splitBallotEntries } from './ballotReorder'

const entries: ContestEntry[] = [
  { id: 1, mottoShowId: 1, artist: 'One', title: 'A', youtubeUrl: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', comment: null, listened: false, relisten: false, rankingPosition: 1, participantId: null, createdAt: '', updatedAt: '' },
  { id: 2, mottoShowId: 1, artist: 'Two', title: 'B', youtubeUrl: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', comment: null, listened: false, relisten: false, rankingPosition: 2, participantId: null, createdAt: '', updatedAt: '' },
  { id: 3, mottoShowId: 1, artist: 'Three', title: 'C', youtubeUrl: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', comment: null, listened: false, relisten: false, rankingPosition: null, participantId: null, createdAt: '', updatedAt: '' },
]

function drop(sourceId: string, sourceList: string, sourceIndex: number, destinationList: string, destinationIndex: number): DropResult {
  return {
    draggableId: sourceId,
    type: 'DEFAULT',
    source: { droppableId: sourceList, index: sourceIndex },
    destination: { droppableId: destinationList, index: destinationIndex },
    reason: 'DROP',
    mode: 'FLUID',
    combine: null,
  }
}

describe('ballot ranking interactions', () => {
  it('moves an entry from the unranked pool into the ranked list and immediately recalculates positions', () => {
    const moved = applyBallotDrop(splitBallotEntries(entries), drop('3', 'unranked-entries', 0, 'ranked-entries', 1))

    expect(moved.ranked.map((entry) => entry.id)).toEqual([1, 3, 2])
    expect(moved.ranked.map((entry) => entry.rankingPosition)).toEqual([1, 2, 3])
    expect(moved.unranked).toEqual([])
  })

  it('clears the moved rank and compacts the remaining positions when returning an entry to the pool', () => {
    const moved = applyBallotDrop(splitBallotEntries(entries), drop('1', 'ranked-entries', 0, 'unranked-entries', 0))

    expect(moved.ranked.map((entry) => [entry.id, entry.rankingPosition])).toEqual([[2, 1]])
    expect(moved.unranked.map((entry) => [entry.id, entry.rankingPosition])).toEqual([[1, null], [3, null]])
  })

  it('sends one complete two-list state and rolls back to the last server-confirmed lists when persistence fails', async () => {
    const confirmed = splitBallotEntries(entries)
    const failure = new Error('conflict')
    const save = vi.fn().mockRejectedValue(failure)
    const onOptimisticChange = vi.fn()
    const onConfirmedChange = vi.fn()

    await expect(persistDroppedBallot({
      result: drop('3', 'unranked-entries', 0, 'ranked-entries', 1),
      confirmed,
      save,
      onOptimisticChange,
      onConfirmedChange,
    })).rejects.toBe(failure)

    expect(save).toHaveBeenCalledWith({ rankedEntryIds: [1, 3, 2], unrankedEntryIds: [] })
    expect(onOptimisticChange).toHaveBeenCalledWith(expect.objectContaining({
      ranked: [
        expect.objectContaining({ id: 1, rankingPosition: 1 }),
        expect.objectContaining({ id: 3, rankingPosition: 2 }),
        expect.objectContaining({ id: 2, rankingPosition: 3 }),
      ],
    }))
    expect(onConfirmedChange).toHaveBeenLastCalledWith(confirmed)
  })
})
