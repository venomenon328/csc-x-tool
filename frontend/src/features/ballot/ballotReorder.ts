import type { DropResult } from '@hello-pangea/dnd'
import type { ContestEntry } from '../entries/api'
import type { BallotRanking } from './api'

export const ENTRY_POOL_DROPPABLE_ID = 'entry-pool'
export const RANKING_DROPPABLE_ID = 'ranking-entries'

export function rankedEntries(entries: ContestEntry[]): ContestEntry[] {
  return entries.filter((entry) => entry.rankingPosition !== null)
    .sort((left, right) => left.rankingPosition! - right.rankingPosition!)
}

export function applyRankingDrop(entries: ContestEntry[], result: DropResult): ContestEntry[] {
  if (result.destination === null) return entries
  const source = result.source.droppableId
  const destination = result.destination.droppableId
  if (!isVotingDroppable(source) || !isVotingDroppable(destination)) return entries

  const entryId = draggableEntryId(result.draggableId)
  if (entryId === null) return entries
  const moved = entries.find((entry) => entry.id === entryId)
  if (moved === undefined) return entries

  const ranked = rankedEntries(entries)
  if (source === RANKING_DROPPABLE_ID && destination === ENTRY_POOL_DROPPABLE_ID) {
    return withRanking(entries, ranked.filter((entry) => entry.id !== entryId))
  }
  if (destination !== RANKING_DROPPABLE_ID) return entries

  const withoutMoved = ranked.filter((entry) => entry.id !== entryId)
  const destinationIndex = Math.max(0, Math.min(result.destination.index, withoutMoved.length))
  withoutMoved.splice(destinationIndex, 0, moved)
  return withRanking(entries, withoutMoved)
}

export function removeFromRanking(entries: ContestEntry[], entryId: number): ContestEntry[] {
  return withRanking(entries, rankedEntries(entries).filter((entry) => entry.id !== entryId))
}

export function ballotRankingPayload(entries: ContestEntry[]): BallotRanking {
  const rankedIds = rankedEntries(entries).map((entry) => entry.id)
  const rankedSet = new Set(rankedIds)
  return {
    rankedEntryIds: rankedIds,
    unrankedEntryIds: [...entries].sort((left, right) => left.poolPosition - right.poolPosition)
      .filter((entry) => !rankedSet.has(entry.id)).map((entry) => entry.id),
  }
}

export function applyConfirmedRanking(entries: ContestEntry[], ranking: BallotRanking): ContestEntry[] {
  const rankById = new Map(ranking.rankedEntryIds.map((id, index) => [id, index + 1]))
  const submittedIds = new Set([...ranking.rankedEntryIds, ...ranking.unrankedEntryIds])
  if (submittedIds.size !== entries.length || entries.some((entry) => !submittedIds.has(entry.id))) {
    throw new Error('Der serverbestätigte Rang enthält keinen vollständigen Beitragsbestand.')
  }
  return entries.map((entry) => ({ ...entry, rankingPosition: rankById.get(entry.id) ?? null }))
}

type PersistDroppedBallotOptions = {
  result: DropResult
  confirmedEntries: ContestEntry[]
  save: (ranking: BallotRanking) => Promise<BallotRanking>
  onOptimisticChange: (entries: ContestEntry[]) => void
  onConfirmedChange: (entries: ContestEntry[]) => void
}

/** Persists only a ranking change. The pool's ordering is never rebuilt or reordered here. */
export async function persistDroppedBallot({
  result,
  confirmedEntries,
  save,
  onOptimisticChange,
  onConfirmedChange,
}: PersistDroppedBallotOptions): Promise<ContestEntry[]> {
  if (result.destination === null) return confirmedEntries
  const optimistic = applyRankingDrop(confirmedEntries, result)
  if (sameRanking(optimistic, confirmedEntries)) return confirmedEntries
  onOptimisticChange(optimistic)
  try {
    const serverConfirmed = applyConfirmedRanking(confirmedEntries, await save(ballotRankingPayload(optimistic)))
    onConfirmedChange(serverConfirmed)
    return serverConfirmed
  } catch (error) {
    onConfirmedChange(confirmedEntries)
    throw error
  }
}

function withRanking(entries: ContestEntry[], ranked: ContestEntry[]): ContestEntry[] {
  const positions = new Map(ranked.map((entry, index) => [entry.id, index + 1]))
  return entries.map((entry) => ({ ...entry, rankingPosition: positions.get(entry.id) ?? null }))
}

function draggableEntryId(draggableId: string): number | null {
  const match = /^(?:pool|ranking)-entry-(\d+)$/.exec(draggableId)
  if (match === null) return null
  const id = Number(match[1])
  return Number.isSafeInteger(id) && id > 0 ? id : null
}

function isVotingDroppable(id: string): boolean {
  return id === ENTRY_POOL_DROPPABLE_ID || id === RANKING_DROPPABLE_ID
}

function sameRanking(left: ContestEntry[], right: ContestEntry[]): boolean {
  return left.every((entry) => entry.rankingPosition === right.find((other) => other.id === entry.id)?.rankingPosition)
}
