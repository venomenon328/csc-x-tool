import type { MottoShow } from './api'

export type ShowPrimaryAction = {
  label: string
  to: string
}

export type EvaluationAvailability = 'AVAILABLE' | 'NOT_YET_AVAILABLE'

export function primaryActionForShow(show: MottoShow): ShowPrimaryAction {
  if (show.contestEntryCount === 0) {
    return {
      label: show.candidateCount === 0 ? 'Kandidaten anlegen' : 'Kandidaten bearbeiten',
      to: `/shows/${show.id}/candidates`,
    }
  }

  if (ownParticipationIsMissing(show)) {
    return { label: 'Eigene Teilnahme festlegen', to: '/participants' }
  }

  if (ownEntryNeedsResolution(show)) {
    return {
      label: ballotIsClosed(show) ? 'Abstimmung wieder öffnen' : 'Eigene Einreichung klären',
      to: `/shows/${show.id}/voting`,
    }
  }

  if (!ballotIsClosed(show)) {
    return { label: 'Abstimmung fortsetzen', to: `/shows/${show.id}/voting` }
  }

  if (!entriesAreFullyAssigned(show)) {
    return { label: 'Einreichende zuordnen', to: `/shows/${show.id}/voting` }
  }

  return { label: 'Auswertung öffnen', to: evaluationPath(show.id, 'published-ballots') }
}

export function evaluationAvailabilityForShow(show: MottoShow): EvaluationAvailability {
  return ballotIsClosed(show) && entriesAreFullyAssigned(show) ? 'AVAILABLE' : 'NOT_YET_AVAILABLE'
}

export function evaluationStatusForShow(show: MottoShow): string {
  if (!ballotIsClosed(show)) {
    return 'Auswertung noch nicht verfügbar'
  }

  if (!entriesAreFullyAssigned(show)) {
    return 'Nach Enthüllung Einreichende zuordnen'
  }

  const capturedBallotCount = show.publishedBallotVotedCount
  const participantCount = show.activeParticipantCount
  if (show.publishedBallotUnrecordedCount > 0) {
    return `${capturedBallotCount} von ${participantCount} Stimmzetteln erfasst`
  }

  return `${show.publishedBallotVotedCount} abgegeben · ${show.publishedBallotNotVotedCount} nicht abgestimmt`
}

export function ownEntryStatusForShow(show: MottoShow): string {
  if (ownParticipationIsMissing(show)) return 'Eigene Teilnahme nicht festgelegt'
  if (ownEntryNeedsResolution(show)) return ballotIsClosed(show) ? 'Abstimmung zum Klären wieder öffnen' : 'Eigene Einreichung noch ungeklärt'
  if (show.ownEntryResolution === 'NO_OWN_ENTRY') return 'Keine eigene Einreichung bestätigt'
  return 'Eigene Einreichung bestätigt'
}

export function evaluatableEntryCountForShow(show: MottoShow): number {
  return hasConfirmedOwnEntry(show) ? show.contestEntryCount - 1 : show.contestEntryCount
}

export function hasConfirmedOwnEntry(show: MottoShow): boolean {
  return show.ownEntryResolution === 'OWN_ENTRY' && show.ownEntryId !== null && show.ownEntryId !== undefined
}

export function evaluationPath(showId: number, view: 'published-ballots' | 'own-entry' | 'standings') {
  return `/shows/${showId}/evaluation?view=${view}`
}

function ballotIsClosed(show: MottoShow) {
  return show.ballotClosedAt !== null && show.ballotClosedAt !== undefined
}

function ownParticipationIsMissing(show: MottoShow) {
  return show.ownParticipationId === null || show.ownParticipationId === undefined
}

function ownEntryNeedsResolution(show: MottoShow) {
  return show.ownEntryResolution === undefined
    || show.ownEntryResolution === 'UNRESOLVED'
    || (show.ownEntryResolution === 'OWN_ENTRY' && show.ownEntryId == null)
}

function entriesAreFullyAssigned(show: MottoShow) {
  return show.contestEntryCount > 0 && show.assignedEntryCount >= show.contestEntryCount
}
