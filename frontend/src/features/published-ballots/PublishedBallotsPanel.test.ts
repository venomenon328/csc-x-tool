import { describe, expect, it } from 'vitest'
import type { Participant } from '../participants/api'
import { voterSelectionPatch } from './voterSelection'

const participants: Participant[] = [
  {
    participationId: 11,
    id: 101,
    displayName: 'Alice',
    countryCode: 'DE',
    countryName: 'Deutschland',
    active: true,
    aliases: [],
    createdAt: '2026-08-30T00:00:00Z',
    updatedAt: '2026-08-30T00:00:00Z',
  },
  {
    participationId: 22,
    id: 202,
    displayName: 'Bob',
    countryCode: 'FI',
    countryName: 'Finnland',
    active: true,
    aliases: [],
    createdAt: '2026-08-30T00:00:00Z',
    updatedAt: '2026-08-30T00:00:00Z',
  },
]

describe('published ballot manual voter correction', () => {
  it('recomputes replacement requirement from the newly selected voter', () => {
    const existing = new Set([22])

    const existingPatch = voterSelectionPatch(22, participants, existing, [
      { code: 'UNRESOLVED_VOTER', message: 'old parser warning' },
    ])
    expect(existingPatch).toMatchObject({
      participationId: 22,
      participantId: 202,
      displayName: 'Bob',
      existingBallot: true,
      replaceExisting: false,
    })
    expect(existingPatch.warnings.map((warning) => warning.code)).toEqual(['EXISTING_BALLOT'])

    const freshPatch = voterSelectionPatch(11, participants, existing, [
      { code: 'EXISTING_BALLOT', message: 'stale parser warning' },
    ])
    expect(freshPatch).toMatchObject({
      participationId: 11,
      participantId: 101,
      displayName: 'Alice',
      existingBallot: false,
      replaceExisting: false,
    })
    expect(freshPatch.warnings).toEqual([])
  })
})
