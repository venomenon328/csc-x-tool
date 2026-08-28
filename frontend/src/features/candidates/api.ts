import { readApiError, type ApiError } from '../../api/error'
import { apiFetch } from '../../api/request'

export type CandidateStatus = 'OFFEN' | 'IM_RENNEN' | 'ENGERE_AUSWAHL' | 'FINALIST' | 'VERWORFEN'

export type Candidate = {
  id: number
  mottoShowId: number
  artist: string
  title: string
  youtubeUrl: string
  comment: string | null
  status: CandidateStatus
  manualPosition: number
  createdAt: string
  updatedAt: string
}

export type CandidateInput = Pick<Candidate, 'artist' | 'title' | 'youtubeUrl' | 'comment'>

export class CandidateApiError extends Error {
  constructor(readonly apiError: ApiError) {
    super(apiError.message)
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await apiFetch(path, init)
  if (!response.ok) {
    throw new CandidateApiError(await readApiError(response))
  }
  if (response.status === 204) {
    return undefined as T
  }
  return response.json() as Promise<T>
}

function json(method: string, body: unknown): RequestInit {
  return { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }
}

export function fetchCandidates(showId: number): Promise<Candidate[]> {
  return request(`/api/shows/${showId}/candidates`)
}

export function createCandidate(showId: number, candidate: CandidateInput): Promise<Candidate> {
  return request(`/api/shows/${showId}/candidates`, json('POST', candidate))
}

export function updateCandidate(showId: number, candidate: Candidate): Promise<Candidate> {
  return request(`/api/shows/${showId}/candidates/${candidate.id}`, json('PATCH', {
    artist: candidate.artist,
    title: candidate.title,
    youtubeUrl: candidate.youtubeUrl,
    comment: candidate.comment,
    status: candidate.status,
  }))
}

export function deleteCandidate(showId: number, candidateId: number): Promise<void> {
  return request(`/api/shows/${showId}/candidates/${candidateId}`, { method: 'DELETE' })
}

export function reorderCandidates(showId: number, candidateIds: number[]): Promise<Candidate[]> {
  return request(`/api/shows/${showId}/candidates/reorder`, json('PUT', { candidateIds }))
}

export function copyCandidate(showId: number, candidateId: number, targetShowIds: number[]): Promise<Candidate[]> {
  return request(`/api/shows/${showId}/candidates/${candidateId}/copy`, json('POST', { targetShowIds }))
}

export function selectSubmission(showId: number, candidateId: number, confirmReplacement: boolean): Promise<Candidate> {
  return request(`/api/shows/${showId}/submission`, json('PUT', { candidateId, confirmReplacement }))
}

export function clearSubmission(showId: number): Promise<void> {
  return request(`/api/shows/${showId}/submission`, { method: 'DELETE' })
}
