import type { DropResult } from '@hello-pangea/dnd'
import { describe, expect, it, vi } from 'vitest'
import type { Candidate } from './api'
import { persistDroppedCandidateOrder } from './candidateReorder'

const candidates: Candidate[] = [
  { id: 11, mottoShowId: 7, artist: 'Erste', title: 'A', youtubeUrl: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', comment: null, status: 'OFFEN', manualPosition: 1, createdAt: '2026-08-27T00:00:00Z', updatedAt: '2026-08-27T00:00:00Z' },
  { id: 22, mottoShowId: 7, artist: 'Zweite', title: 'B', youtubeUrl: 'https://www.youtube.com/watch?v=9bZkp7q19f0', comment: null, status: 'OFFEN', manualPosition: 2, createdAt: '2026-08-27T00:00:00Z', updatedAt: '2026-08-27T00:00:00Z' },
  { id: 33, mottoShowId: 7, artist: 'Dritte', title: 'C', youtubeUrl: 'https://www.youtube.com/watch?v=3JZ_D3ELwOQ', comment: null, status: 'OFFEN', manualPosition: 3, createdAt: '2026-08-27T00:00:00Z', updatedAt: '2026-08-27T00:00:00Z' },
]

function drop(sourceIndex: number, destinationIndex: number | null): DropResult {
  return {
    draggableId: '11',
    type: 'DEFAULT',
    source: { droppableId: 'candidate-list', index: sourceIndex },
    destination: destinationIndex === null ? null : { droppableId: 'candidate-list', index: destinationIndex },
    reason: destinationIndex === null ? 'CANCEL' : 'DROP',
    mode: 'FLUID',
    combine: null,
  }
}

describe('persistDroppedCandidateOrder', () => {
  it('does not send a reorder request before a valid drop, then sends the full optimistic order', async () => {
    const save = vi.fn(async (candidateIds: number[]) => candidates.map((candidate) => ({ ...candidate, manualPosition: candidateIds.indexOf(candidate.id) + 1 })))
    const onOptimisticChange = vi.fn()
    const onConfirmedChange = vi.fn()

    await persistDroppedCandidateOrder({ result: drop(0, null), confirmedCandidates: candidates, save, onOptimisticChange, onConfirmedChange })
    expect(save).not.toHaveBeenCalled()
    expect(onOptimisticChange).not.toHaveBeenCalled()
    expect(onConfirmedChange).not.toHaveBeenCalled()

    await persistDroppedCandidateOrder({ result: drop(0, 2), confirmedCandidates: candidates, save, onOptimisticChange, onConfirmedChange })
    expect(save).toHaveBeenCalledOnce()
    expect(save).toHaveBeenCalledWith([22, 33, 11])
    expect(onOptimisticChange).toHaveBeenCalledWith(expect.objectContaining({ 0: expect.objectContaining({ id: 22 }) }))
  })

  it('rolls the displayed order back to the last confirmed order when saving fails', async () => {
    const failure = new Error('conflict')
    const save = vi.fn().mockRejectedValue(failure)
    const onOptimisticChange = vi.fn()
    const onConfirmedChange = vi.fn()

    await expect(persistDroppedCandidateOrder({ result: drop(0, 2), confirmedCandidates: candidates, save, onOptimisticChange, onConfirmedChange })).rejects.toBe(failure)

    expect(onOptimisticChange).toHaveBeenCalledWith(expect.arrayContaining([expect.objectContaining({ id: 22 })]))
    expect(onConfirmedChange).toHaveBeenLastCalledWith(candidates)
  })

  it('keeps hidden rejected candidates in a complete reorder payload', async () => {
    const rejected = { ...candidates[1], status: 'VERWORFEN' as const }
    const fourth = { ...candidates[2], id: 44, artist: 'Vierte', title: 'D', manualPosition: 4 }
    const confirmedCandidates = [candidates[0], rejected, candidates[2], fourth]
    const visibleCandidates = [candidates[0], candidates[2], fourth]
    const save = vi.fn(async (candidateIds: number[]) => confirmedCandidates.map((candidate) => ({
      ...candidate,
      manualPosition: candidateIds.indexOf(candidate.id) + 1,
    })))
    const onOptimisticChange = vi.fn()
    const onConfirmedChange = vi.fn()

    await persistDroppedCandidateOrder({
      result: drop(2, 0),
      confirmedCandidates,
      visibleCandidates,
      save,
      onOptimisticChange,
      onConfirmedChange,
    })

    expect(save).toHaveBeenCalledWith([44, 22, 11, 33])
    expect(onOptimisticChange).toHaveBeenCalledWith(expect.arrayContaining([
      expect.objectContaining({ id: 44, manualPosition: 1 }),
      expect.objectContaining({ id: 22, manualPosition: 2 }),
      expect.objectContaining({ id: 11, manualPosition: 3 }),
    ]))
  })

  it('persists a dropped order when rejected candidates are shown', async () => {
    const rejected = { ...candidates[1], status: 'VERWORFEN' as const }
    const confirmedCandidates = [candidates[0], rejected, candidates[2]]
    const save = vi.fn(async (candidateIds: number[]) => confirmedCandidates.map((candidate) => ({
      ...candidate,
      manualPosition: candidateIds.indexOf(candidate.id) + 1,
    })))
    const onOptimisticChange = vi.fn()
    const onConfirmedChange = vi.fn()

    await persistDroppedCandidateOrder({
      result: drop(2, 0),
      confirmedCandidates,
      visibleCandidates: confirmedCandidates,
      save,
      onOptimisticChange,
      onConfirmedChange,
    })

    expect(save).toHaveBeenCalledWith([33, 11, 22])
    expect(onOptimisticChange).toHaveBeenCalledWith(expect.arrayContaining([
      expect.objectContaining({ id: 33, manualPosition: 1 }),
      expect.objectContaining({ id: 22, manualPosition: 3 }),
    ]))
  })
})
