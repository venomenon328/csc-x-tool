import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { TipsExportPanel } from './TipsExportPanel'

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('TipsExportPanel', () => {
  it('keeps export actions unavailable until the stored tip is complete', () => {
    render(<TipsExportPanel ready={false} showId={7} />)

    expect(screen.getByText(/Sobald alle tippbaren Beiträge gespeichert zugeordnet sind/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Zuordnungen kopieren' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Textdatei herunterladen' })).not.toBeInTheDocument()
  })

  it('downloads from the canonical backend export endpoint', () => {
    render(<TipsExportPanel ready showId={7} />)

    expect(screen.getByRole('link', { name: 'Textdatei herunterladen' }))
      .toHaveAttribute('href', '/api/shows/7/tips/export')
  })

  it('copies exactly the text returned by the canonical backend export endpoint', async () => {
    const exported = 'Atomship - The Vast Unseen [Brasilien/Teilnehmer]'
    const fetchMock = vi.fn().mockResolvedValue(new Response(exported, { status: 200 }))
    const writeText = vi.fn().mockResolvedValue(undefined)
    vi.stubGlobal('fetch', fetchMock)
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } })
    render(<TipsExportPanel ready showId={7} />)

    fireEvent.click(screen.getByRole('button', { name: 'Zuordnungen kopieren' }))

    await waitFor(() => expect(writeText).toHaveBeenCalledWith(exported))
    expect(fetchMock).toHaveBeenCalledWith('/api/shows/7/tips/export')
    expect(screen.getByText(/in die Zwischenablage kopiert/)).toBeInTheDocument()
  })
})
