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

export async function searchSongs(query: string): Promise<SearchResult[]> {
  if (!query.trim()) return []
  const response = await apiFetch(`/api/search?q=${encodeURIComponent(query.trim())}`)
  if (!response.ok) throw new SearchApiError(await readApiError(response))
  return response.json() as Promise<SearchResult[]>
}
