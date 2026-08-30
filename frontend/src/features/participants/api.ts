import { readApiError, type ApiError } from '../../api/error'
import { apiFetch } from '../../api/request'

export type Country = { code: string, name: string }
export type Participant = {
  participationId?: number
  id: number
  displayName: string
  countryCode: string
  countryName: string
  active: boolean
  identityActive?: boolean
  aliases: string[]
  createdAt: string
  updatedAt: string
}
export type ParticipantInput = { displayName: string, countryCode: string, active: boolean, aliases: string[] }
type ParticipantIdentity = { id: number, displayName: string, active: boolean, aliases: string[] }

export class ParticipantApiError extends Error {
  constructor(readonly apiError: ApiError) { super(apiError.message) }
}

export async function fetchCountries(): Promise<Country[]> {
  const response = await apiFetch('/api/countries')
  if (!response.ok) throw new ParticipantApiError(await readApiError(response))
  return response.json() as Promise<Country[]>
}

export async function fetchParticipants(options: { contestId: number, q?: string, includeInactive?: boolean }): Promise<Participant[]> {
  const search = new URLSearchParams()
  if (options.q?.trim()) search.set('q', options.q.trim())
  if (options.includeInactive) search.set('includeInactive', 'true')
  const suffix = search.size === 0 ? '' : '?' + search.toString()
  const response = await apiFetch('/api/contests/' + options.contestId + '/participants' + suffix)
  if (!response.ok) throw new ParticipantApiError(await readApiError(response))
  return response.json() as Promise<Participant[]>
}

export async function createParticipant(contestId: number, input: ParticipantInput): Promise<Participant> {
  const identity = await writeIdentity('/api/participants', 'POST', input)
  return createParticipation(contestId, identity.id, input)
}

export async function updateParticipant(contestId: number, participantId: number, input: ParticipantInput): Promise<Participant> {
  await writeIdentity('/api/participants/' + participantId, 'PATCH', input)
  const response = await apiFetch('/api/contests/' + contestId + '/participants/' + participantId, {
    method: 'PATCH', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ countryCode: input.countryCode, active: input.active }),
  })
  if (!response.ok) throw new ParticipantApiError(await readApiError(response))
  return response.json() as Promise<Participant>
}

export async function deleteParticipant(contestId: number, participantId: number): Promise<void> {
  const response = await apiFetch('/api/contests/' + contestId + '/participants/' + participantId, { method: 'DELETE' })
  if (!response.ok) throw new ParticipantApiError(await readApiError(response))
}

async function createParticipation(contestId: number, participantId: number, input: ParticipantInput): Promise<Participant> {
  const response = await apiFetch('/api/contests/' + contestId + '/participants', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ participantId, countryCode: input.countryCode, active: input.active }),
  })
  if (!response.ok) throw new ParticipantApiError(await readApiError(response))
  return response.json() as Promise<Participant>
}

async function writeIdentity(path: string, method: string, input: ParticipantInput): Promise<ParticipantIdentity> {
  const payload = method === 'POST'
    ? { displayName: input.displayName, active: true, aliases: input.aliases }
    : { displayName: input.displayName, aliases: input.aliases }
  const response = await apiFetch(path, {
    method, headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  if (!response.ok) throw new ParticipantApiError(await readApiError(response))
  return response.json() as Promise<ParticipantIdentity>
}
