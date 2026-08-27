import type { DropResult } from '@hello-pangea/dnd'
import type { Candidate } from './api'
import { moveCandidate } from './candidateListUtils'

type PersistDroppedCandidateOrderOptions = {
  result: DropResult
  confirmedCandidates: Candidate[]
  save: (candidateIds: number[]) => Promise<Candidate[]>
  onOptimisticChange: (candidates: Candidate[]) => void
  onConfirmedChange: (candidates: Candidate[]) => void
}

/**
 * Applies a completed drag optimistically and restores the last confirmed order
 * when the server rejects the complete replacement order.
 */
export async function persistDroppedCandidateOrder({
  result,
  confirmedCandidates,
  save,
  onOptimisticChange,
  onConfirmedChange,
}: PersistDroppedCandidateOrderOptions): Promise<Candidate[]> {
  if (result.destination === null || result.destination.index === result.source.index) return confirmedCandidates

  const optimisticCandidates = moveCandidate(confirmedCandidates, result.source.index, result.destination.index)
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
