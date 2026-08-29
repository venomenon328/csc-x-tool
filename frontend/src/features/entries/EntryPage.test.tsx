import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '../../app/App'
import type { ContestEntry, ImportPreviewLine } from './api'

const show = { id: 1, showNumber: 1, name: 'Show Eins', candidateCount: 0, contestEntryCount: 2, assessedEntryCount: 0, selectedCandidate: null }
const first: ContestEntry = {
  id: 11, mottoShowId: 1, artist: 'Imminence', title: 'Paralyzed', youtubeUrl: 'https://www.youtube.com/watch?v=2Dqu1Gh45qU',
  comment: null, assessment: null, assessmentConfidence: null, poolPosition: 1, rankingPosition: null, participantId: null,
  createdAt: '2026-08-27T00:00:00Z', updatedAt: '2026-08-27T00:00:00Z',
}
const second: ContestEntry = { ...first, id: 12, artist: 'Alice In Chains', title: 'Would?', youtubeUrl: 'https://www.youtube.com/watch?v=mOJEcEkR1a8', poolPosition: 2, assessment: 3, assessmentConfidence: 1 }
const openBallot = { ballotClosedAt: null, currentSnapshot: null, snapshots: [], renderedText: null }

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('EntryPage', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    window.history.pushState({}, '', '/shows/1/voting')
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockReset()
  })

  afterEach(() => vi.unstubAllGlobals())

  it('sends html and plaintext from one paste event, renders only preview text, and imports exactly selected rows', async () => {
    const preview: ImportPreviewLine[] = [
      {
        sourcePosition: 1, sourceType: 'HTML_LINK', sourceText: 'Imminence - Paralyzed', artist: 'Imminence', title: 'Paralyzed',
        youtubeUrl: first.youtubeUrl, status: 'READY', warnings: [], possibleDuplicate: false,
      },
      {
        sourcePosition: 2, sourceType: 'PLAINTEXT', sourceText: 'Ohne Link - sichtbar', artist: 'Ohne Link', title: 'sichtbar',
        youtubeUrl: null, status: 'INCOMPLETE', warnings: [{ code: 'MISSING_YOUTUBE_URL', message: 'Es wurde kein YouTube-Link erkannt.' }], possibleDuplicate: false,
      },
    ]
    fetchMock.mockImplementation(async (input, init) => {
      const path = String(input)
      if (path === '/api/shows') return jsonResponse([show])
      if (path === '/api/shows/1/ballot') return jsonResponse(openBallot)
      if (path === '/api/shows/1/entries' && init?.method === 'POST') return jsonResponse([first], 200)
      if (path === '/api/shows/1/entries/import-preview') return jsonResponse(preview)
      if (path === '/api/shows/1/entries') return jsonResponse([])
      throw new Error(`Unexpected request ${path}`)
    })
    render(<App />)

    await screen.findByRole('heading', { name: 'Show Eins – Abstimmung' })
    const getData = vi.fn((type: string) => type === 'text/html'
      ? '<img src=x onerror=alert(1)><a href="https://www.youtube.com/watch?v=2Dqu1Gh45qU">Imminence - Paralyzed</a>'
      : 'Imminence - Paralyzed -> https://www.youtube.com/watch?v=2Dqu1Gh45qU')
    fireEvent.paste(screen.getByRole('button', { name: 'CSC-Beitragsblock einfügen' }), { clipboardData: { getData } })

    await screen.findByRole('heading', { name: 'Importvorschau' })
    expect(getData).toHaveBeenCalledWith('text/html')
    expect(getData).toHaveBeenCalledWith('text/plain')
    expect(screen.getByText(/HTML_LINK: Imminence - Paralyzed/)).toBeVisible()
    expect(within(screen.getByRole('region', { name: 'Importvorschau' })).queryByRole('img')).not.toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/shows/1/entries/import-preview', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ html: '<img src=x onerror=alert(1)><a href="https://www.youtube.com/watch?v=2Dqu1Gh45qU">Imminence - Paralyzed</a>', text: 'Imminence - Paralyzed -> https://www.youtube.com/watch?v=2Dqu1Gh45qU' }),
    }))

    await userEvent.setup().click(screen.getByLabelText('Zeile 2 importieren'))
    await userEvent.setup().click(screen.getByRole('button', { name: '1 Beitrag importieren' }))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/shows/1/entries/import', expect.objectContaining({
      body: JSON.stringify({ entries: [{ artist: 'Imminence', title: 'Paralyzed', youtubeUrl: first.youtubeUrl, comment: null }] }),
    })))
  })

  it('keeps a preview row with missing artist and title non-importable until it is corrected', async () => {
    const user = userEvent.setup()
    const preview: ImportPreviewLine[] = [{
      sourcePosition: 1,
      sourceType: 'HTML_LINK',
      sourceText: 'Paralyzed',
      artist: null,
      title: null,
      youtubeUrl: first.youtubeUrl,
      status: 'INCOMPLETE',
      warnings: [{ code: 'MISSING_ARTIST_TITLE_SEPARATOR', message: 'Interpret und Titel konnten nicht eindeutig getrennt werden.' }],
      possibleDuplicate: false,
    }]
    fetchMock.mockImplementation(async (input) => {
      const path = String(input)
      if (path === '/api/shows') return jsonResponse([show])
      if (path === '/api/shows/1/ballot') return jsonResponse(openBallot)
      if (path === '/api/shows/1/entries/import-preview') return jsonResponse(preview)
      if (path === '/api/shows/1/entries') return jsonResponse([])
      throw new Error(`Unexpected request ${path}`)
    })
    render(<App />)

    await screen.findByRole('heading', { name: 'Show Eins – Abstimmung' })
    fireEvent.paste(screen.getByRole('button', { name: 'CSC-Beitragsblock einfügen' }), {
      clipboardData: { getData: (type: string) => type === 'text/html' ? '<a href="https://www.youtube.com/watch?v=2Dqu1Gh45qU">Paralyzed</a>' : 'Paralyzed' },
    })

    const importButton = await screen.findByRole('button', { name: '1 Beitrag importieren' })
    expect(importButton).toBeDisabled()
    await user.type(screen.getByLabelText('Interpret'), 'Imminence')
    await user.type(screen.getByLabelText('Titel'), 'Paralyzed')
    expect(importButton).toBeEnabled()
  })

  it('saves compact assessments independently from metadata and keeps the existing pool and ranking state', async () => {
    const user = userEvent.setup()
    let entries = [first, second]
    fetchMock.mockImplementation(async (input, init) => {
      const path = String(input)
      if (path === '/api/shows') return jsonResponse([show])
      if (path === '/api/shows/1/ballot') return jsonResponse(openBallot)
      if (path === '/api/shows/1/entries' && init?.method === 'POST') {
        const created = { ...first, id: 13, artist: 'Neu', title: 'Beitrag' }
        entries = [...entries, created]
        return jsonResponse(created, 201)
      }
      if (path === '/api/shows/1/entries/11' && init?.method === 'PATCH') {
        const request = JSON.parse(String(init.body)) as Partial<ContestEntry>
        entries = entries.map((entry) => entry.id === 11 ? { ...entry, ...request } : entry)
        return jsonResponse(entries[0])
      }
      if (path === '/api/shows/1/entries/11/assessment' && init?.method === 'PATCH') {
        const request = JSON.parse(String(init.body)) as Pick<ContestEntry, 'assessment' | 'assessmentConfidence'>
        entries = entries.map((entry) => entry.id === 11 ? { ...entry, ...request } : entry)
        return jsonResponse({ ...entries[0], artist: 'Veralteter Interpret', poolPosition: 99, rankingPosition: 5 })
      }
      if (path === '/api/shows/1/entries/12' && init?.method === 'DELETE') return new Response(null, { status: 204 })
      if (path === '/api/shows/1/entries') return jsonResponse(entries)
      throw new Error(`Unexpected request ${path}`)
    })
    render(<App />)

    await screen.findAllByRole('heading', { name: 'Paralyzed' })
    expect(screen.getByText('Imminence')).toBeVisible()
    expect(screen.getByLabelText('Paralyzed: Noch nicht gerankt')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Paralyzed von Imminence auswählen' }))
    await user.click(screen.getByLabelText('Paralyzed: Einschätzung 4 – Klarer Punkte-Kandidat'))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/shows/1/entries/11/assessment', expect.objectContaining({
      method: 'PATCH', body: JSON.stringify({ assessment: 4, assessmentConfidence: 1 }),
    })))
    expect(screen.getByLabelText('Paralyzed: Einschätzung 4 – Klarer Punkte-Kandidat')).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByLabelText('Paralyzed: Sicherheit 1 – Erster Eindruck; kann sich noch deutlich verändern')).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByText('Imminence')).toBeVisible()
    expect(screen.getByLabelText('Paralyzed: Noch nicht gerankt')).toBeVisible()
    await user.click(screen.getByLabelText('Paralyzed: Einschätzung 5 – Favorit'))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/shows/1/entries/11/assessment', expect.objectContaining({
      body: JSON.stringify({ assessment: 5, assessmentConfidence: 1 }),
    })))
    await user.click(screen.getByLabelText('Paralyzed: Sicherheit 3 – Meinung bildet sich; kleinere bis mittlere Verschiebungen bleiben möglich'))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/shows/1/entries/11/assessment', expect.objectContaining({
      body: JSON.stringify({ assessment: 5, assessmentConfidence: 3 }),
    })))
    await user.click(screen.getByLabelText('Weitere Aktionen für Imminence – Paralyzed'))
    await user.click(screen.getByRole('menuitem', { name: 'Einschätzung zurücksetzen' }))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/shows/1/entries/11/assessment', expect.objectContaining({
      body: JSON.stringify({ assessment: null, assessmentConfidence: null }),
    })))
    await user.click(screen.getByLabelText('Weitere Aktionen für Imminence – Paralyzed'))
    await user.click(screen.getByRole('menuitem', { name: 'Bearbeiten' }))
    await user.type(screen.getByLabelText('Kommentar / Hörnotiz'), 'Meine Notiz')
    await user.click(screen.getByRole('button', { name: 'Speichern' }))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/shows/1/entries/11', expect.objectContaining({
      method: 'PATCH', body: expect.stringContaining('"comment":"Meine Notiz"'),
    })))
    await user.click(screen.getByRole('button', { name: 'Nächster' }))
    expect(screen.getByRole('heading', { name: 'Alice In Chains – Would?' })).toBeVisible()

    await user.click(screen.getByLabelText('Ohne Einschätzung'))
    expect(within(screen.getByLabelText('Beitragspool')).getByRole('button', { name: 'Paralyzed von Imminence auswählen' })).toBeVisible()
    expect(within(screen.getByLabelText('Beitragspool')).queryByRole('button', { name: 'Would? von Alice In Chains auswählen' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Beitrag manuell anlegen' }))
    const dialog = screen.getByRole('dialog')
    const fields = dialog.querySelectorAll('input, textarea')
    await user.type(fields[0]!, 'Neu')
    await user.type(fields[1]!, 'Beitrag')
    await user.type(fields[2]!, 'https://youtu.be/dQw4w9WgXcQ')
    await user.click(screen.getByRole('button', { name: 'Speichern' }))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/shows/1/entries', expect.objectContaining({ method: 'POST' })))
  })

  it('offers every assessment level by keyboard and locks an entry while its assessment is pending', async () => {
    const user = userEvent.setup()
    let finishAssessment: ((response: Response) => void) | undefined
    fetchMock.mockImplementation(async (input, init) => {
      const path = String(input)
      if (path === '/api/shows') return jsonResponse([show])
      if (path === '/api/shows/1/ballot') return jsonResponse(openBallot)
      if (path === '/api/shows/1/entries/11/assessment' && init?.method === 'PATCH') {
        return new Promise<Response>((resolve) => { finishAssessment = resolve })
      }
      if (path === '/api/shows/1/entries') return jsonResponse([first, second])
      throw new Error(`Unexpected request ${path}`)
    })
    render(<App />)

    const favorite = await screen.findByLabelText('Paralyzed: Einschätzung 5 – Favorit')
    favorite.focus()
    await user.keyboard('{Enter}')
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/shows/1/entries/11/assessment', expect.objectContaining({
      body: JSON.stringify({ assessment: 5, assessmentConfidence: 1 }),
    })))
    expect(favorite).toBeDisabled()
    expect(screen.getByLabelText('Paralyzed: Sicherheit 1 – Erster Eindruck; kann sich noch deutlich verändern')).toBeDisabled()

    finishAssessment?.(jsonResponse({ ...first, assessment: 5, assessmentConfidence: 1 }))
    await waitFor(() => expect(favorite).not.toBeDisabled())
    expect(favorite).toHaveAttribute('aria-pressed', 'true')
  })

  it('unlocks bundled participant assignment and the without-participant filter only after ballot closure', async () => {
    const user = userEvent.setup()
    const closedBallot = { ballotClosedAt: '2026-08-27T10:00:00Z', currentSnapshot: { id: 1, snapshotNumber: 1, createdAt: '2026-08-27T10:00:00Z', current: true, items: [] }, snapshots: [], renderedText: null }
    const participant = { id: 31, displayName: 'Mira', countryCode: 'AT', countryName: 'Österreich', active: true, aliases: ['Maus'], createdAt: '2026-08-27T00:00:00Z', updatedAt: '2026-08-27T00:00:00Z' }
    let entries = [first, second]
    fetchMock.mockImplementation(async (input, init) => {
      const path = String(input)
      if (path === '/api/shows') return jsonResponse([{ ...show, ballotClosedAt: closedBallot.ballotClosedAt, assignedEntryCount: 0, activeParticipantCount: 1, knownActiveResultCount: 0, resultsClosedAt: null }])
      if (path === '/api/shows/1/ballot') return jsonResponse(closedBallot)
      if (path === '/api/participants?includeInactive=true') return jsonResponse([participant])
      if (path === '/api/shows/1/entries/11/participant' && init?.method === 'PUT') {
        entries = [{ ...first, participantId: 31 }, second]
        return jsonResponse(entries[0])
      }
      if (path === '/api/shows/1/entries') return jsonResponse(entries)
      throw new Error(`Unexpected request ${path}`)
    })
    render(<App />)

    await screen.findByLabelText('Ohne Teilnehmer')
    expect(fetchMock).toHaveBeenCalledWith('/api/participants?includeInactive=true')
    await user.click(screen.getByRole('button', { name: 'Paralyzed von Imminence auswählen' }))
    await user.click(screen.getByLabelText('Teilnehmer dieses Beitrags'))
    await user.click(await screen.findByText('Mira'))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/shows/1/entries/11/participant', expect.objectContaining({
      method: 'PUT', body: JSON.stringify({ participantId: 31 }),
    })))
    await user.click(screen.getByLabelText('Ohne Teilnehmer'))
    expect(within(screen.getByLabelText('Beitragspool')).queryByRole('button', { name: 'Paralyzed von Imminence auswählen' })).not.toBeInTheDocument()
  })
})
