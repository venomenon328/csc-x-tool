import type { Candidate, CandidateStatus } from './api'

export type SortMode = 'MANUAL' | 'ARTIST' | 'TITLE' | 'STATUS' | 'CREATED'
export type StatusFilter = 'ALL' | CandidateStatus

const statusRank: Record<CandidateStatus, number> = {
  OFFEN: 0,
  IM_RENNEN: 1,
  ENGERE_AUSWAHL: 2,
  FINALIST: 3,
  VERWORFEN: 4,
}

export function moveCandidate(candidates: Candidate[], sourceIndex: number, destinationIndex: number): Candidate[] {
  const reordered = [...candidates]
  const [moved] = reordered.splice(sourceIndex, 1)
  if (moved === undefined) return candidates
  reordered.splice(destinationIndex, 0, moved)
  return reordered.map((candidate, index) => ({ ...candidate, manualPosition: index + 1 }))
}

export function visibleCandidates(
  candidates: Candidate[],
  search: string,
  statusFilter: StatusFilter,
  showRejected: boolean,
  sortMode: SortMode,
): Candidate[] {
  const normalizedSearch = search.trim().toLocaleLowerCase('de-DE')
  const filtered = candidates.filter((candidate) => (
    (showRejected || candidate.status !== 'VERWORFEN')
    && (statusFilter === 'ALL' || candidate.status === statusFilter)
    && (!normalizedSearch
      || candidate.artist.toLocaleLowerCase('de-DE').includes(normalizedSearch)
      || candidate.title.toLocaleLowerCase('de-DE').includes(normalizedSearch))
  ))
  return [...filtered].sort((left, right) => {
    if (sortMode === 'MANUAL') return left.manualPosition - right.manualPosition
    if (sortMode === 'ARTIST') return left.artist.localeCompare(right.artist, 'de') || left.manualPosition - right.manualPosition
    if (sortMode === 'TITLE') return left.title.localeCompare(right.title, 'de') || left.manualPosition - right.manualPosition
    if (sortMode === 'STATUS') return statusRank[left.status] - statusRank[right.status] || left.manualPosition - right.manualPosition
    return new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime()
  })
}
