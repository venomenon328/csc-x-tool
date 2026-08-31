import { readApiError, type ApiError } from '../../api/error'
import { apiFetch } from '../../api/request'

export type ShowStanding = {
  interimRank: number
  entryId: number
  artist: string
  title: string
  youtubeUrl: string | null
  submitterParticipantId: number | null
  submitterDisplayName: string | null
  submitterCountryCode: string | null
  submitterCountryName: string | null
  points: number
  mentions: number
}

export type ShowStandings = {
  mottoShowId: number
  votedCount: number
  notVotedCount: number
  unrecordedCount: number
  entries: ShowStanding[]
}

export class ShowStandingsApiError extends Error {
  constructor(readonly apiError: ApiError) { super(apiError.message) }
}

export async function fetchShowStandings(showId: number): Promise<ShowStandings> {
  const response = await apiFetch(`/api/shows/${showId}/published-ballots/standings`)
  if (!response.ok) throw new ShowStandingsApiError(await readApiError(response))
  return response.json() as Promise<ShowStandings>
}
