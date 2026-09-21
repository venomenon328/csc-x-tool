import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Participant } from '../participants/api'
import { AssignmentImportPanel, type EditableAssignmentLine } from './AssignmentImportPanel'
import type { ContestEntry } from './api'

const entry: ContestEntry = {
  id: 11, mottoShowId: 1, artist: 'Band', title: 'Song', youtubeUrl: 'https://youtu.be/aaaaaaaaaaa',
  comment: null, assessment: null, assessmentConfidence: null, poolPosition: 1, rankingPosition: null,
  participantId: 21, contestParticipationId: 31, createdAt: '2026-09-21T00:00:00Z', updatedAt: '2026-09-21T00:00:00Z',
}
const participants: Participant[] = [
  { participationId: 31, id: 21, displayName: 'Alice', countryCode: 'DE', countryName: 'Deutschland', active: true, aliases: [], createdAt: '', updatedAt: '' },
  { participationId: 32, id: 22, displayName: 'Bob', countryCode: 'CH', countryName: 'Schweiz', active: true, aliases: [], createdAt: '', updatedAt: '' },
]
const unresolved: EditableAssignmentLine = {
  sourcePosition: 1, sourceText: '<script>alert(1)</script> Unknown - Song', artist: null, title: null,
  youtubeUrl: null, participantToken: 'Bob', countryToken: 'Schweiz', participantId: null,
  participationId: null, entryId: null, previousParticipationId: null, action: null, status: 'INCOMPLETE',
  warnings: [{ code: 'UNRECOGNIZED_FORMAT', message: 'Quelle prüfen' }], included: false,
  warningsReviewed: false, confirmReplacement: false,
}

describe('AssignmentImportPanel', () => {
  it('keeps unknown source text inert and requires manual choices and an explicit replacement', async () => {
    const user = userEvent.setup()
    let lines = [unresolved]
    const onImport = vi.fn()
    const onPaste = vi.fn(async () => {})
    let view: ReturnType<typeof render>
    const panel = () => <AssignmentImportPanel busy={false} entries={[entry]} lines={lines} onCancel={() => {}}
      onChange={(next) => { lines = next; view.rerender(panel()) }} onImport={onImport} onPaste={onPaste} participants={participants} />
    view = render(panel())

    expect(screen.getByText('1 / 1 Beiträge zugeordnet')).toBeVisible()
    expect(screen.getByText(/<script>alert\(1\)<\/script>/)).toBeVisible()
    expect(screen.queryByRole('script')).not.toBeInTheDocument()
    fireEvent.paste(screen.getByRole('button', { name: 'Einreichenden-Zuordnungsblock einfügen' }), {
      clipboardData: { getData: (type: string) => type === 'text/html' ? '<p>html</p>' : 'plain' },
    })
    expect(onPaste).toHaveBeenCalledWith('<p>html</p>', 'plain')

    await user.click(screen.getByLabelText('Vorhandener Beitrag'))
    await user.click(await screen.findByRole('option', { name: 'Band – Song' }))
    await user.click(screen.getByLabelText('Einreichender'))
    await user.click(await screen.findByRole('option', { name: 'Bob · Schweiz' }))
    await user.click(screen.getByLabelText('Quellwarnungen geprüft und Auswahl manuell bestätigt'))
    await user.click(screen.getByLabelText('Diese Zeile importieren'))
    expect(screen.getByRole('button', { name: '1 Zuordnung bestätigen' })).toBeDisabled()
    await user.click(screen.getByLabelText('Bestehende Zuordnung ausdrücklich ersetzen'))
    expect(screen.getByRole('button', { name: '1 Zuordnung bestätigen' })).toBeEnabled()
    await user.click(screen.getByRole('button', { name: '1 Zuordnung bestätigen' }))
    expect(onImport).toHaveBeenCalledOnce()
    expect(lines[0]).toMatchObject({ entryId: 11, participationId: 32, previousParticipationId: 31, confirmReplacement: true })
  })
})
