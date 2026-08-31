import { readApiError, type ApiError } from '../../api/error'
import { apiFetch } from '../../api/request'

export type DerivedResultState = 'OWN_ENTRY' | 'RANKED' | 'OUTSIDE_TOP_15' | 'NO_BALLOT' | 'UNKNOWN'
export type DerivedResultLine = {
  participationId: number
  participantId: number
  displayName: string
  countryCode: string
  countryName: string
  ballotStatus: 'EIGENE_TEILNAHME' | 'ABGESTIMMT' | 'NICHT_ABGESTIMMT' | 'UNERFASST'
  state: DerivedResultState
  rank: number | null
  points: number | null
}
export type ShowResult = {
  mottoShowId: number
  prerequisite: 'OWN_PARTICIPATION_MISSING' | 'OWN_ENTRY_UNRESOLVED' | 'OWN_ENTRY_NONE' | 'ENTRY_LIST_INCOMPLETE' | 'OWN_ENTRY_MISSING' | 'READY'
  ownParticipation: { participationId: number, participantId: number, displayName: string, countryCode: string } | null
  ownEntry: { entryId: number, artist: string, title: string, youtubeUrl: string | null } | null
  selectedCandidateDiffers: boolean
  votedCount: number
  notVotedCount: number
  unrecordedCount: number
  derivedTotalPoints: number
  lines: DerivedResultLine[]
}

export class ResultApiError extends Error {
  constructor(readonly apiError: ApiError) { super(apiError.message) }
}

export async function fetchResult(showId: number): Promise<ShowResult> {
  const response = await apiFetch(`/api/shows/${showId}/results`)
  if (!response.ok) throw new ResultApiError(await readApiError(response))
  return response.json() as Promise<ShowResult>
}
