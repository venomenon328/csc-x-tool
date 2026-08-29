import type { BallotRanking } from './api'
import type { ContestEntry } from '../entries/api'

const confidenceFactors = [35, 55, 70, 85, 100] as const

export type BallotWarningCode =
  | 'UNASSESSED_ENTRIES'
  | 'UNCERTAIN_TOP_FIFTEEN'
  | 'HIGH_ASSESSMENT_OUTSIDE_TOP_FIFTEEN'
  | 'LOW_ASSESSMENT_IN_TOP_FIFTEEN'
  | 'UNCERTAIN_CUTOFF'

export type BallotWarning = {
  code: BallotWarningCode
  entryIds: number[]
  scoreDifference?: number
}

/**
 * Computes the integer proposal score without rounding: confidence pulls an
 * assessment towards the neutral middle (300), rather than adding a bonus.
 */
export function assessmentSortValue(assessment: number | null, confidence: number | null): number | null {
  if (!isAssessmentValue(assessment) || !isAssessmentValue(confidence)) return null
  return 300 + (assessment - 3) * confidenceFactors[confidence - 1]
}

/**
 * Produces the complete payload for the existing atomic ballot reorder command.
 * It is deliberately a calculation only; callers decide when to persist it.
 */
export function suggestedBallotRanking(entries: ContestEntry[]): BallotRanking {
  const assessed = entries.filter((entry) => assessmentSortValue(entry.assessment, entry.assessmentConfidence) !== null)
  const unassessed = entries.filter((entry) => assessmentSortValue(entry.assessment, entry.assessmentConfidence) === null)

  assessed.sort((left, right) => {
    const valueDifference = assessmentSortValue(right.assessment, right.assessmentConfidence)!
      - assessmentSortValue(left.assessment, left.assessmentConfidence)!
    if (valueDifference !== 0) return valueDifference

    // Preserve any existing manual position; entries without one fall back to
    // their durable pool position. This yields one transitive tie-break order.
    const existingOrderDifference = (left.rankingPosition ?? left.poolPosition)
      - (right.rankingPosition ?? right.poolPosition)
    if (existingOrderDifference !== 0) return existingOrderDifference
    return left.poolPosition - right.poolPosition
  })

  return {
    rankedEntryIds: assessed.map((entry) => entry.id),
    unrankedEntryIds: unassessed.sort((left, right) => left.poolPosition - right.poolPosition).map((entry) => entry.id),
  }
}

/** Derives non-blocking completion hints from the current, persisted ranking. */
export function deriveBallotWarnings(entries: ContestEntry[]): BallotWarning[] {
  const ranked = rankedEntries(entries)
  const topFifteen = ranked.slice(0, 15)
  const warnings: BallotWarning[] = []
  const unassessedIds = entries.filter((entry) => assessmentSortValue(entry.assessment, entry.assessmentConfidence) === null).map((entry) => entry.id)
  if (unassessedIds.length > 0) warnings.push({ code: 'UNASSESSED_ENTRIES', entryIds: unassessedIds })

  const uncertainTopFifteenIds = topFifteen.filter(isUncertain).map((entry) => entry.id)
  if (uncertainTopFifteenIds.length > 0) warnings.push({ code: 'UNCERTAIN_TOP_FIFTEEN', entryIds: uncertainTopFifteenIds })

  const highAssessmentOutsideTopFifteenIds = entries
    .filter((entry) => (entry.assessment === 4 || entry.assessment === 5) && (entry.rankingPosition === null || entry.rankingPosition > 15))
    .map((entry) => entry.id)
  if (highAssessmentOutsideTopFifteenIds.length > 0) warnings.push({ code: 'HIGH_ASSESSMENT_OUTSIDE_TOP_FIFTEEN', entryIds: highAssessmentOutsideTopFifteenIds })

  const lowAssessmentTopFifteenIds = topFifteen
    .filter((entry) => entry.assessment === 1 || entry.assessment === 2)
    .map((entry) => entry.id)
  if (lowAssessmentTopFifteenIds.length > 0) warnings.push({ code: 'LOW_ASSESSMENT_IN_TOP_FIFTEEN', entryIds: lowAssessmentTopFifteenIds })

  const fifteenth = ranked[14]
  const sixteenth = ranked[15]
  const fifteenthValue = fifteenth === undefined ? null : assessmentSortValue(fifteenth.assessment, fifteenth.assessmentConfidence)
  const sixteenthValue = sixteenth === undefined ? null : assessmentSortValue(sixteenth.assessment, sixteenth.assessmentConfidence)
  if (fifteenthValue !== null && sixteenthValue !== null) {
    const scoreDifference = Math.abs(fifteenthValue - sixteenthValue)
    if (scoreDifference <= 15 && (isUncertain(fifteenth) || isUncertain(sixteenth))) {
      warnings.push({ code: 'UNCERTAIN_CUTOFF', entryIds: [fifteenth.id, sixteenth.id], scoreDifference })
    }
  }

  return warnings
}

export function ballotWarningText(warning: BallotWarning): string {
  switch (warning.code) {
    case 'UNASSESSED_ENTRIES':
      return `${warning.entryIds.length} ${pluralize(warning.entryIds.length, 'Beitrag hat', 'Beiträge haben')} noch keine Einschätzung.`
    case 'UNCERTAIN_TOP_FIFTEEN':
      return `${warning.entryIds.length} ${pluralize(warning.entryIds.length, 'Top-15-Beitrag hat', 'Top-15-Beiträge haben')} nur Sicherheit 1 oder 2.`
    case 'HIGH_ASSESSMENT_OUTSIDE_TOP_FIFTEEN':
      return warning.entryIds.length === 1
        ? '1 hoch eingeschätzter Beitrag liegt außerhalb der Top 15 oder ist noch nicht gerankt.'
        : `${warning.entryIds.length} hoch eingeschätzte Beiträge liegen außerhalb der Top 15 oder sind noch nicht gerankt.`
    case 'LOW_ASSESSMENT_IN_TOP_FIFTEEN':
      return `${warning.entryIds.length} ${pluralize(warning.entryIds.length, 'niedrig eingeschätzter Beitrag ist', 'niedrig eingeschätzte Beiträge sind')} in den Top 15.`
    case 'UNCERTAIN_CUTOFF':
      return `Die Grenze zwischen Rang 15 und 16 ist rechnerisch knapp (Abstand ${warning.scoreDifference ?? 0}); mindestens einer der beiden Beiträge ist noch unsicher.`
  }
}

function isAssessmentValue(value: number | null): value is 1 | 2 | 3 | 4 | 5 {
  return value !== null && Number.isInteger(value) && value >= 1 && value <= 5
}

function rankedEntries(entries: ContestEntry[]): ContestEntry[] {
  return entries.filter((entry) => entry.rankingPosition !== null)
    .sort((left, right) => left.rankingPosition! - right.rankingPosition!)
}

function isUncertain(entry: ContestEntry): boolean {
  return entry.assessmentConfidence === 1 || entry.assessmentConfidence === 2
}

function pluralize(count: number, singular: string, plural: string): string {
  return count === 1 ? singular : plural
}
