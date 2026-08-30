import { apiFetch } from '../../api/request'
import { readApiError, type ApiError } from '../../api/error'

export type SearchResult = {
  type: 'CANDIDATE' | 'ENTRY'
  id: number
  showId: number
  showNumber: number
  showName: string
  artist: string
  title: string
}

export class SearchApiError extends Error {
  constructor(readonly apiError: ApiError) { super(apiError.message) }
}

export async function searchSongs(query: string, contestId: number | null): Promise<SearchResult[]> {
  if (!query.trim() || contestId === null) return []
  const response = await apiFetch(`/api/search?q=${encodeURIComponent(query.trim())}&contestId=${contestId}`)
  if (!response.ok) throw new SearchApiError(await readApiError(response))
  return response.json() as Promise<SearchResult[]>
}
