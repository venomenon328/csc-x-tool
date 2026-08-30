import { readApiError, type ApiError } from '../../api/error'
import { apiFetch } from '../../api/request'

export type PublishedBallotStatus = 'UNERFASST' | 'NICHT_ABGESTIMMT' | 'ABGESTIMMT'
export type BallotWarning = { code: string, message: string }
export type BallotPreviewPosition = {
  sourcePosition: number, rank: number, sourceText: string, entryId: number | null, artist: string | null, title: string | null,
  submitterParticipantId: number | null, submitterDisplayName: string | null, warnings: BallotWarning[]
}
export type BallotPreviewBlock = {
  sourcePosition: number, participationId: number | null, participantId: number | null, displayName: string | null,
  countryCode: string | null, existingBallot: boolean, status: 'READY' | 'WARNING' | 'INCOMPLETE',
  positions: BallotPreviewPosition[], warnings: BallotWarning[]
}
export type PublishedBallotOverview = {
  mottoShowId: number, entryListReady: boolean, votedCount: number, notVotedCount: number, unrecordedCount: number,
  participants: { participationId: number, participantId: number, displayName: string, countryCode: string, countryName: string, status: PublishedBallotStatus, ballotExists: boolean, updatedAt: string | null }[]
}
export type PublishedBallotDetail = {
  mottoShowId: number, participationId: number, participantId: number, displayName: string, countryCode: string,
  status: PublishedBallotStatus, ballotExists: boolean,
  positions: { rank: number, points: number, entryId: number, artist: string, title: string, youtubeUrl: string | null, submitterParticipantId: number | null, submitterDisplayName: string | null, submitterCountryCode: string | null }[],
  entries: { entryId: number, artist: string, title: string, youtubeUrl: string | null, submitterParticipantId: number | null, submitterDisplayName: string | null, submitterCountryCode: string | null, state: 'RANKED' | 'OUTSIDE_TOP_15' | 'OWN_ENTRY' | 'NO_BALLOT' | 'UNKNOWN', rank: number | null, points: number | null }[]
}
export class PublishedBallotApiError extends Error { constructor(readonly apiError: ApiError) { super(apiError.message) } }

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await apiFetch(path, init)
  if (!response.ok) throw new PublishedBallotApiError(await readApiError(response))
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}
function json(method: string, body: unknown): RequestInit { return { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) } }
export function fetchPublishedBallotOverview(showId: number): Promise<PublishedBallotOverview> { return request(`/api/shows/${showId}/published-ballots`) }
export function fetchPublishedBallotDetail(showId: number, participationId: number): Promise<PublishedBallotDetail> { return request(`/api/shows/${showId}/published-ballots/${participationId}`) }
export function previewPublishedBallots(showId: number, html: string, text: string): Promise<BallotPreviewBlock[]> { return request(`/api/shows/${showId}/published-ballots/import-preview`, json('POST', { html, text })) }
export function importPublishedBallots(showId: number, ballots: { participationId: number, replaceExisting: boolean, positions: { entryId: number, rank: number }[] }[]): Promise<PublishedBallotOverview> { return request(`/api/shows/${showId}/published-ballots/import`, json('POST', { ballots })) }
export function setPublishedBallotStatus(showId: number, participationId: number, status: 'UNERFASST' | 'NICHT_ABGESTIMMT'): Promise<void> { return request(`/api/shows/${showId}/published-ballots/${participationId}/status`, json('PUT', { status })) }
