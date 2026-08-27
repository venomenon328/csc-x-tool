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
  it('moves an entry from the unranked pool into the ranked list and recalculates its position', () => {
    const moved = applyBallotDrop(splitBallotEntries(entries), drop('3', 'unranked-entries', 0, 'ranked-entries', 1))

    expect(moved.ranked.map((entry) => entry.id)).toEqual([1, 3, 2])
    expect(moved.unranked).toEqual([])
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
    expect(onOptimisticChange).toHaveBeenCalledWith(expect.objectContaining({ ranked: expect.arrayContaining([expect.objectContaining({ id: 3 })]) }))
    expect(onConfirmedChange).toHaveBeenLastCalledWith(confirmed)
  })
})
