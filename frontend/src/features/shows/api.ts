import { readApiError, type ApiError } from '../../api/error'
import { apiFetch } from '../../api/request'

export type MottoShow = {
  id: number
  contestId: number
  showNumber: number
  name: string
  entryListComplete: boolean
  candidateCount: number
  contestEntryCount: number
  assessedEntryCount: number
  rankedEntryCount: number
  assignedEntryCount: number
  activeParticipantCount: number
  publishedBallotVotedCount: number
  publishedBallotNotVotedCount: number
  publishedBallotUnrecordedCount: number
  ballotClosedAt: string | null
  ownParticipationId?: number | null
  ownEntryResolution?: 'UNRESOLVED' | 'NO_OWN_ENTRY' | 'OWN_ENTRY'
  ownEntryId?: number | null
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

export async function createHistoricalShow(contestId: number, showNumber: number, name: string): Promise<MottoShow> {
  const response = await apiFetch(`/api/contests/${contestId}/shows`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ showNumber, name }),
  })
  if (!response.ok) throw new ShowApiError(await readApiError(response))
  return response.json() as Promise<MottoShow>
}

export async function deleteHistoricalShow(contestId: number, showId: number): Promise<void> {
  const response = await apiFetch(`/api/contests/${contestId}/shows/${showId}`, { method: 'DELETE' })
  if (!response.ok) throw new ShowApiError(await readApiError(response))
}

export async function updateHistoricalShow(contestId: number, showId: number, showNumber: number, name: string): Promise<MottoShow> {
  const response = await apiFetch(`/api/contests/${contestId}/shows/${showId}`, {
    method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ showNumber, name }),
  })
  if (!response.ok) throw new ShowApiError(await readApiError(response))
  return response.json() as Promise<MottoShow>
}
