import { readApiError, type ApiError } from '../../api/error'
import { apiFetch } from '../../api/request'

export type ReceivedScoreStatus = 'UNBEKANNT' | 'NICHT_ABGESTIMMT' | 'ABGESTIMMT'

export type ReceivedScoreLine = {
  participantId: number
  displayName: string
  countryCode: string
  countryName: string
  active: boolean
  status: ReceivedScoreStatus
  points: number | null
  persisted: boolean
}

export type ResultSubmission = { id: number, artist: string, title: string, youtubeUrl: string }

export type ShowResult = {
  mottoShowId: number
  ballotClosedAt: string | null
  resultsClosedAt: string | null
  selectedCandidate: ResultSubmission | null
  lines: ReceivedScoreLine[]
  calculatedTotalPoints: number
  officialTotalPoints: number | null
  officialTotalDifference: number | null
  finalPlace: number | null
  finalPlaceTied: boolean
}

export class ResultApiError extends Error {
  constructor(readonly apiError: ApiError) {
    super(apiError.message)
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await apiFetch(path, init)
  if (!response.ok) throw new ResultApiError(await readApiError(response))
  return response.json() as Promise<T>
}

function json(method: string, body?: unknown): RequestInit {
  return body === undefined ? { method } : { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }
}

export function fetchResult(showId: number): Promise<ShowResult> {
  return request(`/api/shows/${showId}/results`)
}

export function updateReceivedScore(showId: number, participantId: number, status: ReceivedScoreStatus, points: number | null): Promise<ShowResult> {
  return request(`/api/shows/${showId}/results/scores/${participantId}`, json('PUT', { status, points }))
}

export function updateResultDetails(showId: number, input: Pick<ShowResult, 'officialTotalPoints' | 'finalPlace' | 'finalPlaceTied'>): Promise<ShowResult> {
  return request(`/api/shows/${showId}/results/details`, json('PUT', input))
}

export function closeResults(showId: number): Promise<ShowResult> {
  return request(`/api/shows/${showId}/results/close`, json('POST'))
}

export function reopenResults(showId: number): Promise<ShowResult> {
  return request(`/api/shows/${showId}/results/reopen`, json('POST'))
}
