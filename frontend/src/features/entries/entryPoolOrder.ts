import type { DropResult } from '@hello-pangea/dnd'
import type { ContestEntry } from './api'

export type EntryPoolSortMode = 'MANUAL' | 'ARTIST' | 'TITLE' | 'LISTENED' | 'RELISTEN' | 'RANK' | 'CREATED'

export type EntryPoolFilters = {
  search: string
  onlyUnlistened: boolean
  onlyRelisten: boolean
  onlyUnranked: boolean
  onlyWithoutParticipant: boolean
}

export function visiblePoolEntries(entries: ContestEntry[], filters: EntryPoolFilters, sortMode: EntryPoolSortMode): ContestEntry[] {
  const needle = filters.search.trim().toLocaleLowerCase('de-DE')
  return entries.filter((entry) => (
    (!needle || `${entry.artist} ${entry.title}`.toLocaleLowerCase('de-DE').includes(needle))
    && (!filters.onlyUnlistened || !entry.listened)
    && (!filters.onlyRelisten || entry.relisten)
    && (!filters.onlyUnranked || entry.rankingPosition === null)
    && (!filters.onlyWithoutParticipant || entry.participantId === null)
  )).sort((left, right) => {
    if (sortMode === 'MANUAL') return left.poolPosition - right.poolPosition
    if (sortMode === 'ARTIST') return left.artist.localeCompare(right.artist, 'de') || left.poolPosition - right.poolPosition
    if (sortMode === 'TITLE') return left.title.localeCompare(right.title, 'de') || left.poolPosition - right.poolPosition
    if (sortMode === 'LISTENED') return Number(left.listened) - Number(right.listened) || left.poolPosition - right.poolPosition
    if (sortMode === 'RELISTEN') return Number(right.relisten) - Number(left.relisten) || left.poolPosition - right.poolPosition
    if (sortMode === 'RANK') {
      if (left.rankingPosition !== null && right.rankingPosition !== null) return left.rankingPosition - right.rankingPosition
      if (left.rankingPosition !== null) return -1
      if (right.rankingPosition !== null) return 1
      return left.poolPosition - right.poolPosition
    }
    return new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime() || left.poolPosition - right.poolPosition
  })
}

export function movePoolEntry(entries: ContestEntry[], sourceIndex: number, destinationIndex: number): ContestEntry[] {
  if (sourceIndex < 0 || destinationIndex < 0 || sourceIndex >= entries.length || destinationIndex >= entries.length) return entries
  const ordered = [...entries]
  const [moved] = ordered.splice(sourceIndex, 1)
  if (moved === undefined) return entries
  ordered.splice(destinationIndex, 0, moved)
  return ordered.map((entry, index) => ({ ...entry, poolPosition: index + 1 }))
}

type PersistDroppedPoolOrderOptions = {
  result: DropResult
  confirmedEntries: ContestEntry[]
  save: (entryIds: number[]) => Promise<ContestEntry[]>
  onOptimisticChange: (entries: ContestEntry[]) => void
  onConfirmedChange: (entries: ContestEntry[]) => void
}

/** Persists the complete manual pool order and rolls back only this order on failure. */
export async function persistDroppedPoolOrder({
  result,
  confirmedEntries,
  save,
  onOptimisticChange,
  onConfirmedChange,
}: PersistDroppedPoolOrderOptions): Promise<ContestEntry[]> {
  if (result.destination === null || result.source.index === result.destination.index) return confirmedEntries
  const optimistic = movePoolEntry(confirmedEntries, result.source.index, result.destination.index)
  if (optimistic.every((entry, index) => entry.id === confirmedEntries[index]?.id)) return confirmedEntries
  onOptimisticChange(optimistic)
  try {
    const serverConfirmed = await save(optimistic.map((entry) => entry.id))
    onConfirmedChange(serverConfirmed)
    return serverConfirmed
  } catch (error) {
    onConfirmedChange(confirmedEntries)
    throw error
  }
}
