import type { TipsAssignment, TipsAssignmentInput, TipsEntry } from './api'

/** A participant move always makes the displaced tip visible by clearing it in the same draft. */
export function assignParticipant(entries: TipsEntry[], entryId: number, participationId: number | null): TipsEntry[] {
  return entries.map((entry) => {
    if (entry.id === entryId) {
      return { ...entry, tip: participationId === null ? null : {
        entryId,
        guessedParticipationId: participationId,
        confidence: entry.tip?.confidence ?? null,
        note: entry.tip?.note ?? null,
      } }
    }
    if (participationId !== null && entry.tip?.guessedParticipationId === participationId) return { ...entry, tip: null }
    return entry
  })
}

export function changeTipMetadata(entries: TipsEntry[], entryId: number, patch: Pick<TipsAssignment, 'confidence' | 'note'>): TipsEntry[] {
  return entries.map((entry) => entry.id === entryId && entry.tip !== null ? {
    ...entry,
    tip: { ...entry.tip, ...patch },
  } : entry)
}

export function assignmentPayload(entries: TipsEntry[]): TipsAssignmentInput[] {
  return entries.flatMap((entry) => entry.tip === null ? [] : [{
    entryId: entry.id,
    guessedParticipationId: entry.tip.guessedParticipationId,
    confidence: entry.tip.confidence,
    note: entry.tip.note,
  }])
}

type PersistTipsOptions<T extends { entries: TipsEntry[] }> = {
  previous: T
  optimistic: T
  save: (assignments: TipsAssignmentInput[]) => Promise<T>
  onChange: (game: T) => void
}

/** Keeps the last confirmed full draft intact if a DnD or keyboard assignment is rejected. */
export async function persistTipsDraft<T extends { entries: TipsEntry[] }>({ previous, optimistic, save, onChange }: PersistTipsOptions<T>): Promise<T> {
  onChange(optimistic)
  try {
    const confirmed = await save(assignmentPayload(optimistic.entries))
    onChange(confirmed)
    return confirmed
  } catch (error) {
    onChange(previous)
    throw error
  }
}
