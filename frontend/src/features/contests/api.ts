import { apiFetch } from '../../api/request'
import { readApiError, type ApiError } from '../../api/error'

export type Contest = {
  id: number
  name: string
  displayOrder: number
  current: boolean
  participantCount: number
  showCount: number
  ownParticipationId: number | null
  createdAt: string
  updatedAt: string
}

export class ContestApiError extends Error {
  constructor(readonly apiError: ApiError) { super(apiError.message) }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await apiFetch(path, init)
  if (!response.ok) throw new ContestApiError(await readApiError(response))
  return response.json() as Promise<T>
}

export function fetchContests(): Promise<Contest[]> { return request('/api/contests') }
export function createContest(name: string): Promise<Contest> {
  return request('/api/contests', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name }) })
}
export function renameContest(id: number, name: string): Promise<Contest> {
  return request('/api/contests/' + id, { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name }) })
}
export function makeCurrent(id: number): Promise<Contest> { return request('/api/contests/' + id + '/make-current', { method: 'POST' }) }
export function setOwnParticipation(id: number, participationId: number | null, confirmChange = false): Promise<Contest> {
  return request('/api/contests/' + id + '/own-participation', {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ participationId, confirmChange }),
  })
}
