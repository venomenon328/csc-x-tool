import { describe, expect, it } from 'vitest'
import type { ContestEntry } from '../entries/api'
import { assessmentSortValue, ballotWarningText, deriveBallotWarnings, suggestedBallotRanking } from './ballotSuggestion'

function entry(overrides: Partial<ContestEntry> = {}): ContestEntry {
  return {
    id: 1,
    mottoShowId: 1,
    artist: 'Artist',
    title: 'Title',
    youtubeUrl: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ',
    comment: null,
    assessment: 3,
    assessmentConfidence: 3,
    poolPosition: 1,
    rankingPosition: null,
    participantId: null,
    createdAt: '',
    updatedAt: '',
    ...overrides,
  }
}

const sortValues = [
  [1, 1, 230], [1, 2, 190], [1, 3, 160], [1, 4, 130], [1, 5, 100],
  [2, 1, 265], [2, 2, 245], [2, 3, 230], [2, 4, 215], [2, 5, 200],
  [3, 1, 300], [3, 2, 300], [3, 3, 300], [3, 4, 300], [3, 5, 300],
  [4, 1, 335], [4, 2, 355], [4, 3, 370], [4, 4, 385], [4, 5, 400],
  [5, 1, 370], [5, 2, 410], [5, 3, 440], [5, 4, 470], [5, 5, 500],
] as const

describe('ballot suggestion domain logic', () => {
  it.each(sortValues)('calculates assessment %i / confidence %i as %i', (assessment, confidence, expected) => {
    expect(assessmentSortValue(assessment, confidence)).toBe(expected)
  })

  it('excludes unassessed entries from the proposal and keeps the pool order for them', () => {
    const suggestion = suggestedBallotRanking([
      entry({ id: 1, assessment: 5, assessmentConfidence: 1, poolPosition: 4 }),
      entry({ id: 2, assessment: 4, assessmentConfidence: 5, poolPosition: 2, rankingPosition: 2 }),
      entry({ id: 3, assessment: null, assessmentConfidence: null, poolPosition: 1, rankingPosition: 1 }),
      entry({ id: 4, assessment: 3, assessmentConfidence: 1, poolPosition: 3 }),
    ])

    expect(suggestion).toEqual({ rankedEntryIds: [2, 1, 4], unrankedEntryIds: [3] })
  })

  it('uses the existing ranking and then pool position as deterministic tie-breakers', () => {
    const suggestion = suggestedBallotRanking([
      entry({ id: 1, assessment: 3, assessmentConfidence: 1, poolPosition: 1, rankingPosition: 2 }),
      entry({ id: 2, assessment: 3, assessmentConfidence: 5, poolPosition: 3, rankingPosition: 1 }),
      entry({ id: 3, assessment: 3, assessmentConfidence: 4, poolPosition: 2, rankingPosition: null }),
      entry({ id: 4, assessment: 3, assessmentConfidence: 2, poolPosition: 4, rankingPosition: null }),
    ])

    expect(suggestion.rankedEntryIds).toEqual([2, 1, 3, 4])
  })

  it('returns no proposal rows for entirely unassessed entries', () => {
    expect(suggestedBallotRanking([
      entry({ id: 1, assessment: null, assessmentConfidence: null, poolPosition: 2 }),
      entry({ id: 2, assessment: null, assessmentConfidence: null, poolPosition: 1 }),
    ])).toEqual({ rankedEntryIds: [], unrankedEntryIds: [2, 1] })
  })

  it('excludes the marked own entry from the proposal and its warnings', () => {
    const own = entry({ id: 3, ownEntry: true, assessment: 5, assessmentConfidence: 5, rankingPosition: 1 })
    const eligible = entry({ id: 2, assessment: 4, assessmentConfidence: 4, poolPosition: 2 })

    expect(suggestedBallotRanking([own, eligible])).toEqual({ rankedEntryIds: [2], unrankedEntryIds: [3] })
    expect(deriveBallotWarnings([own])).toEqual([])
  })

  it('derives the unassessed-entry warning', () => {
    expect(deriveBallotWarnings([entry({ id: 1, assessment: null, assessmentConfidence: null })])).toEqual([
      { code: 'UNASSESSED_ENTRIES', entryIds: [1] },
    ])
  })

  it('derives the uncertain-top-fifteen warning', () => {
    const warnings = deriveBallotWarnings([entry({ id: 1, assessment: 4, assessmentConfidence: 2, rankingPosition: 1 })])

    expect(warnings).toContainEqual({ code: 'UNCERTAIN_TOP_FIFTEEN', entryIds: [1] })
  })

  it('derives the high-assessment warning for entries outside the top fifteen and unranked entries', () => {
    const warnings = deriveBallotWarnings([
      entry({ id: 1, assessment: 5, assessmentConfidence: 5, rankingPosition: 16 }),
      entry({ id: 2, assessment: 4, assessmentConfidence: 4, rankingPosition: null }),
    ])

    expect(warnings).toContainEqual({ code: 'HIGH_ASSESSMENT_OUTSIDE_TOP_FIFTEEN', entryIds: [1, 2] })
  })

  it('derives the low-assessment warning for entries in the top fifteen', () => {
    const warnings = deriveBallotWarnings([entry({ id: 1, assessment: 2, assessmentConfidence: 5, rankingPosition: 1 })])

    expect(warnings).toContainEqual({ code: 'LOW_ASSESSMENT_IN_TOP_FIFTEEN', entryIds: [1] })
  })

  it('derives the uncertain cutoff warning at an exact score difference of fifteen', () => {
    const ranked = Array.from({ length: 16 }, (_, index) => entry({
      id: index + 1,
      poolPosition: index + 1,
      rankingPosition: index + 1,
      assessment: 3,
      assessmentConfidence: 5,
    }))
    ranked[14] = entry({ id: 15, poolPosition: 15, rankingPosition: 15, assessment: 2, assessmentConfidence: 2 })
    ranked[15] = entry({ id: 16, poolPosition: 16, rankingPosition: 16, assessment: 1, assessmentConfidence: 1 })

    expect(deriveBallotWarnings(ranked)).toContainEqual({
      code: 'UNCERTAIN_CUTOFF', entryIds: [15, 16], scoreDifference: 15,
    })
  })

  it('does not warn at the cutoff when the difference is too large, both entries are certain, or one has no assessment', () => {
    const base = Array.from({ length: 16 }, (_, index) => entry({
      id: index + 1,
      poolPosition: index + 1,
      rankingPosition: index + 1,
      assessment: 3,
      assessmentConfidence: 5,
    }))
    const variations: ContestEntry[][] = [
      base.map((item, index) => index === 14 ? { ...item, assessment: 5, assessmentConfidence: 1 } : item),
      base.map((item, index) => index === 14 ? { ...item, assessment: 2, assessmentConfidence: 3 }
        : index === 15 ? { ...item, assessment: 2, assessmentConfidence: 4 } : item),
      base.map((item, index) => index === 14 ? { ...item, assessment: null, assessmentConfidence: null } : item),
    ]

    for (const entries of variations) {
      expect(deriveBallotWarnings(entries).some((warning) => warning.code === 'UNCERTAIN_CUTOFF')).toBe(false)
    }
  })

  it('keeps completion hints non-blocking text', () => {
    expect(ballotWarningText({ code: 'UNASSESSED_ENTRIES', entryIds: [1, 2] })).toContain('2 Beiträge haben')
  })
})
