import { Alert, Box, Button, Stack, Tab, Tabs, Typography } from '@mui/material'
import { useEffect, useState } from 'react'
import { Link as RouterLink, useParams, useSearchParams } from 'react-router-dom'
import { ApiErrorNotice } from '../../components/ApiErrorNotice'
import { PublishedBallotsEvaluation } from '../published-ballots/PublishedBallotPage'
import { OwnEntryEvaluation } from '../results/ResultPage'
import { fetchShow, ShowApiError, type MottoShow } from '../shows/api'
import { evaluationPath } from '../shows/showWorkflow'

type EvaluationView = 'published-ballots' | 'own-entry'

export function EvaluationPage() {
  const parsedShowId = Number(useParams().showId)
  const showId = Number.isSafeInteger(parsedShowId) && parsedShowId > 0 ? parsedShowId : null
  const [searchParams] = useSearchParams()
  const [show, setShow] = useState<MottoShow | null>(null)
  const [error, setError] = useState<ShowApiError | null>(null)
  const view: EvaluationView = searchParams.get('view') === 'own-entry' ? 'own-entry' : 'published-ballots'

  useEffect(() => {
    if (showId === null) return
    let cancelled = false
    void fetchShow(showId).then((loadedShow) => {
      if (!cancelled) { setShow(loadedShow); setError(null) }
    }).catch((caught: unknown) => {
      if (!cancelled) { setShow(null); setError(asShowApiError(caught, `/api/shows/${showId}`)) }
    })
    return () => { cancelled = true }
  }, [showId])

  if (showId === null) return <Alert severity="error">Die Mottoshow-ID ist ungültig.</Alert>
  if (error !== null) return <ApiErrorNotice error={error.apiError} />
  if (show === null) return <Typography color="text.secondary">Auswertung wird geladen …</Typography>

  return <Stack spacing={3}>
    <Button component={RouterLink} sx={{ alignSelf: 'flex-start' }} to="/">Zur Übersicht</Button>
    <Box>
      <Typography color="secondary" variant="overline">Show {show.showNumber}</Typography>
      <Typography component="h1" variant="h4">{show.name} – Auswertung</Typography>
      <Typography color="text.secondary" sx={{ mt: 1 }}>Veröffentlichte Stimmzettel und die read-only abgeleitete Ansicht der eigenen Einreichung nutzen dieselbe kanonische Datenbasis.</Typography>
    </Box>
    <Tabs aria-label="Auswertungsansichten" value={view} variant="scrollable">
      <Tab aria-label="Veröffentlichte Stimmzettel" component={RouterLink} label="Veröffentlichte Stimmzettel" to={evaluationPath(showId, 'published-ballots')} value="published-ballots" />
      <Tab aria-label="Meine Einreichung" component={RouterLink} label="Meine Einreichung" to={evaluationPath(showId, 'own-entry')} value="own-entry" />
    </Tabs>
    {view === 'published-ballots'
      ? <PublishedBallotsEvaluation show={show} showId={showId} />
      : <OwnEntryEvaluation showId={showId} />}
  </Stack>
}

function asShowApiError(error: unknown, path: string) {
  if (error instanceof ShowApiError) return error
  return new ShowApiError({ timestamp: new Date().toISOString(), status: 0, code: 'NETWORK_ERROR', message: 'Die Auswertung konnte nicht geladen werden.', path })
}
