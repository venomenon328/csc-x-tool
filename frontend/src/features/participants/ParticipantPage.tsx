import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  IconButton,
  Paper,
  Skeleton,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { useEffect, useState } from 'react'
import { ApiErrorNotice } from '../../components/ApiErrorNotice'
import { CountryFlag } from './CountryFlag'
import {
  ParticipantApiError,
  createParticipant,
  deleteParticipant,
  fetchCountries,
  fetchParticipants,
  updateParticipant,
  type Country,
  type Participant,
  type ParticipantInput,
} from './api'

type EditorState = {
  participant: Participant | null
  input: ParticipantInput
}

const emptyInput: ParticipantInput = { displayName: '', countryCode: '', active: true, aliases: [] }

export function ParticipantPage() {
  const [countries, setCountries] = useState<Country[]>([])
  const [participants, setParticipants] = useState<Participant[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<ParticipantApiError | null>(null)
  const [searchInput, setSearchInput] = useState('')
  const [activeSearch, setActiveSearch] = useState('')
  const [includeInactive, setIncludeInactive] = useState(false)
  const [editor, setEditor] = useState<EditorState | null>(null)
  const [participantToDelete, setParticipantToDelete] = useState<Participant | null>(null)

  const load = async (query = activeSearch, include = includeInactive) => {
    setIsLoading(true)
    setError(null)
    try {
      const [loadedCountries, loadedParticipants] = await Promise.all([
        fetchCountries(), fetchParticipants({ q: query, includeInactive: include }),
      ])
      setCountries(loadedCountries)
      setParticipants(loadedParticipants)
    } catch (caughtError) {
      setError(asParticipantApiError(caughtError, '/api/participants'))
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    let disposed = false
    void Promise.all([fetchCountries(), fetchParticipants()]).then(([loadedCountries, loadedParticipants]) => {
      if (disposed) return
      setCountries(loadedCountries)
      setParticipants(loadedParticipants)
      setIsLoading(false)
    }).catch((caughtError: unknown) => {
      if (disposed) return
      setError(asParticipantApiError(caughtError, '/api/participants'))
      setIsLoading(false)
    })
    return () => { disposed = true }
  }, [])

  const submitSearch = (event: React.FormEvent) => {
    event.preventDefault()
    setActiveSearch(searchInput)
    void load(searchInput, includeInactive)
  }

  const changeInactiveFilter = (checked: boolean) => {
    setIncludeInactive(checked)
    void load(activeSearch, checked)
  }

  const saveParticipant = async (input: ParticipantInput) => {
    if (editor === null) return
    try {
      if (editor.participant === null) {
        await createParticipant(input)
      } else {
        await updateParticipant(editor.participant.id, input)
      }
      setEditor(null)
      await load()
    } catch (caughtError) {
      throw asParticipantApiError(caughtError, '/api/participants')
    }
  }

  const confirmDelete = async () => {
    if (participantToDelete === null) return
    try {
      await deleteParticipant(participantToDelete.id)
      setParticipantToDelete(null)
      await load()
    } catch (caughtError) {
      setError(asParticipantApiError(caughtError, `/api/participants/${participantToDelete.id}`))
    }
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Typography component="h1" variant="h4">Teilnehmer</Typography>
        <Typography color="text.secondary">Andere CSC-Teilnehmer mit Land, Aliasnamen und Aktivstatus verwalten.</Typography>
      </Box>

      {error !== null && <ApiErrorNotice error={error.apiError} />}

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ alignItems: { sm: 'center' }, justifyContent: 'space-between' }}>
        <Box component="form" onSubmit={submitSearch} sx={{ display: 'flex', flexGrow: 1, gap: 1 }}>
          <TextField
            aria-label="Teilnehmer suchen"
            fullWidth
            onChange={(event) => setSearchInput(event.target.value)}
            placeholder="Name oder Alias suchen"
            value={searchInput}
          />
          <Button type="submit" variant="outlined">Suchen</Button>
        </Box>
        <FormControlLabel control={<Switch checked={includeInactive} onChange={(event) => changeInactiveFilter(event.target.checked)} />} label="Inaktive anzeigen" />
        <Button onClick={() => setEditor({ participant: null, input: emptyInput })} variant="contained">Teilnehmer anlegen</Button>
      </Stack>

      {isLoading ? <ParticipantLoading /> : (
        <ParticipantTable
          participants={participants}
          onDelete={setParticipantToDelete}
          onEdit={(participant) => setEditor({
            participant,
            input: {
              displayName: participant.displayName,
              countryCode: participant.countryCode,
              active: participant.active,
              aliases: participant.aliases,
            },
          })}
        />
      )}

      {editor !== null && <ParticipantEditor
        countries={countries}
        initialInput={editor.input}
        key={editor.participant?.id ?? 'new'}
        onClose={() => setEditor(null)}
        onSave={saveParticipant}
        title={editor.participant === null ? 'Teilnehmer anlegen' : 'Teilnehmer bearbeiten'}
      />}
      <DeleteParticipantDialog participant={participantToDelete} onClose={() => setParticipantToDelete(null)} onConfirm={confirmDelete} />
    </Stack>
  )
}

