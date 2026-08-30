import { Alert, Box, Stack, Typography } from '@mui/material'
import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { ApiErrorNotice } from '../../components/ApiErrorNotice'
import { fetchEntries, type ContestEntry, type EntryApiError } from '../entries/api'
import { fetchParticipants, type Participant, type ParticipantApiError } from '../participants/api'
import { fetchShow, type MottoShow, type ShowApiError } from '../shows/api'
import { PublishedBallotsPanel } from './PublishedBallotsPanel'

type PageError = EntryApiError | ParticipantApiError | ShowApiError

export function PublishedBallotPage() {
  const showId = Number(useParams().showId)
  const [show, setShow] = useState<MottoShow | null>(null)
  const [entries, setEntries] = useState<ContestEntry[]>([])
  const [participants, setParticipants] = useState<Participant[]>([])
  const [error, setError] = useState<PageError | null>(null)
  useEffect(() => {
    if (!Number.isFinite(showId)) return
    let cancelled = false
    void fetchShow(showId).then(async (loadedShow) => {
      const [loadedEntries, loadedParticipants] = await Promise.all([
        fetchEntries(showId), fetchParticipants({ contestId: loadedShow.contestId, includeInactive: true }),
      ])
      if (!cancelled) { setShow(loadedShow); setEntries(loadedEntries); setParticipants(loadedParticipants); setError(null) }
    }).catch((caught: unknown) => { if (!cancelled) setError(caught as PageError) })
    return () => { cancelled = true }
  }, [showId])
  if (error !== null) return <ApiErrorNotice error={error.apiError} />
  if (show === null) return <Typography color="text.secondary">Einzelwertungen werden geladen …</Typography>
  return <Stack spacing={3}><Box><Typography component="h1" variant="h4">Show {show.showNumber} · {show.name}</Typography><Typography color="text.secondary">Veröffentlichte Einzelwertungen derselben CSC-Ausgabe.</Typography></Box>{!show.entryListComplete && show.ballotClosedAt === null ? <Alert severity="info">Einzelwertungen können nach dem Abschluss der eigenen Top 15 gepflegt werden.</Alert> : <PublishedBallotsPanel entries={entries} participants={participants} showId={showId} />}</Stack>
}
