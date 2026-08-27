import { readApiError, type ApiError } from '../../api/error'

export type MottoShow = {
  id: number
  showNumber: number
  name: string
  candidateCount: number
  contestEntryCount: number
  listenedEntryCount: number
  rankedEntryCount: number
  ballotClosedAt: string | null
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

export async function fetchShows(): Promise<MottoShow[]> {
  const response = await fetch('/api/shows')
  if (!response.ok) {
    throw new ShowApiError(await readApiError(response))
  }
  return response.json() as Promise<MottoShow[]>
}

export async function renameShow(showId: number, name: string): Promise<MottoShow> {
  const response = await fetch(`/api/shows/${showId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  })
  if (!response.ok) {
    throw new ShowApiError(await readApiError(response))
  }
  return response.json() as Promise<MottoShow>
}
