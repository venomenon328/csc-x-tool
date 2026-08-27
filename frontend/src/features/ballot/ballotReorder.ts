import type { DropResult } from '@hello-pangea/dnd'
import type { ContestEntry } from '../entries/api'
import type { BallotRanking } from './api'

export type BallotLists = { ranked: ContestEntry[], unranked: ContestEntry[] }

export function splitBallotEntries(entries: ContestEntry[]): BallotLists {
  return {
    ranked: entries.filter((entry) => entry.rankingPosition !== null).sort((left, right) => left.rankingPosition! - right.rankingPosition!),
    unranked: entries.filter((entry) => entry.rankingPosition === null),
  }
}

export function applyBallotDrop(lists: BallotLists, result: DropResult): BallotLists {
  if (result.destination === null) return lists
  const sourceName = listName(result.source.droppableId)
  const destinationName = listName(result.destination.droppableId)
  if (sourceName === null || destinationName === null) return lists

  const source = [...lists[sourceName]]
  const [moved] = source.splice(result.source.index, 1)
  if (moved === undefined) return lists

  if (sourceName === destinationName) {
    source.splice(result.destination.index, 0, moved)
    return { ...lists, [sourceName]: source }
  }

  const destination = [...lists[destinationName]]
  destination.splice(result.destination.index, 0, moved)
  return { ...lists, [sourceName]: source, [destinationName]: destination }
}

export function confirmedBallotLists(entries: ContestEntry[], ranking: BallotRanking): BallotLists {
  const byId = new Map(entries.map((entry) => [entry.id, entry]))
  const fromIds = (ids: number[], ranked: boolean) => ids.map((id, index) => {
    const entry = byId.get(id)
    if (entry === undefined) throw new Error('Der serverbestätigte Rang enthält einen unbekannten Beitrag.')
    return { ...entry, rankingPosition: ranked ? index + 1 : null }
  })
  return { ranked: fromIds(ranking.rankedEntryIds, true), unranked: fromIds(ranking.unrankedEntryIds, false) }
}

export function combineBallotLists(lists: BallotLists): ContestEntry[] {
  return [...lists.ranked, ...lists.unranked]
}

type PersistDroppedBallotOptions = {
  result: DropResult
  confirmed: BallotLists
  save: (ranking: BallotRanking) => Promise<BallotRanking>
  onOptimisticChange: (lists: BallotLists) => void
  onConfirmedChange: (lists: BallotLists) => void
}

/** Sends one complete replacement state for a completed drop and restores the confirmed state on failure. */
export async function persistDroppedBallot({ result, confirmed, save, onOptimisticChange, onConfirmedChange }: PersistDroppedBallotOptions): Promise<BallotLists> {
  if (result.destination === null || (result.source.droppableId === result.destination.droppableId && result.source.index === result.destination.index)) {
    return confirmed
  }
  const optimistic = applyBallotDrop(confirmed, result)
  onOptimisticChange(optimistic)
  try {
    const ranking = await save({
      rankedEntryIds: optimistic.ranked.map((entry) => entry.id),
      unrankedEntryIds: optimistic.unranked.map((entry) => entry.id),
    })
    const serverConfirmed = confirmedBallotLists(combineBallotLists(confirmed), ranking)
    onConfirmedChange(serverConfirmed)
    return serverConfirmed
  } catch (error) {
    onConfirmedChange(confirmed)
    throw error
  }
}

function listName(droppableId: string): keyof BallotLists | null {
  if (droppableId === 'ranked-entries') return 'ranked'
  if (droppableId === 'unranked-entries') return 'unranked'
  return null
}
