import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import { App } from './App'

describe('App', () => {
  beforeEach(() => {
    window.history.pushState({}, '', '/')
  })

  it('renders the dark application shell and its planned navigation', () => {
    render(<App />)

    expect(screen.getAllByRole('heading', { name: 'CSC X Tool' })).not.toHaveLength(0)
    expect(screen.getByRole('list', { name: 'Hauptnavigation' })).toHaveTextContent('Teilnehmer')
    expect(screen.getByRole('button', { name: 'Dialog prüfen' })).toBeVisible()
  })

  it('opens the component-library dialog', async () => {
    const user = userEvent.setup()
    render(<App />)

    await user.click(screen.getByRole('button', { name: 'Dialog prüfen' }))

    expect(screen.getByRole('dialog')).toHaveTextContent('Komponentenbibliothek')
  })
})
