import { describe, expect, it } from 'vitest'
import type { MottoShow } from './api'
import { evaluationAvailabilityForShow, evaluationStatusForShow, ownEntryStatusForShow, primaryActionForShow } from './showWorkflow'

const baseShow: MottoShow = {
  id: 7,
  contestId: 1,
  showNumber: 7,
  name: 'Testshow',
  entryListComplete: false,
  candidateCount: 2,
  contestEntryCount: 20,
  assessedEntryCount: 0,
  rankedEntryCount: 0,
  assignedEntryCount: 0,
  activeParticipantCount: 20,
  publishedBallotVotedCount: 0,
  publishedBallotNotVotedCount: 0,
  publishedBallotUnrecordedCount: 20,
  ballotClosedAt: null,
  ownParticipationId: 11,
  ownEntryResolution: 'NO_OWN_ENTRY',
  ownEntryId: null,
  selectedCandidate: null,
}

describe('show-card workflow', () => {
  it('selects the deterministic next action for each primary workflow state', () => {
    expect(primaryActionForShow({ ...baseShow, candidateCount: 0, contestEntryCount: 0 })).toEqual({
      label: 'Kandidaten anlegen', to: '/shows/7/candidates',
    })
    expect(primaryActionForShow({ ...baseShow, contestEntryCount: 0 })).toEqual({
      label: 'Kandidaten bearbeiten', to: '/shows/7/candidates',
    })
    expect(primaryActionForShow({ ...baseShow, ownEntryResolution: 'UNRESOLVED' })).toEqual({
      label: 'Eigene Einreichung klären', to: '/shows/7/voting',
    })
    expect(primaryActionForShow(baseShow)).toEqual({ label: 'Abstimmung fortsetzen', to: '/shows/7/voting' })
    expect(primaryActionForShow({ ...baseShow, ballotClosedAt: '2026-08-31T00:00:00Z', assignedEntryCount: 19 })).toEqual({
      label: 'Einreichende zuordnen', to: '/shows/7/voting',
    })
    expect(primaryActionForShow({ ...baseShow, ballotClosedAt: '2026-08-31T00:00:00Z', assignedEntryCount: 20 })).toEqual({
      label: 'Auswertung öffnen', to: '/shows/7/evaluation?view=published-ballots',
    })
  })

  it('prioritizes the #83 identity and own-entry prerequisites over a misleading count', () => {
    expect(primaryActionForShow({ ...baseShow, ownParticipationId: null })).toEqual({
      label: 'Eigene Teilnahme festlegen', to: '/participants',
    })
    expect(primaryActionForShow({ ...baseShow, ballotClosedAt: '2026-08-31T00:00:00Z', ownEntryResolution: 'UNRESOLVED', assignedEntryCount: 20 })).toEqual({
      label: 'Abstimmung wieder öffnen', to: '/shows/7/voting',
    })
    expect(ownEntryStatusForShow({ ...baseShow, ownParticipationId: null })).toBe('Eigene Teilnahme nicht festgelegt')
    expect(ownEntryStatusForShow({ ...baseShow, ownEntryResolution: 'UNRESOLVED' })).toBe('Eigene Einreichung noch ungeklärt')
    expect(ownEntryStatusForShow({ ...baseShow, ballotClosedAt: '2026-08-31T00:00:00Z', ownEntryResolution: 'UNRESOLVED' })).toBe('Abstimmung zum Klären wieder öffnen')
  })

  it('only surfaces ballot progress after the song assignments make the evaluation available', () => {
    expect(evaluationStatusForShow(baseShow)).toBe('Auswertung noch nicht verfügbar')
    expect(evaluationStatusForShow({ ...baseShow, ballotClosedAt: '2026-08-31T00:00:00Z', assignedEntryCount: 19 })).toBe('Nach Enthüllung Einreichende zuordnen')
    expect(evaluationStatusForShow({ ...baseShow, ballotClosedAt: '2026-08-31T00:00:00Z', assignedEntryCount: 20 })).toBe('0 von 20 Stimmzetteln erfasst')
    expect(evaluationStatusForShow({ ...baseShow, ballotClosedAt: '2026-08-31T00:00:00Z', assignedEntryCount: 20, publishedBallotVotedCount: 17, publishedBallotNotVotedCount: 1, publishedBallotUnrecordedCount: 2 })).toBe('17 von 20 Stimmzetteln erfasst')
    expect(evaluationAvailabilityForShow({ ...baseShow, ballotClosedAt: '2026-08-31T00:00:00Z', assignedEntryCount: 20 })).toBe('AVAILABLE')
  })
})
