import { Alert, Box, Stack, Typography } from '@mui/material'
import { useEffect, useState } from 'react'
import { ApiErrorNotice } from '../../components/ApiErrorNotice'
import { EntryApiError, fetchEntries, type ContestEntry } from '../entries/api'
import { fetchParticipants, ParticipantApiError, type Participant } from '../participants/api'
import { ShowApiError, type MottoShow } from '../shows/api'
import { PublishedBallotsPanel } from './PublishedBallotsPanel'

type PageError = EntryApiError | ParticipantApiError | ShowApiError

export function PublishedBallotsEvaluation({ show, showId }: { show: MottoShow, showId: number }) {
  const [entries, setEntries] = useState<ContestEntry[]>([])
  const [participants, setParticipants] = useState<Participant[]>([])
  const [error, setError] = useState<PageError | null>(null)

  useEffect(() => {
    let cancelled = false
    void Promise.all([
      fetchEntries(showId),
      fetchParticipants({ contestId: show.contestId, includeInactive: true }),
    ]).then(([loadedEntries, loadedParticipants]) => {
      if (!cancelled) { setEntries(loadedEntries); setParticipants(loadedParticipants); setError(null) }
    }).catch((caught: unknown) => { if (!cancelled) setError(asPageError(caught)) })
    return () => { cancelled = true }
  }, [show.contestId, showId])

  return <Stack component="section" spacing={3}>
    <Box>
      <Typography component="h2" variant="h5">Veröffentlichte Stimmzettel</Typography>
      <Typography color="text.secondary">Persönliche Top 15 derselben CSC-Ausgabe importieren, prüfen und korrigieren.</Typography>
    </Box>
    {error !== null && <ApiErrorNotice error={error.apiError} />}
    {!show.entryListComplete && show.ballotClosedAt === null
      ? <Alert severity="info">Veröffentlichte Stimmzettel können nach dem Abschluss der eigenen Top 15 gepflegt werden.</Alert>
      : <PublishedBallotsPanel entries={entries} headingLevel="h3" participants={participants} showId={showId} />}
  </Stack>
}

function asPageError(error: unknown): PageError {
  if (error instanceof EntryApiError || error instanceof ParticipantApiError || error instanceof ShowApiError) return error
  return new ShowApiError({ timestamp: new Date().toISOString(), status: 0, code: 'NETWORK_ERROR', message: 'Die veröffentlichten Stimmzettel konnten nicht geladen werden.', path: '/api/shows' })
}
