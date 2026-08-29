import { readApiError, type ApiError } from '../../api/error'
import { apiFetch } from '../../api/request'
import type { PlayableSong } from '../songs/PlayableSong'

export type ContestEntry = PlayableSong & {
  mottoShowId: number
  comment: string | null
  assessment: number | null
  assessmentConfidence: number | null
  poolPosition: number
  rankingPosition: number | null
  participantId: number | null
  createdAt: string
  updatedAt: string
}

export type ContestEntryInput = Pick<ContestEntry, 'artist' | 'title' | 'youtubeUrl' | 'comment'>

export type ImportPreviewStatus = 'READY' | 'WARNING' | 'INCOMPLETE'

export type ImportWarning = { code: string, message: string }

export type ImportPreviewLine = {
  sourcePosition: number
  sourceType: string
  sourceText: string
  artist: string | null
  title: string | null
  youtubeUrl: string | null
  status: ImportPreviewStatus
  warnings: ImportWarning[]
  possibleDuplicate: boolean
}

export type ImportEntry = { artist: string, title: string, youtubeUrl: string, comment: string | null }

export class EntryApiError extends Error {
  constructor(readonly apiError: ApiError) {
    super(apiError.message)
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await apiFetch(path, init)
  if (!response.ok) throw new EntryApiError(await readApiError(response))
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

function json(method: string, body: unknown): RequestInit {
  return { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }
}

export function fetchEntries(showId: number): Promise<ContestEntry[]> {
  return request(`/api/shows/${showId}/entries`)
}

export function reorderEntryPool(showId: number, entryIds: number[]): Promise<ContestEntry[]> {
  return request(`/api/shows/${showId}/entries/reorder`, json('PUT', { entryIds }))
}

export function createEntry(showId: number, entry: ContestEntryInput): Promise<ContestEntry> {
  return request(`/api/shows/${showId}/entries`, json('POST', entry))
}

export function updateEntry(showId: number, entry: ContestEntry): Promise<ContestEntry> {
  return request(`/api/shows/${showId}/entries/${entry.id}`, json('PATCH', {
    artist: entry.artist,
    title: entry.title,
    youtubeUrl: entry.youtubeUrl,
    comment: entry.comment,
  }))
}

export function updateEntryAssessment(
  showId: number,
  entryId: number,
  assessment: number | null,
  assessmentConfidence: number | null,
): Promise<ContestEntry> {
  return request(`/api/shows/${showId}/entries/${entryId}/assessment`, json('PATCH', { assessment, assessmentConfidence }))
}

export function updateParticipantAssignment(showId: number, entryId: number, participantId: number | null): Promise<ContestEntry> {
  return request(`/api/shows/${showId}/entries/${entryId}/participant`, json('PUT', { participantId }))
}

export function deleteEntry(showId: number, entryId: number): Promise<void> {
  return request(`/api/shows/${showId}/entries/${entryId}`, { method: 'DELETE' })
}

export function previewImport(showId: number, html: string, text: string): Promise<ImportPreviewLine[]> {
  return request(`/api/shows/${showId}/entries/import-preview`, json('POST', { html, text }))
}

export function importEntries(showId: number, entries: ImportEntry[]): Promise<ContestEntry[]> {
  return request(`/api/shows/${showId}/entries/import`, json('POST', { entries }))
}
