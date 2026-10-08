import type { Statistics } from './api'

export const nameOf = (data: Statistics, id: number) => data.standings.rows.find(r => r.participationId === id)?.displayName ?? String(id)
export const profilePath = (id: number) => `/statistics/participants?participant=${id}`
