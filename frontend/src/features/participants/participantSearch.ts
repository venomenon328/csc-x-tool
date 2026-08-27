import type { Participant } from './api'

export function filterParticipants(participants: Participant[], query: string): Participant[] {
  const normalizedQuery = query.trim().toLocaleLowerCase()
  if (normalizedQuery === '') return participants
  return participants.filter((participant) => (
    participant.displayName.toLocaleLowerCase().includes(normalizedQuery)
    || participant.aliases.some((alias) => alias.toLocaleLowerCase().includes(normalizedQuery))
  ))
}
