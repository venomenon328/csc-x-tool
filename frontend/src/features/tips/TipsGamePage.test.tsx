import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { type ReactNode } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { TipsGamePage } from './TipsGamePage'

type DroppableProvided = { innerRef: (element: HTMLElement | null) => void, droppableProps: Record<string, never>, placeholder: null }
type DraggableProvided = { innerRef: (element: HTMLElement | null) => void, draggableProps: Record<string, never>, dragHandleProps: Record<string, never> }

vi.mock('@hello-pangea/dnd', () => ({
  DragDropContext: ({ children }: { children: ReactNode }) => children,
  Droppable: ({ children }: { children: (provided: DroppableProvided) => ReactNode }) => children({ innerRef: () => {}, droppableProps: {}, placeholder: null }),
  Draggable: ({ children }: { children: (provided: DraggableProvided, state: { isDragging: boolean }) => ReactNode }) => children({ innerRef: () => {}, draggableProps: {}, dragHandleProps: {} }, { isDragging: false }),
}))

const tipsGame = {
  showId: 1, contestId: 1, persisted: false, status: 'DRAFT', createdAt: null, updatedAt: null, resolvedAt: null,
  actualAssignmentsComplete: false,
  participants: [
    { participationId: 11, participantId: 101, displayName: 'CSC und BOTB', countryCode: 'DE', countryName: 'Deutschland', active: true, identityActive: true },
    { participationId: 12, participantId: 102, displayName: 'Nur CSC', countryCode: 'AT', countryName: 'Österreich', active: true, identityActive: true },
    { participationId: 13, participantId: 103, displayName: 'Nur BOTB', countryCode: 'SE', countryName: 'Schweden', active: true, identityActive: true },
    { participationId: 14, participantId: 104, displayName: 'Historienfehler', countryCode: 'FR', countryName: 'Frankreich', active: true, identityActive: true },
  ],
  entries: [], statistics: null,
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('TipsGamePage participant history', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockReset()
    fetchMock.mockImplementation(async (input) => {
      const path = String(input)
      if (path === '/api/shows/1/tips') return jsonResponse(tipsGame)
      if (path === '/api/shows/1/tips/participants/11/history') return jsonResponse({
        participationId: 11,
        entries: [{ entryId: 1, showId: 7, showNumber: 2, showName: 'Archivshow', contestId: 5, contestName: 'CSC IX', currentContest: false, countryCode: 'AT', countryName: 'Österreich', artist: 'Archivartist', title: 'Archivsong', youtubeUrl: null }],
        botbSelections: [{ id: 41, editionNumber: 9, artist: 'VOLA', knownSince: '2024-05-12' }, { id: 40, editionNumber: 2, artist: 'Ohne Datum', knownSince: null }],
      })
      if (path === '/api/shows/1/tips/participants/12/history') return jsonResponse({
        participationId: 12,
        entries: [{ entryId: 2, showId: 8, showNumber: 1, showName: 'Nur CSC', contestId: 4, contestName: 'CSC VIII', currentContest: false, countryCode: 'AT', countryName: 'Österreich', artist: 'CSC Act', title: 'CSC Song', youtubeUrl: null }],
        botbSelections: [],
      })
      if (path === '/api/shows/1/tips/participants/13/history') return jsonResponse({
        participationId: 13, entries: [], botbSelections: [{ id: 50, editionNumber: 3, artist: 'Nur BOTB Act', knownSince: null }],
      })
      if (path === '/api/shows/1/tips/participants/14/history') return jsonResponse({
        timestamp: '2026-09-02T10:00:00Z', status: 500, code: 'HISTORY_UNAVAILABLE', message: 'Die Teilnehmerhistorie konnte nicht geladen werden.', path,
      }, 500)
      throw new Error(`Unexpected request ${path}`)
    })
  })

  afterEach(() => vi.unstubAllGlobals())

  it('shows separated searchable CSC and read-only BOTB histories from the single existing history request', async () => {
    const user = userEvent.setup()
    renderPage()

    expect(await screen.findByText('Teilnehmerhistorie')).toBeVisible()
    expect(screen.getByText('Einen Teilnehmer auswählen, um gepflegte frühere Einreichungen derselben Identität zu sehen.')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'CSC und BOTB' }))

    expect(await screen.findByText('Historische CSC-Einreichungen (1)')).toBeVisible()
    expect(screen.getByText('BOTB-Interpreten (2)')).toBeVisible()
    expect(screen.getByText('BOTB #9 · VOLA')).toBeVisible()
    expect(screen.getByText('bekannt seit 2024-05-12')).toBeVisible()
    expect(screen.queryByRole('button', { name: /BOTB.*bearbeiten/i })).not.toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/shows/1/tips/participants/11/history')

    const search = screen.getByRole('textbox', { name: 'Historie durchsuchen' })
    await user.type(search, 'BOTB #9')
    expect(screen.getByText('BOTB #9 · VOLA')).toBeVisible()
    expect(screen.queryByText('Archivartist – Archivsong')).not.toBeInTheDocument()
    await user.clear(search)
    await user.type(search, 'Archivartist')
    expect(screen.getByText('Archivartist – Archivsong')).toBeVisible()
    expect(screen.queryByText('BOTB #9 · VOLA')).not.toBeInTheDocument()
  })

  it('keeps the empty states independent for CSC-only and BOTB-only identities', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Teilnehmerhistorie')
    await user.click(screen.getByRole('button', { name: 'Nur CSC' }))
    expect(await screen.findByText('Keine BOTB-Interpreten erfasst.')).toBeVisible()
    expect(screen.getByText('CSC Act – CSC Song')).toBeVisible()

    await user.click(screen.getByRole('button', { name: 'Nur BOTB' }))
    expect(await screen.findByText('Keine gepflegten früheren CSC-Einreichungen gefunden.')).toBeVisible()
    expect(screen.getByText('BOTB #3 · Nur BOTB Act')).toBeVisible()
  })

  it('shows a history load error without replacing the tips workspace', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Teilnehmerhistorie')
    await user.click(screen.getByRole('button', { name: 'Historienfehler' }))

    expect(await screen.findByText('Die Teilnehmerhistorie konnte nicht geladen werden.')).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Tippspiel' })).toBeVisible()
  })
})

function renderPage() {
  render(<MemoryRouter initialEntries={['/shows/1/tips']}><Routes><Route element={<TipsGamePage />} path="/shows/:showId/tips" /></Routes></MemoryRouter>)
}