function ParticipantTable({ participants, onEdit, onDelete }: {
  participants: Participant[]
  onEdit: (participant: Participant) => void
  onDelete: (participant: Participant) => void
}) {
  if (participants.length === 0) {
    return <Paper sx={{ p: 3 }}><Typography>Noch keine passenden Teilnehmer vorhanden.</Typography></Paper>
  }
  return (
    <Paper sx={{ overflowX: 'auto' }}>
      <Table aria-label="Teilnehmerliste">
        <TableHead><TableRow><TableCell>Teilnehmer</TableCell><TableCell>Land</TableCell><TableCell>Aliasse</TableCell><TableCell>Status</TableCell><TableCell align="right">Aktionen</TableCell></TableRow></TableHead>
        <TableBody>
          {participants.map((participant) => (
            <TableRow key={participant.id} sx={participant.active ? undefined : { opacity: 0.65 }}>
              <TableCell><Typography sx={{ fontWeight: 600 }}>{participant.displayName}</Typography></TableCell>
              <TableCell><Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}><CountryFlag code={participant.countryCode} countryName={participant.countryName} /><span>{participant.countryName}</span></Stack></TableCell>
              <TableCell>{participant.aliases.length === 0 ? '—' : <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap' }}>{participant.aliases.map((alias) => <Chip key={alias} label={alias} size="small" />)}</Stack>}</TableCell>
              <TableCell>{participant.active ? 'Aktiv' : 'Inaktiv'}</TableCell>
              <TableCell align="right"><Button onClick={() => onEdit(participant)}>Bearbeiten</Button><Button color="error" onClick={() => onDelete(participant)}>Löschen</Button></TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Paper>
  )
}

function ParticipantEditor({ countries, initialInput, onClose, onSave, title }: {
  countries: Country[]
  initialInput: ParticipantInput
  onClose: () => void
  onSave: (input: ParticipantInput) => Promise<void>
  title: string
}) {
  const [input, setInput] = useState<ParticipantInput>(() => ({ ...initialInput, aliases: [...initialInput.aliases] }))
  const [error, setError] = useState<ParticipantApiError | null>(null)
  const [isSaving, setIsSaving] = useState(false)

  const save = async () => {
    setIsSaving(true)
    setError(null)
    try {
      await onSave(input)
    } catch (caughtError) {
      setError(asParticipantApiError(caughtError, '/api/participants'))
    } finally {
      setIsSaving(false)
    }
  }

  const selectedCountry = countries.find((country) => country.code === input.countryCode) ?? null
  const updateAlias = (index: number, alias: string) => setInput((current) => ({
    ...current,
    aliases: current.aliases.map((existingAlias, existingIndex) => existingIndex === index ? alias : existingAlias),
  }))

  return (
    <Dialog fullWidth maxWidth="sm" onClose={onClose} open>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent><Stack spacing={2} sx={{ pt: 1 }}>
        {error !== null && <Alert severity="error">{error.message}</Alert>}
        <TextField autoFocus label="Anzeigename" onChange={(event) => setInput((current) => ({ ...current, displayName: event.target.value }))} required value={input.displayName} />
        <Autocomplete
          getOptionKey={(country) => country.code}
          getOptionLabel={(country) => country.name}
          isOptionEqualToValue={(left, right) => left.code === right.code}
          onChange={(_, country) => setInput((current) => ({ ...current, countryCode: country?.code ?? '' }))}
          options={countries}
          renderInput={(params) => <TextField {...params} label="Land" required />}
          renderOption={(props, country) => <Box component="li" {...props} key={country.code} sx={{ alignItems: 'center', gap: 1 }}><CountryFlag code={country.code} countryName={country.name} />{country.name}</Box>}
          value={selectedCountry}
        />
        <FormControlLabel control={<Switch checked={input.active} onChange={(event) => setInput((current) => ({ ...current, active: event.target.checked }))} />} label="Aktiver Teilnehmer" />
        <Stack spacing={1}><Typography variant="subtitle2">Aliasse</Typography>
          {input.aliases.map((alias, index) => <Stack direction="row" key={index} spacing={1}><TextField fullWidth label={`Alias ${index + 1}`} onChange={(event) => updateAlias(index, event.target.value)} value={alias} /><IconButton aria-label={`Alias ${index + 1} entfernen`} onClick={() => setInput((current) => ({ ...current, aliases: current.aliases.filter((_, aliasIndex) => aliasIndex !== index) }))}>×</IconButton></Stack>)}
          <Button onClick={() => setInput((current) => ({ ...current, aliases: [...current.aliases, ''] }))} sx={{ alignSelf: 'flex-start' }}>Alias hinzufügen</Button>
        </Stack>
      </Stack></DialogContent>
      <DialogActions><Button onClick={onClose}>Abbrechen</Button><Button disabled={isSaving} onClick={() => void save()} variant="contained">Speichern</Button></DialogActions>
    </Dialog>
  )
}

function DeleteParticipantDialog({ participant, onClose, onConfirm }: {
  participant: Participant | null
  onClose: () => void
  onConfirm: () => void
}) {
  return <Dialog onClose={onClose} open={participant !== null}>
    <DialogTitle>Teilnehmer löschen?</DialogTitle>
    <DialogContent><Typography>{participant?.displayName ?? ''} wird dauerhaft samt Aliasnamen entfernt. Für ausgeschiedene Teilnehmer ist „Inaktiv“ meist die passende Alternative.</Typography></DialogContent>
    <DialogActions><Button onClick={onClose}>Abbrechen</Button><Button color="error" onClick={onConfirm} variant="contained">Teilnehmer löschen</Button></DialogActions>
  </Dialog>
}

function ParticipantLoading() {
  return <Stack spacing={1}>{Array.from({ length: 3 }, (_, index) => <Skeleton height={64} key={index} variant="rounded" />)}</Stack>
}

function asParticipantApiError(error: unknown, path: string): ParticipantApiError {
  if (error instanceof ParticipantApiError) return error
  return new ParticipantApiError({ timestamp: new Date().toISOString(), status: 0, code: 'NETWORK_ERROR', message: 'Die Teilnehmerdaten konnten nicht verarbeitet werden.', path })
}
