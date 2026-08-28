import { readApiError, type ApiError } from '../../api/error'
import { apiFetch } from '../../api/request'

export type Country = {
  code: string
  name: string
}

export type Participant = {
  id: number
  displayName: string
  countryCode: string
  countryName: string
  active: boolean
  aliases: string[]
  createdAt: string
  updatedAt: string
}

export type ParticipantInput = {
  displayName: string
  countryCode: string
  active: boolean
  aliases: string[]
}

export class ParticipantApiError extends Error {
  constructor(readonly apiError: ApiError) {
    super(apiError.message)
  }
}

export async function fetchCountries(): Promise<Country[]> {
  const response = await apiFetch('/api/countries')
  if (!response.ok) throw new ParticipantApiError(await readApiError(response))
  return response.json() as Promise<Country[]>
}

export async function fetchParticipants(options: { q?: string, includeInactive?: boolean } = {}): Promise<Participant[]> {
  const search = new URLSearchParams()
  if (options.q?.trim()) search.set('q', options.q.trim())
  if (options.includeInactive) search.set('includeInactive', 'true')
  const suffix = search.size === 0 ? '' : `?${search.toString()}`
  const response = await apiFetch(`/api/participants${suffix}`)
  if (!response.ok) throw new ParticipantApiError(await readApiError(response))
  return response.json() as Promise<Participant[]>
}

export async function createParticipant(input: ParticipantInput): Promise<Participant> {
  return writeParticipant('/api/participants', 'POST', input)
}

export async function updateParticipant(participantId: number, input: ParticipantInput): Promise<Participant> {
  return writeParticipant(`/api/participants/${participantId}`, 'PATCH', input)
}

export async function deleteParticipant(participantId: number): Promise<void> {
  const response = await apiFetch(`/api/participants/${participantId}`, { method: 'DELETE' })
  if (!response.ok) throw new ParticipantApiError(await readApiError(response))
}

async function writeParticipant(path: string, method: 'POST' | 'PATCH', input: ParticipantInput): Promise<Participant> {
  const response = await apiFetch(path, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
  if (!response.ok) throw new ParticipantApiError(await readApiError(response))
  return response.json() as Promise<Participant>
}
