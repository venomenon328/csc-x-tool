import { describe, expect, it, vi } from 'vitest'
import type { TipsEntry } from './api'
import { assignParticipant, assignmentPayload, persistTipsDraft } from './tipsAssignments'

const entries: TipsEntry[] = [
  { id: 1, artist: 'Alpha', title: 'One', youtubeUrl: null, actualAssignment: null, tip: { entryId: 1, guessedParticipationId: 10, confidence: 'HIGH', note: 'Alt' } },
  { id: 2, artist: 'Beta', title: 'Two', youtubeUrl: null, actualAssignment: null, tip: null },
]

describe('tips assignments', () => {
  it('moves an already used participant explicitly instead of keeping two hidden assignments', () => {
    const moved = assignParticipant(entries, 2, 10)
    expect(moved.map((entry) => entry.tip?.guessedParticipationId ?? null)).toEqual([null, 10])
    expect(moved[1]?.tip?.confidence).toBeNull()
  })

  it('keeps the confirmed complete draft when a save rejects the optimistic move', async () => {
    const previous = { entries }
    const optimistic = { entries: assignParticipant(entries, 2, 10) }
    const failure = new Error('conflict')
    const save = vi.fn().mockRejectedValue(failure)
    const onChange = vi.fn()

    await expect(persistTipsDraft({ previous, optimistic, save, onChange })).rejects.toBe(failure)

    expect(save).toHaveBeenCalledWith([{ entryId: 2, guessedParticipationId: 10, confidence: null, note: null }])
    expect(onChange).toHaveBeenLastCalledWith(previous)
  })

  it('serializes a deliberately incomplete draft without manufacturing assignments', () => {
    expect(assignmentPayload(assignParticipant(entries, 1, null))).toEqual([])
  })
})
