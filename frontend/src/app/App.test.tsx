import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from './App'

const shows = [
  { id: 1, showNumber: 1, name: 'Super Men' },
  { id: 9, showNumber: 9, name: 'TBA' },
]

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('App', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    window.history.pushState({}, '', '/')
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockReset()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('loads the persistent show overview and offers the prepared work-area navigation', async () => {
    fetchMock.mockResolvedValue(jsonResponse(shows))
    render(<App />)

    expect(screen.getByLabelText('Mottoshows werden geladen')).toBeVisible()
    expect(await screen.findByRole('heading', { name: 'Super Men' })).toBeVisible()
    expect(screen.getAllByText('Eigene Einreichung: noch nicht festgelegt')).not.toHaveLength(0)
    expect(screen.getAllByRole('link', { name: 'Kandidaten' })[0]).toHaveAttribute('href', '/shows/1/candidates')
  })

  it('renders a clear empty state when the API has no shows', async () => {
    fetchMock.mockResolvedValue(jsonResponse([]))
    render(<App />)

    expect(await screen.findByText('Noch keine Mottoshows verfügbar.')).toBeVisible()
  })

  it('persists a renamed show and updates the overview', async () => {
    const user = userEvent.setup()
    fetchMock
      .mockResolvedValueOnce(jsonResponse(shows))
      .mockResolvedValueOnce(jsonResponse({ id: 9, showNumber: 9, name: 'Neues Motto' }))
    render(<App />)

    await screen.findByRole('heading', { name: 'TBA' })
    await user.click(screen.getByRole('button', { name: 'Show 9 bearbeiten' }))
    await user.clear(screen.getByLabelText('Name der Mottoshow'))
    await user.type(screen.getByLabelText('Name der Mottoshow'), 'Neues Motto')
    await user.click(screen.getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.getByRole('heading', { name: 'Neues Motto' })).toBeVisible()
    expect(fetchMock).toHaveBeenLastCalledWith('/api/shows/9', expect.objectContaining({
      method: 'PATCH',
      body: JSON.stringify({ name: 'Neues Motto' }),
    }))
  })

  it('keeps the rename dialog open and displays structured API errors', async () => {
    const user = userEvent.setup()
    fetchMock
      .mockResolvedValueOnce(jsonResponse(shows))
      .mockResolvedValueOnce(jsonResponse({
        timestamp: '2026-08-27T00:00:00Z',
        status: 400,
        code: 'VALIDATION_ERROR',
        message: 'Der Show-Name darf nicht leer sein.',
        path: '/api/shows/9',
      }, 400))
    render(<App />)

    await screen.findByRole('heading', { name: 'TBA' })
    await user.click(screen.getByRole('button', { name: 'Show 9 bearbeiten' }))
    await user.clear(screen.getByLabelText('Name der Mottoshow'))
    await user.click(screen.getByRole('button', { name: 'Speichern' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Der Show-Name darf nicht leer sein.')
    expect(screen.getByRole('dialog')).toBeVisible()
  })
})
