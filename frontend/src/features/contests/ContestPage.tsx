import { Alert, Box, Button, List, ListItem, ListItemText, Stack, TextField, Typography } from '@mui/material'
import { useState } from 'react'
import { ApiErrorNotice } from '../../components/ApiErrorNotice'
import { createContest, makeCurrent, renameContest, type ContestApiError } from './api'
import { useContest } from './ContestContext'

export function ContestPage() {
  const { contests, selectedContestId, selectContest, refresh } = useContest()
  const [newName, setNewName] = useState('')
  const [error, setError] = useState<ContestApiError | null>(null)
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
  return <Stack spacing={3}>
    <Box><Typography component="h1" variant="h4">CSC-Ausgaben</Typography><Typography color="text.secondary">Aktuelle Ausgabe und Archiv getrennt verwalten.</Typography></Box>
    {error !== null && <ApiErrorNotice error={error.apiError} />}
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
      <TextField label="Neue CSC-Ausgabe" onChange={(event) => setNewName(event.target.value)} value={newName} />
      <Button disabled={!newName.trim()} onClick={() => void create()} variant="contained">Anlegen</Button>
    </Stack>
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
