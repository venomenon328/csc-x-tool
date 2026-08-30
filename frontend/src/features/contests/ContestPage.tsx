import { Alert, Box, Button, List, ListItem, ListItemText, MenuItem, Select, Stack, TextField, Typography } from '@mui/material'
import { useEffect, useState } from 'react'
import { ApiErrorNotice } from '../../components/ApiErrorNotice'
import { createContest, makeCurrent, renameContest, setOwnParticipation, type ContestApiError } from './api'
import { useContest } from './ContestContext'
import { fetchParticipants, type Participant } from '../participants/api'

export function ContestPage() {
  const { contests, selectedContest, selectedContestId, selectContest, refresh } = useContest()
  const [newName, setNewName] = useState('')
  const [error, setError] = useState<ContestApiError | null>(null)
  const [participations, setParticipations] = useState<Participant[]>([])
  const selectedContestIdForParticipations = selectedContest?.id
  useEffect(() => {
    if (selectedContestIdForParticipations === undefined) return
    let cancelled = false
    void fetchParticipants({ contestId: selectedContestIdForParticipations, includeInactive: true })
      .then((loaded) => { if (!cancelled) setParticipations(loaded) })
      .catch(() => { if (!cancelled) setParticipations([]) })
    return () => { cancelled = true }
  }, [selectedContestIdForParticipations])
  const create = async () => {
    try {
      const contest = await createContest(newName)
      setNewName('')
      await refresh()
      selectContest(contest.id)
    } catch (caught) { setError(caught as ContestApiError) }
  }
  const rename = async (contest: typeof contests[number]) => {
    const name = window.prompt('Name der CSC-Ausgabe', contest.name)
    if (name === null) return
    try { await renameContest(contest.id, name); await refresh() } catch (caught) { setError(caught as ContestApiError) }
  }
  const setCurrent = async (id: number) => {
    try { await makeCurrent(id); await refresh(); selectContest(id) } catch (caught) { setError(caught as ContestApiError) }
  }
  const chooseOwnParticipation = async (value: string) => {
    if (selectedContest === null) return
    const participationId = value === '' ? null : Number(value)
    try {
      await setOwnParticipation(selectedContest.id, participationId)
      await refresh()
    } catch (caught) {
      const apiError = caught as ContestApiError
      if (apiError.apiError.code === 'OWN_PARTICIPATION_CHANGE_CONFIRMATION_REQUIRED'
          && window.confirm('Die eigene Teilnahme wird bereits für abgeleitete Ergebnisse verwendet. Bezug bewusst ändern?')) {
        try { await setOwnParticipation(selectedContest.id, participationId, true); await refresh() } catch (retry) { setError(retry as ContestApiError) }
      } else setError(apiError)
    }
  }
  return <Stack spacing={3}>
    <Box><Typography component="h1" variant="h4">CSC-Ausgaben</Typography><Typography color="text.secondary">Aktuelle Ausgabe und Archiv getrennt verwalten.</Typography></Box>
    {error !== null && <ApiErrorNotice error={error.apiError} />}
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
      <TextField label="Neue CSC-Ausgabe" onChange={(event) => setNewName(event.target.value)} value={newName} />
      <Button disabled={!newName.trim()} onClick={() => void create()} variant="contained">Anlegen</Button>
    </Stack>
    {selectedContest !== null && <Box component="section"><Typography component="h2" variant="h6">Eigene Contest-Teilnahme</Typography><Typography color="text.secondary" sx={{ mb: 1 }}>Bewusst auswählen; die Ergebnisansicht leitet daraus die eigene tatsächliche Einreichung ab.</Typography><Select aria-label="Eigene Contest-Teilnahme" displayEmpty onChange={(event) => void chooseOwnParticipation(String(event.target.value))} value={selectedContest.ownParticipationId?.toString() ?? ''}><MenuItem value="">Nicht festgelegt</MenuItem>{participations.map((participant) => <MenuItem key={participant.participationId} value={participant.participationId}>{participant.displayName} – {participant.countryName}</MenuItem>)}</Select></Box>}
    <List aria-label="CSC-Ausgaben">
      {contests.map((contest) => <ListItem key={contest.id} secondaryAction={<Stack direction="row" spacing={1}>
        <Button onClick={() => selectContest(contest.id)} variant={selectedContestId === contest.id ? 'contained' : 'outlined'}>Öffnen</Button>
        <Button onClick={() => void rename(contest)}>Umbenennen</Button>
        {!contest.current && <Button onClick={() => void setCurrent(contest.id)}>Als aktuell festlegen</Button>}
      </Stack>}>
        <ListItemText primary={contest.name + (contest.current ? ' · aktuell' : '')} secondary={contest.participantCount + ' Teilnehmer · ' + contest.showCount + ' Shows'} />
      </ListItem>)}
    </List>
    {contests.length === 0 && <Alert severity="warning">Es ist keine CSC-Ausgabe verfügbar.</Alert>}
  </Stack>
}
