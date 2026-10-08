import { readApiError } from '../../api/error'
import { apiFetch } from '../../api/request'
import { ShowStandingsApiError } from './api'

export type ClosureStatus = 'IN_PROGRESS' | 'READY' | 'CLOSED'
export type ResultClosure = { showId: number, contestId: number, status: ClosureStatus, closedAt: string | null, reasons: string[] }
export type ContestShow = { showId: number, showNumber: number, name: string, status: ClosureStatus, closedAt: string | null }
export type ShowCell = {
  showId: number, state: 'SCORED_ENTRY' | 'NO_ENTRY' | 'NOT_COUNTED', entryId: number | null,
  artist: string | null, title: string | null, ballotPoints: number | null, showRank: number | null, contestPoints: number | null,
}
export type HistoryStep = { showId: number, showNumber: number, included: boolean, totalPoints: number | null, rank: number | null, rankChange: number | null }
export type ContestStanding = {
  participationId: number, participantId: number, displayName: string, countryCode: string, countryName: string,
  rank: number | null, totalPoints: number, rankChange: number | null, shows: ShowCell[], history: HistoryStep[],
}
export type ContestStandings = { contestId: number, shows: ContestShow[], includedShowIds: number[], rows: ContestStanding[] }

async function request<T>(path: string, method = 'GET'): Promise<T> {
  const response = await apiFetch(path, { method })
  if (!response.ok) throw new ShowStandingsApiError(await readApiError(response))
  return response.json() as Promise<T>
}
export const fetchContestStandings = (contestId: number) => request<ContestStandings>(`/api/contests/${contestId}/standings`)
export const fetchResultClosure = (showId: number) => request<ResultClosure>(`/api/shows/${showId}/result-closure`)
export const changeResultClosure = (showId: number, action: 'close' | 'reopen') => request<ResultClosure>(`/api/shows/${showId}/result-closure/${action}`, 'POST')
export const closureLabels: Record<ClosureStatus, string> = { IN_PROGRESS: 'Erfassung läuft', READY: 'Abschlussbereit', CLOSED: 'Abgeschlossen' }
