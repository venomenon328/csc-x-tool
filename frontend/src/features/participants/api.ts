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
  botbSelectionCount: number
  createdAt: string
  updatedAt: string
}
export type ParticipantInput = { displayName: string, countryCode: string, active: boolean, aliases: string[] }
export type ParticipantIdentity = { id: number, displayName: string, active: boolean, aliases: string[] }
export type IdentityInput = Pick<ParticipantInput, 'displayName' | 'aliases'>
export type ParticipationInput = Pick<ParticipantInput, 'countryCode' | 'active'>
export type BotbSelection = { id: number, participantId: number, editionNumber: number, artist: string, knownSince: string | null, createdAt: string, updatedAt: string }
export type BotbSelectionInput = { id?: number, editionNumber: number, artist: string, knownSince: string | null }

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

export async function fetchParticipantIdentities(): Promise<ParticipantIdentity[]> {
  const response = await apiFetch('/api/participants?includeInactive=true')
  if (!response.ok) throw new ParticipantApiError(await readApiError(response))
  return response.json() as Promise<ParticipantIdentity[]>
}

export async function createParticipant(contestId: number, input: ParticipantInput): Promise<Participant> {
  return createParticipation(contestId, { displayName: input.displayName, aliases: input.aliases, countryCode: input.countryCode, active: input.active })
}

export async function addExistingParticipant(contestId: number, participantId: number, input: ParticipationInput): Promise<Participant> {
  return createParticipation(contestId, { participantId, countryCode: input.countryCode, active: input.active })
}

export async function updateParticipation(contestId: number, participantId: number, input: ParticipationInput): Promise<Participant> {
  const response = await apiFetch('/api/contests/' + contestId + '/participants/' + participantId, {
    method: 'PATCH', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ countryCode: input.countryCode, active: input.active }),
  })
  if (!response.ok) throw new ParticipantApiError(await readApiError(response))
  return response.json() as Promise<Participant>
}

export async function updateParticipantIdentity(participantId: number, input: IdentityInput): Promise<ParticipantIdentity> {
  return writeIdentity('/api/participants/' + participantId, 'PATCH', input)
}

export async function deleteParticipant(contestId: number, participantId: number): Promise<void> {
  const response = await apiFetch('/api/contests/' + contestId + '/participants/' + participantId, { method: 'DELETE' })
  if (!response.ok) throw new ParticipantApiError(await readApiError(response))
}

export async function fetchBotbSelections(participantId: number): Promise<BotbSelection[]> {
  const response = await apiFetch('/api/participants/' + participantId + '/botb-selections')
  if (!response.ok) throw new ParticipantApiError(await readApiError(response))
  return response.json() as Promise<BotbSelection[]>
}

export async function replaceBotbSelections(participantId: number, selections: BotbSelectionInput[]): Promise<BotbSelection[]> {
  const response = await apiFetch('/api/participants/' + participantId + '/botb-selections', {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(selections),
  })
  if (!response.ok) throw new ParticipantApiError(await readApiError(response))
  return response.json() as Promise<BotbSelection[]>
}

async function createParticipation(contestId: number, input: Record<string, unknown>): Promise<Participant> {
  const response = await apiFetch('/api/contests/' + contestId + '/participants', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
  if (!response.ok) throw new ParticipantApiError(await readApiError(response))
  return response.json() as Promise<Participant>
}

async function writeIdentity(path: string, method: string, input: IdentityInput): Promise<ParticipantIdentity> {
  const response = await apiFetch(path, {
    method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input),
  })
  if (!response.ok) throw new ParticipantApiError(await readApiError(response))
  return response.json() as Promise<ParticipantIdentity>
}
