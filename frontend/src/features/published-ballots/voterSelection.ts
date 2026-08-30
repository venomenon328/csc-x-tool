import type { Participant } from '../participants/api'
import type { BallotWarning } from './api'

export function voterSelectionPatch(
  participationId: number | null,
  participants: Participant[],
  existingBallotParticipationIds: ReadonlySet<number>,
  warnings: BallotWarning[],
) {
  const participant = participants.find((item) => item.participationId === participationId)
  const existingBallot = participationId !== null && existingBallotParticipationIds.has(participationId)
  const retainedWarnings = warnings.filter((warning) => ![
    'EXISTING_BALLOT', 'UNRESOLVED_VOTER', 'AMBIGUOUS_VOTER', 'COUNTRY_CONFLICT',
  ].includes(warning.code))
  const nextWarnings = existingBallot
    ? [...retainedWarnings, {
      code: 'EXISTING_BALLOT',
      message: 'Für diesen Teilnehmer ist bereits ein Stimmzettelstatus gespeichert. Ein Ersatz muss bewusst bestätigt werden.',
    }]
    : retainedWarnings
  return {
    participationId,
    participantId: participant?.id ?? null,
    displayName: participant?.displayName ?? null,
    countryCode: participant?.countryCode ?? null,
    existingBallot,
    replaceExisting: false,
    warnings: nextWarnings,
  }
}
