import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { ResultClosurePanel } from './ResultClosurePanel'
import type * as ContestApi from './contestApi'

const api = vi.hoisted(() => ({ fetch: vi.fn(), change: vi.fn() }))
vi.mock('./contestApi', async (original) => ({ ...await original<typeof ContestApi>(), fetchResultClosure: api.fetch, changeResultClosure: api.change }))
const ready = { showId: 1, contestId: 1, status: 'READY', closedAt: null, reasons: [] }
beforeEach(() => { api.fetch.mockReset(); api.change.mockReset(); api.fetch.mockResolvedValue(ready) })

it('requires a conscious confirmation and explains reopening without changing personal snapshots', async () => {
  const user = userEvent.setup()
  api.change.mockResolvedValueOnce({ ...ready, status: 'CLOSED', closedAt: '2026-10-08T00:00:00Z' }).mockResolvedValueOnce(ready)
  render(<MemoryRouter><ResultClosurePanel showId={1} /></MemoryRouter>)
  await screen.findByText('Showergebnis · Abschlussbereit')
  await user.click(screen.getByRole('button', { name: 'Showergebnis abschließen' }))
  expect(api.change).not.toHaveBeenCalled()
  await user.click(screen.getByRole('button', { name: 'Bewusst bestätigen' }))
  expect(await screen.findByText('Showergebnis · Abgeschlossen')).toBeVisible()
  await user.click(await screen.findByRole('button', { name: 'Show wieder öffnen' }))
  expect(screen.getByRole('dialog')).toHaveTextContent('entfällt sofort aus Gesamtwertung und Verlauf')
  expect(screen.getByRole('dialog')).toHaveTextContent('Persönliche Top-15-Snapshots')
  await user.click(screen.getByRole('button', { name: 'Bewusst bestätigen' }))
  expect(await screen.findByText('Showergebnis · Abschlussbereit')).toBeVisible()
  expect(api.change.mock.calls).toEqual([[1, 'close'], [1, 'reopen']])
})

it('shows concrete missing prerequisites and reloads after ballot changes', async () => {
  api.fetch.mockResolvedValueOnce({ ...ready, status: 'IN_PROGRESS', reasons: ['Eine Teilnahme ist UNERFASST.'] })
  const view = render(<MemoryRouter><ResultClosurePanel showId={1} /></MemoryRouter>)
  expect(await screen.findByText('Eine Teilnahme ist UNERFASST.')).toBeVisible()
  expect(screen.getByRole('button', { name: 'Showergebnis abschließen' })).toBeDisabled()
  view.rerender(<MemoryRouter><ResultClosurePanel showId={1} revision={1} /></MemoryRouter>)
  await screen.findByText('Showergebnis · Abschlussbereit')
  expect(screen.getByRole('button', { name: 'Showergebnis abschließen' })).toBeEnabled()
})

it('keeps a server conflict visible when the displayed ready state was stale', async () => {
  const user = userEvent.setup()
  api.change.mockRejectedValue(new Error('Eine neue Teilnahme ist noch unerfasst.'))
  render(<MemoryRouter><ResultClosurePanel showId={1} /></MemoryRouter>)
  await screen.findByText('Showergebnis · Abschlussbereit')
  await user.click(screen.getByRole('button', { name: 'Showergebnis abschließen' }))
  await user.click(screen.getByRole('button', { name: 'Bewusst bestätigen' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Eine neue Teilnahme ist noch unerfasst.')
  expect(screen.queryByText('Showergebnis · Abgeschlossen')).not.toBeInTheDocument()
})
