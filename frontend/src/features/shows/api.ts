import { readApiError, type ApiError } from '../../api/error'
import { apiFetch } from '../../api/request'

export type MottoShow = {
  id: number
  contestId: number
  showNumber: number
  name: string
  candidateCount: number
  contestEntryCount: number
  assessedEntryCount: number
  rankedEntryCount: number
  assignedEntryCount: number
  activeParticipantCount: number
  knownActiveResultCount: number
  ballotClosedAt: string | null
  resultsClosedAt: string | null
  calculatedTotalPoints: number
  officialTotalPoints: number | null
  officialTotalDifference: number | null
  finalPlace: number | null
  finalPlaceTied: boolean
  selectedCandidate: SelectedCandidate | null
}

export type SelectedCandidate = {
  id: number
  artist: string
  title: string
  youtubeUrl: string
}

export class ShowApiError extends Error {
  constructor(readonly apiError: ApiError) {
    super(apiError.message)
  }
}

export async function fetchShows(contestId?: number): Promise<MottoShow[]> {
  const response = await apiFetch(contestId === undefined ? '/api/shows' : '/api/shows?contestId=' + contestId)
  if (!response.ok) {
    throw new ShowApiError(await readApiError(response))
  }
  return response.json() as Promise<MottoShow[]>
}

export async function fetchShow(showId: number): Promise<MottoShow> {
  const response = await apiFetch('/api/shows/' + showId)
  if (!response.ok) throw new ShowApiError(await readApiError(response))
  return response.json() as Promise<MottoShow>
}

export async function renameShow(showId: number, name: string): Promise<MottoShow> {
  const response = await apiFetch(`/api/shows/${showId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  })
  if (!response.ok) {
    throw new ShowApiError(await readApiError(response))
  }
  return response.json() as Promise<MottoShow>
}
