import { readApiError, type ApiError } from '../../api/error'

export type BallotSnapshotItem = {
  rank: number
  contestEntryId: number | null
  artist: string
  title: string
  youtubeUrl: string
}

export type BallotSnapshot = {
  id: number
  snapshotNumber: number
  createdAt: string
  current: boolean
  items: BallotSnapshotItem[]
}

export type Ballot = {
  ballotClosedAt: string | null
  currentSnapshot: BallotSnapshot | null
  snapshots: BallotSnapshot[]
  renderedText: string | null
}

export type BallotRanking = {
  rankedEntryIds: number[]
  unrankedEntryIds: number[]
}

export class BallotApiError extends Error {
  constructor(readonly apiError: ApiError) {
    super(apiError.message)
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, init)
  if (!response.ok) throw new BallotApiError(await readApiError(response))
  return response.json() as Promise<T>
}

function json(method: string, body?: unknown): RequestInit {
  return body === undefined
    ? { method }
    : { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }
}

export function fetchBallot(showId: number): Promise<Ballot> {
  return request(`/api/shows/${showId}/ballot`)
}

export function reorderBallot(showId: number, ranking: BallotRanking): Promise<BallotRanking> {
  return request(`/api/shows/${showId}/ballot/reorder`, json('PUT', ranking))
}

export function closeBallot(showId: number): Promise<Ballot> {
  return request(`/api/shows/${showId}/ballot/close`, json('POST'))
}

export function reopenBallot(showId: number): Promise<Ballot> {
  return request(`/api/shows/${showId}/ballot/reopen`, json('POST'))
}
