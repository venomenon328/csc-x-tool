import { apiFetch } from '../../api/request'
import { readApiError } from '../../api/error'
import { ShowStandingsApiError } from '../standings/api'
import type { ContestStandings } from '../standings/contestApi'

export type Direction = { giverId: number, receiverId: number }
export type Evaluation = { showId: number, entryId: number | null, state: string, ballotRank: number | null, points: number | null }
export type Relation = Direction & { points: number, scoredShows: number, opportunities: number, average: number | null, twentyFives: number, shows: Evaluation[] }
export type Pair = { firstId: number, secondId: number, firstToSecond: number, secondToFirst: number, partnership: number, difference: number, strongerGiverId: number | null, commonShowIds: number[] }
export type Run = { length: number, firstShowId: number, lastShowId: number, showIds: number[] }
export type Profile = { participationId: number, topGiverIds: number[], topReceiverIds: number[], twentyFives: number, opportunities: number, twentyFiveRate: number | null, countedEntries: number, top15Count: number, podiumCount: number, wins: number, pointRuns: Run[], podiumRuns: Run[] }
export type Entry = { id: number, showId: number, participationId: number | null, artist: string, title: string }
export type EntryAward = { entryId: number, twentyFives: number, opportunities: number, rate: number | null }
export type Metric = { numerator: string | null, denominator: string | null, value: number | null, reason: string | null }
export type BallotBasis = { ballotId: number, participationId: number, positions: { entryId: number, rank: number, points: number }[] }
export type PairShow = { showId: number, similarity: Metric, comparisonEntries: number, excludedEntryIds: number[] }
export type SimilarityPair = { firstId: number, secondId: number, similarity: Metric, comparedShows: number, shows: PairShow[] }
export type EntryPreference = { entryId: number, evaluations: number, positiveEvaluations: number, sumPoints: number, twentyFives: number, audienceRate: Metric, variance: Metric, standardDeviation: number | null, polarizationEligible: boolean, histogram: { points: number, count: number }[], exclusiveGiverId: number | null, exclusivePoints: number }
export type ConsensusShow = { showId: number, similarity: Metric, otherBallots: number, comparisonEntries: number, excludedEntryId: number | null, ownPointSum: number, fieldPointSum: number }
export type ParticipantPreference = { participationId: number, consensus: Metric, comparedShows: number, shows: ConsensusShow[], exclusivePoints: number, exclusiveEntryIds: number[], exclusiveTwentyFiveEntryIds: number[] }
export type Preferences = { showBases: { showId: number, ballots: BallotBasis[] }[], pairs: SimilarityPair[], parallelOrder: Direction[], entries: EntryPreference[], audienceOrder: number[], polarizationOrder: number[], participants: ParticipantPreference[], exclusiveOrder: number[], records: { twins: Direction[], parallels: Direction[], audienceEntryIds: number[], polarizationEntryIds: number[], exclusiveParticipantIds: number[], consensusParticipantIds: number[] } }
export type BoundaryEntry = { entryId: number, participationId: number, ballotPoints: number, showRank: number, contestPoints: number }
export type NearMiss = BoundaryEntry & { showId: number, gap: number, lastPointGroup: BoundaryEntry[] }
export type NearMisses = { cases: NearMiss[], frequencies: { participationId: number, count: number, entryIds: number[] }[], frequencyWinnerIds: number[], smallestGapEntryIds: number[] }
export type ImpactOutcome = { ballotPoints: number, showRank: number, contestPoints: number, contestTotal: number }
export type ImpactComparison = { entryId: number, participationId: number, artist: string, title: string, actual: ImpactOutcome, hypothetical: ImpactOutcome | null, delta: ImpactOutcome | null, boundaryChange: string }
export type BallotImpact = { standings: ContestStandings, showId: number, removedBallot: BallotBasis, validBallots: number, remainingBallots: number, state: 'EVALUABLE' | 'NO_REMAINING_BALLOT', actualWinnerEntryIds: number[], hypotheticalWinnerEntryIds: number[], comparisons: ImpactComparison[] }
export type Statistics = {
  nearMisses: NearMisses,
  preferences: Preferences,
  standings: ContestStandings, entries: Entry[], relations: Relation[], topRelations: Direction[], pairs: Pair[], profiles: Profile[], entryAwards: EntryAward[],
  records: { partnerships: Direction[], unrequited: Direction[], twentyFiveParticipantIds: number[], twentyFiveEntryIds: number[], pointRunParticipantIds: number[], podiumRunParticipantIds: number[], top15ParticipantIds: number[], podiumParticipantIds: number[] },
}
export async function fetchStatistics(contestId: number): Promise<Statistics> {
  const response = await apiFetch(`/api/contests/${contestId}/statistics`)
  if (!response.ok) throw new ShowStandingsApiError(await readApiError(response))
  return response.json() as Promise<Statistics>
}
export async function fetchBallotImpact(contestId: number, showId: number, ballotId: number): Promise<BallotImpact> {
  const response = await apiFetch(`/api/contests/${contestId}/shows/${showId}/ballot-impact/${ballotId}`)
  if (!response.ok) throw new ShowStandingsApiError(await readApiError(response))
  return response.json() as Promise<BallotImpact>
}
export const evaluationLabels: Record<string, string> = {
  NOT_COUNTED: 'Show nicht gewertet', NO_ENTRY: 'Keine Empfängereinreichung', UNRECORDED: 'Stimmzettel unerfasst',
  NOT_VOTED: 'Nicht abgestimmt', OUTSIDE_TOP_15: 'Außerhalb Top 15', POINTS: 'In Top 15',
}
export const decimal = (value: number | null) => value === null ? 'N/A' : value.toLocaleString('de-DE', { maximumFractionDigits: 2 })
export const percent = (value: number | null) => value === null ? 'N/A' : `${decimal(value * 100)} %`
