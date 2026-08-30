import { Alert, Box, Button, Card, CardActions, CardContent, Dialog, DialogActions, DialogContent, DialogTitle, Stack, TextField, Typography } from '@mui/material'
import { Link as RouterLink } from 'react-router-dom'
import { useCallback, useEffect, useState } from 'react'
import { ApiErrorNotice } from '../../components/ApiErrorNotice'
import { createHistoricalShow, deleteHistoricalShow, fetchShows, updateHistoricalShow, type MottoShow, type ShowApiError } from '../shows/api'
import { useContest } from '../contests/ContestContext'

export function HistoricalContestPage() {
  const { selectedContest } = useContest()
  const [shows, setShows] = useState<MottoShow[] | null>(null)
  const [error, setError] = useState<ShowApiError | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [showNumber, setShowNumber] = useState('')
  const [name, setName] = useState('')
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState<MottoShow | null>(null)

  const load = useCallback(async () => {
    if (selectedContest === null) return
    try {
      const loadedShows = await fetchShows(selectedContest.id)
      setError(null); setShows(loadedShows)
    } catch (caught) { setError(caught as ShowApiError); setShows(null) }
  }, [selectedContest])

  useEffect(() => {
    if (selectedContest === null) return
    let cancelled = false
    void fetchShows(selectedContest.id)
      .then((loadedShows) => { if (!cancelled) { setError(null); setShows(loadedShows) } })
      .catch((caught: unknown) => { if (!cancelled) { setError(caught as ShowApiError); setShows(null) } })
    return () => { cancelled = true }
  }, [selectedContest])

  async function create() {
    if (selectedContest === null) return
    setSaving(true)
    try {
      await createHistoricalShow(selectedContest.id, Number(showNumber), name)
      setCreateOpen(false); setShowNumber(''); setName(''); await load()
    } catch (caught) { setError(caught as ShowApiError) } finally { setSaving(false) }
  }

  async function remove(show: MottoShow) {
    if (selectedContest === null) return
    try { await deleteHistoricalShow(selectedContest.id, show.id); await load() } catch (caught) { setError(caught as ShowApiError) }
  }

  async function update() {
    if (selectedContest === null || editing === null) return
    setSaving(true)
    try {
      await updateHistoricalShow(selectedContest.id, editing.id, Number(showNumber), name)
      setEditing(null); setShowNumber(''); setName(''); await load()
    } catch (caught) { setError(caught as ShowApiError) } finally { setSaving(false) }
  }

  if (selectedContest === null) return <Alert severity="info">Eine CSC-Ausgabe auswählen, um ihr Archiv zu verwalten.</Alert>
  return <Stack spacing={3}>
    <Box><Typography component="h1" variant="h4">Archiv · {selectedContest.name}</Typography><Typography color="text.secondary">{selectedContest.participantCount} Teilnehmer · vollständige Songlisten vor späteren Einzelwertungen pflegen.</Typography></Box>
    {error !== null && <ApiErrorNotice error={error.apiError} />}
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}><Button component={RouterLink} to="/participants" variant="outlined">Teilnehmerfeld pflegen</Button><Button onClick={() => setCreateOpen(true)} variant="contained">Historische Mottoshow anlegen</Button></Stack>
    {shows === null ? <Typography color="text.secondary">Historische Mottoshows werden geladen …</Typography> : shows.length === 0 ? <Alert severity="info">Noch keine historischen Mottoshows. Zuerst eine Show anlegen, dann die vollständige Songliste erfassen.</Alert> : <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 290px), 1fr))' }}>
      {shows.map((show) => <Card key={show.id} sx={{ border: 1, borderColor: 'divider' }}><CardContent><Typography color="secondary" variant="overline">Show {show.showNumber}</Typography><Typography component="h2" variant="h6">{show.name}</Typography><Typography color="text.secondary" sx={{ mt: 1 }}>{show.contestEntryCount} Beiträge · Songliste {show.entryListComplete ? 'vollständig' : 'offen'}</Typography></CardContent><CardActions><Button component={RouterLink} to={`/historical-shows/${show.id}`} variant="outlined">Songliste öffnen</Button><Button onClick={() => { setEditing(show); setShowNumber(String(show.showNumber)); setName(show.name) }}>Bearbeiten</Button><Button color="error" disabled={show.contestEntryCount > 0} onClick={() => void remove(show)}>Löschen</Button></CardActions></Card>)}
    </Box>}
    <Dialog fullWidth maxWidth="sm" onClose={() => { if (!saving) { setCreateOpen(false); setEditing(null) } }} open={createOpen || editing !== null}><DialogTitle>{editing === null ? 'Historische Mottoshow anlegen' : 'Historische Mottoshow bearbeiten'}</DialogTitle><DialogContent><Stack spacing={2} sx={{ pt: 1 }}><TextField label="Shownummer" onChange={(event) => setShowNumber(event.target.value)} required type="number" value={showNumber} /><TextField label="Bezeichnung" onChange={(event) => setName(event.target.value)} required value={name} /></Stack></DialogContent><DialogActions><Button disabled={saving} onClick={() => { setCreateOpen(false); setEditing(null) }}>Abbrechen</Button><Button disabled={saving || !name.trim() || Number(showNumber) < 1} onClick={() => void (editing === null ? create() : update())} variant="contained">{editing === null ? 'Anlegen' : 'Speichern'}</Button></DialogActions></Dialog>
  </Stack>
}
