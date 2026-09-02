import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '../../app/App'

const overview = {
  databaseLocation: 'C:\\CSC\\data\\csc-x-tool.db', automaticBackupsLocation: 'C:\\CSC\\backups\\automatic',
  manualBackupsLocation: 'C:\\CSC\\backups\\manual', exportsLocation: 'C:\\CSC\\exports',
  lastBackup: { id: 'manual.cscbackup', createdAt: '2026-08-28T10:00:00Z', applicationVersion: '0.0.1', schemaVersion: 7, reason: 'MANUAL', sizeBytes: 1234 },
  automaticBackups: [], manualBackups: [{ id: 'manual.cscbackup', createdAt: '2026-08-28T10:00:00Z', applicationVersion: '0.0.1', schemaVersion: 7, reason: 'MANUAL', sizeBytes: 1234 }],
}
const currentContest = { id: 1, name: 'CSC X', displayOrder: 1, current: true, participantCount: 0, showCount: 12, createdAt: '', updatedAt: '' }
const currentShow = {
  id: 1, contestId: 1, showNumber: 1, name: 'Aktuelle Show', entryListComplete: false, candidateCount: 0, contestEntryCount: 0,
  assessedEntryCount: 0, rankedEntryCount: 0, assignedEntryCount: 0, activeParticipantCount: 0,
  publishedBallotVotedCount: 0, publishedBallotNotVotedCount: 0, publishedBallotUnrecordedCount: 0, ballotClosedAt: null, selectedCandidate: null,
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('DataManagementPage', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    window.history.pushState({}, '', '/data')
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockReset()
  })
  afterEach(() => vi.unstubAllGlobals())

  it('shows storage, exports and a separate restore confirmation after a backup preview', async () => {
    const user = userEvent.setup()
    fetchMock.mockImplementation(async (input, init) => {
      const path = String(input)
      if (path === '/api/contests') return jsonResponse([currentContest])
      if (path === '/api/shows') return jsonResponse([currentShow])
      if (path === '/api/data') return jsonResponse(overview)
      if (path === '/api/data/restore/preview/backups/manual.cscbackup' && init?.method === 'POST') return jsonResponse({
        token: 'preview-token', sourceType: 'Sicherung', sourceName: 'manual.cscbackup', createdAt: '2026-08-28T10:00:00Z',
        applicationVersion: '0.0.1', schemaVersion: 7, compatible: true,
        counts: { mottoShows: 12, candidates: 3, participants: 2, contestEntries: 15, botbSelections: 1, ballotSnapshots: 1, legacyReceivedScores: 2 },
      })
      throw new Error(`Unexpected request ${path}`)
    })
    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Daten und Sicherungen' })).toBeVisible()
    expect(screen.getByText('C:\\CSC\\data\\csc-x-tool.db')).toBeVisible()
    expect(screen.getAllByRole('link', { name: 'Herunterladen' })[0]).toHaveAttribute('href', '/api/data/export/full')
    await user.click(screen.getByRole('button', { name: 'Wiederherstellen' }))
    expect(await screen.findByRole('dialog', { name: 'Wiederherstellung bestätigen' })).toBeVisible()
    expect(screen.getByText('Daten endgültig wiederherstellen')).toBeVisible()
  })

  it('creates a manual backup and refreshes its status', async () => {
    const user = userEvent.setup()
    fetchMock.mockImplementation(async (input, init) => {
      const path = String(input)
      if (path === '/api/contests') return jsonResponse([currentContest])
      if (path === '/api/shows') return jsonResponse([currentShow])
      if (path === '/api/data') return jsonResponse(overview)
      if (path === '/api/data/backups' && init?.method === 'POST') return jsonResponse(overview.lastBackup)
      throw new Error(`Unexpected request ${path}`)
    })
    render(<App />)
    await screen.findByRole('heading', { name: 'Daten und Sicherungen' })
    await user.click(screen.getByRole('button', { name: 'Manuelle Sicherung erstellen' }))
    await waitFor(() => expect(screen.getByText('Die manuelle Sicherung wurde erfolgreich erstellt.')).toBeVisible())
    expect(fetchMock).toHaveBeenCalledWith('/api/data/backups', { method: 'POST' })
  })

  it('previews the separate versioned analysis package', async () => {
    const user = userEvent.setup()
    fetchMock.mockImplementation(async (input, init) => {
      const path = String(input)
      if (path === '/api/contests') return jsonResponse([currentContest])
      if (path === '/api/shows') return jsonResponse([currentShow])
      if (path === '/api/data') return jsonResponse(overview)
      if (path === '/api/data/analysis-export/preview' && init?.method === 'POST') return jsonResponse({
        scope: { mode: 'FULL_ARCHIVE', contestIds: [], showIds: [], candidateShowId: null }, participants: 4, participations: 5,
        botbSelections: 2, shows: 12, entries: 18, votedBallots: 2, noBallots: 1, unknownBallots: 3, candidates: 0, assessments: 72,
      })
      throw new Error(`Unexpected request ${path}`)
    })
    render(<App />)

    await screen.findByRole('heading', { name: 'Daten und Sicherungen' })
    await user.click(screen.getByRole('button', { name: 'Umfang vorschauen' }))
    expect(await screen.findByLabelText('Analyseexport-Vorschau')).toBeVisible()
    expect(screen.getByText('2 BOTB-Auswahlen')).toBeVisible()
    expect(screen.getByText('18 Einreichungen')).toBeVisible()
  })
})
