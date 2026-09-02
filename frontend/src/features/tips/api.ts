import { readApiError, type ApiError } from '../../api/error'
import { apiFetch } from '../../api/request'

export type TipsGameStatus = 'DRAFT' | 'RESOLVED'
export type TipsConfidence = 'LOW' | 'MEDIUM' | 'HIGH'

export type TipsParticipant = {
  participationId: number
  participantId: number
  displayName: string
  countryCode: string
  countryName: string
  active: boolean
  identityActive: boolean
}

export type TipsActualAssignment = {
  participationId: number
  participantId: number
  displayName: string
  countryCode: string
  countryName: string
}

export type TipsAssignment = {
  entryId: number
  guessedParticipationId: number
  confidence: TipsConfidence | null
  note: string | null
}

export type TipsEntry = {
  id: number
  artist: string
  title: string
  youtubeUrl: string | null
  ownEntry: boolean
  actualAssignment: TipsActualAssignment | null
  tip: TipsAssignment | null
}

export type TipsConfidenceStatistics = {
  confidence: TipsConfidence
  correct: number
  incorrect: number
  tipsSubmitted: number
  hitRate: number | null
}

export type TipsGameStatistics = {
  correct: number
  incorrect: number
  missing: number
  tipsSubmitted: number
  hitRate: number | null
  confidence: TipsConfidenceStatistics[]
}

export type TipsGame = {
  showId: number
  contestId: number
  persisted: boolean
  status: TipsGameStatus
  createdAt: string | null
  updatedAt: string | null
  resolvedAt: string | null
  actualAssignmentsComplete: boolean
  participants: TipsParticipant[]
  entries: TipsEntry[]
  statistics: TipsGameStatistics | null
}

export type TipsHistoryEntry = {
  entryId: number
  showId: number
  showNumber: number
  showName: string
  contestId: number
  contestName: string
  currentContest: boolean
  countryCode: string
  countryName: string
  artist: string
  title: string
  youtubeUrl: string | null
}

export type TipsBotbSelection = {
  id: number
  editionNumber: number
  artist: string
  knownSince: string | null
}

export type TipsHistory = { participationId: number, entries: TipsHistoryEntry[], botbSelections: TipsBotbSelection[] }
export type TipsAssignmentInput = Pick<TipsAssignment, 'entryId' | 'guessedParticipationId' | 'confidence' | 'note'>

export class TipsGameApiError extends Error {
  constructor(readonly apiError: ApiError) { super(apiError.message) }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await apiFetch(path, init)
  if (!response.ok) throw new TipsGameApiError(await readApiError(response))
  return response.json() as Promise<T>
}

function json(method: string, body?: unknown): RequestInit {
  return body === undefined ? { method } : { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }
}

export function fetchTipsGame(showId: number): Promise<TipsGame> { return request(`/api/shows/${showId}/tips`) }
export function saveTipsGame(showId: number, assignments: TipsAssignmentInput[]): Promise<TipsGame> {
  return request(`/api/shows/${showId}/tips`, json('PUT', { assignments }))
}
export function resolveTipsGame(showId: number): Promise<TipsGame> { return request(`/api/shows/${showId}/tips/resolve`, json('POST')) }
export function reopenTipsGame(showId: number): Promise<TipsGame> { return request(`/api/shows/${showId}/tips/reopen`, json('POST')) }
export function fetchTipsHistory(showId: number, participationId: number): Promise<TipsHistory> {
  return request(`/api/shows/${showId}/tips/participants/${participationId}/history`)
}
