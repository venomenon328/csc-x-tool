import type { DropResult } from '@hello-pangea/dnd'
import type { Candidate } from './api'
import { moveCandidate } from './candidateListUtils'

type PersistDroppedCandidateOrderOptions = {
  result: DropResult
  confirmedCandidates: Candidate[]
  visibleCandidates?: Candidate[]
  save: (candidateIds: number[]) => Promise<Candidate[]>
  onOptimisticChange: (candidates: Candidate[]) => void
  onConfirmedChange: (candidates: Candidate[]) => void
}

/**
 * Applies a drop whose indices belong to a filtered visual list to the complete
 * manual order. Hidden candidates keep their slots, while the visible candidates
 * are reordered around them. The resulting list therefore remains a complete
 * reorder payload for the API.
 */
export function reorderVisibleCandidates(
  confirmedCandidates: Candidate[],
  visibleCandidates: Candidate[],
  sourceIndex: number,
  destinationIndex: number,
): Candidate[] {
  if (
    sourceIndex < 0
    || destinationIndex < 0
    || sourceIndex >= visibleCandidates.length
    || destinationIndex >= visibleCandidates.length
  ) return confirmedCandidates

  const reorderedVisible = moveCandidate(visibleCandidates, sourceIndex, destinationIndex)
  const visibleIds = new Set(visibleCandidates.map((candidate) => candidate.id))
  let nextVisibleIndex = 0

  return confirmedCandidates
    .map((candidate) => visibleIds.has(candidate.id) ? reorderedVisible[nextVisibleIndex++] : candidate)
    .map((candidate, index) => ({ ...candidate, manualPosition: index + 1 }))
}

/**
 * Applies a completed drag optimistically and restores the last confirmed order
 * when the server rejects the complete replacement order.
 */
export async function persistDroppedCandidateOrder({
  result,
  confirmedCandidates,
  visibleCandidates = confirmedCandidates,
  save,
  onOptimisticChange,
  onConfirmedChange,
}: PersistDroppedCandidateOrderOptions): Promise<Candidate[]> {
  if (result.destination === null || result.destination.index === result.source.index) return confirmedCandidates

  const optimisticCandidates = reorderVisibleCandidates(
    confirmedCandidates,
    visibleCandidates,
    result.source.index,
    result.destination.index,
  )
  if (optimisticCandidates.every((candidate, index) => candidate.id === confirmedCandidates[index]?.id)) return confirmedCandidates
  onOptimisticChange(optimisticCandidates)

  try {
    const confirmed = await save(optimisticCandidates.map((candidate) => candidate.id))
    onConfirmedChange(confirmed)
    return confirmed
  } catch (error) {
    onConfirmedChange(confirmedCandidates)
    throw error
  }
}
