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
import { ContestApiError, setOwnParticipation } from '../contests/api'
import { useContest } from '../contests/ContestContext'
import { CountryFlag } from './CountryFlag'
import {
  ParticipantApiError,
  addExistingParticipant,
  createParticipant,
  deleteParticipant,
  fetchBotbSelections,
  fetchCountries,
  fetchParticipantIdentities,
  fetchParticipants,
  updateParticipantIdentity,
  updateParticipation,
  replaceBotbSelections,
  type BotbSelectionInput,
  type Country,
  type IdentityInput,
  type Participant,
  type ParticipantIdentity,
  type ParticipantInput,
  type ParticipationInput,
} from './api'

type EditorState =
  | { kind: 'new', input: ParticipantInput }
  | { kind: 'identity', participant: Participant, input: IdentityInput }
  | { kind: 'participation', participant: Participant, input: ParticipationInput }

type ExistingIdentityEditorState = { identities: ParticipantIdentity[] }
type BotbEditorState = { participant: Participant, selections: BotbSelectionInput[] }

const emptyInput: ParticipantInput = { displayName: '', countryCode: '', active: true, aliases: [] }

export function ParticipantPage() {
  const { selectedContestId, selectedContest, refresh } = useContest()
  const [countries, setCountries] = useState<Country[]>([])
  const [participants, setParticipants] = useState<Participant[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<ParticipantApiError | null>(null)
  const [searchInput, setSearchInput] = useState('')
  const [activeSearch, setActiveSearch] = useState('')
  const [includeInactive, setIncludeInactive] = useState(false)
  const [editor, setEditor] = useState<EditorState | null>(null)
  const [existingIdentityEditor, setExistingIdentityEditor] = useState<ExistingIdentityEditorState | null>(null)
  const [participantToDelete, setParticipantToDelete] = useState<Participant | null>(null)
  const [botbEditor, setBotbEditor] = useState<BotbEditorState | null>(null)

  const load = async (query = activeSearch, include = includeInactive) => {
    if (selectedContestId === null) {
      setParticipants([])
      setIsLoading(false)
      return
    }
    setIsLoading(true)
    setError(null)
    try {
      const [loadedCountries, loadedParticipants] = await Promise.all([
        fetchCountries(), fetchParticipants({ contestId: selectedContestId, q: query, includeInactive: include }),
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
    if (selectedContestId === null) {
      void Promise.resolve().then(() => {
        if (disposed) return
        setParticipants([])
        setIsLoading(false)
      })
      return () => { disposed = true }
    }
    void Promise.all([fetchCountries(), fetchParticipants({ contestId: selectedContestId })]).then(([loadedCountries, loadedParticipants]) => {
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
  }, [selectedContestId])

  const submitSearch = (event: React.FormEvent) => {
    event.preventDefault()
    setActiveSearch(searchInput)
    void load(searchInput, includeInactive)
  }

  const changeInactiveFilter = (checked: boolean) => {
    setIncludeInactive(checked)
    void load(activeSearch, checked)
  }

  const createNewParticipant = async (input: ParticipantInput) => {
    if (selectedContestId === null) return
    try {
      await createParticipant(selectedContestId, input)
      setEditor(null)
      await load()
    } catch (caughtError) {
      throw asParticipantApiError(caughtError, '/api/contests/' + selectedContestId + '/participants')
    }
  }

  const saveIdentity = async (participantId: number, input: IdentityInput) => {
    try {
      await updateParticipantIdentity(participantId, input)
      setEditor(null)
      await load()
    } catch (caughtError) {
      throw asParticipantApiError(caughtError, '/api/participants/' + participantId)
    }
  }

  const saveParticipation = async (participantId: number, input: ParticipationInput) => {
    if (selectedContestId === null) return
    try {
      await updateParticipation(selectedContestId, participantId, input)
      setEditor(null)
      await load()
    } catch (caughtError) {
      throw asParticipantApiError(caughtError, '/api/contests/' + selectedContestId + '/participants/' + participantId)
    }
  }

  const openBotbEditor = async (participant: Participant) => {
    setError(null)
    try {
      const selections = await fetchBotbSelections(participant.id)
      setBotbEditor({
        participant,
        selections: selections.map(({ id, editionNumber, artist, knownSince }) => ({ id, editionNumber, artist, knownSince })),
      })
    } catch (caughtError) {
      setError(asParticipantApiError(caughtError, '/api/participants/' + participant.id + '/botb-selections'))
    }
  }

  const saveBotbSelections = async (participantId: number, selections: BotbSelectionInput[]) => {
    try {
      await replaceBotbSelections(participantId, selections)
      setBotbEditor(null)
      await load()
    } catch (caughtError) {
      throw asParticipantApiError(caughtError, '/api/participants/' + participantId + '/botb-selections')
    }
  }

  const openExistingIdentityEditor = async () => {
    if (selectedContestId === null) return
    setError(null)
    try {
      setExistingIdentityEditor({ identities: await fetchParticipantIdentities() })
    } catch (caughtError) {
      setError(asParticipantApiError(caughtError, '/api/participants'))
    }
  }

  const addExistingIdentity = async (identity: ParticipantIdentity, input: ParticipationInput) => {
    if (selectedContestId === null) return
    try {
      await addExistingParticipant(selectedContestId, identity.id, input)
      setExistingIdentityEditor(null)
      await load()
    } catch (caughtError) {
      throw asParticipantApiError(caughtError, '/api/contests/' + selectedContestId + '/participants')
    }
  }

  const confirmDelete = async () => {
    if (participantToDelete === null || selectedContestId === null) return
    try {
      await deleteParticipant(selectedContestId, participantToDelete.id)
      setParticipantToDelete(null)
      await load()
    } catch (caughtError) {
      setError(asParticipantApiError(caughtError, `/api/contests/${selectedContestId}/participants/${participantToDelete.id}`))
    }
  }

  const chooseOwnParticipation = async (participationId: number | null) => {
    if (selectedContest === null) return
    try {
      await setOwnParticipation(selectedContest.id, participationId)
      await refresh()
      await load()
    } catch (caughtError) {
      if (caughtError instanceof ContestApiError
        && caughtError.apiError.code === 'OWN_PARTICIPATION_CHANGE_CONFIRMATION_REQUIRED'
        && window.confirm('Die eigene Teilnahme wird bereits für abgeleitete Ergebnisse verwendet. Bezug bewusst ändern?')) {
        try {
          await setOwnParticipation(selectedContest.id, participationId, true)
          await refresh()
          await load()
        } catch (retry) {
          setError(asParticipantApiError(retry, `/api/contests/${selectedContest.id}/own-participation`))
        }
        return
      }
      setError(asParticipantApiError(caughtError, `/api/contests/${selectedContest.id}/own-participation`))
    }
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Typography component="h1" variant="h4">Teilnehmer{selectedContest === null ? '' : ' · ' + selectedContest.name}</Typography>
        <Typography color="text.secondary">Dauerhafte Identitäten, Aliasnamen und die Teilnahme an dieser CSC-Ausgabe verwalten.</Typography>
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
        <Button onClick={() => void openExistingIdentityEditor()} variant="outlined">Vorhandene Identität hinzufügen</Button>
        <Button onClick={() => setEditor({ kind: 'new', input: emptyInput })} variant="contained">Teilnehmer anlegen</Button>
      </Stack>

      {isLoading ? <ParticipantLoading /> : (
        <ParticipantTable
          ownParticipationId={selectedContest?.ownParticipationId ?? null}
          participants={participants}
          onDelete={setParticipantToDelete}
          onEditIdentity={(participant) => setEditor({
            kind: 'identity', participant, input: { displayName: participant.displayName, aliases: participant.aliases },
          })}
          onEditParticipation={(participant) => setEditor({
            kind: 'participation', participant, input: { countryCode: participant.countryCode, active: participant.active },
          })}
          onEditBotb={(participant) => void openBotbEditor(participant)}
          onSetOwnParticipation={(participationId) => void chooseOwnParticipation(participationId)}
        />
      )}

      {editor?.kind === 'new' && <NewParticipantEditor
        countries={countries}
        initialInput={editor.input}
        onClose={() => setEditor(null)}
        onSave={createNewParticipant}
      />}
      {editor?.kind === 'identity' && <IdentityEditor
        initialInput={editor.input}
        onClose={() => setEditor(null)}
        onSave={(input) => saveIdentity(editor.participant.id, input)}
        title={'Identität bearbeiten · ' + editor.participant.displayName}
      />}
      {editor?.kind === 'participation' && <ParticipationEditor
        countries={countries}
        initialInput={editor.input}
        onClose={() => setEditor(null)}
        onSave={(input) => saveParticipation(editor.participant.id, input)}
        title={'Teilnahme bearbeiten · ' + editor.participant.displayName}
      />}
      {existingIdentityEditor !== null && <ExistingIdentityParticipationEditor
        countries={countries}
        identities={existingIdentityEditor.identities}
        onClose={() => setExistingIdentityEditor(null)}
        onSave={addExistingIdentity}
      />}
      {botbEditor !== null && <BotbSelectionEditor
        initialSelections={botbEditor.selections}
        onClose={() => setBotbEditor(null)}
        onSave={(selections) => saveBotbSelections(botbEditor.participant.id, selections)}
        title={'BOTB bearbeiten · ' + botbEditor.participant.displayName}
      />}
      <DeleteParticipantDialog participant={participantToDelete} onClose={() => setParticipantToDelete(null)} onConfirm={confirmDelete} />
    </Stack>
  )
}

function ParticipantTable({ participants, ownParticipationId, onEditIdentity, onEditParticipation, onEditBotb, onDelete, onSetOwnParticipation }: {
  participants: Participant[]
  ownParticipationId: number | null
  onEditIdentity: (participant: Participant) => void
  onEditParticipation: (participant: Participant) => void
  onEditBotb: (participant: Participant) => void
  onDelete: (participant: Participant) => void
  onSetOwnParticipation: (participationId: number | null) => void
}) {
  if (participants.length === 0) {
    return <Paper sx={{ p: 3 }}><Typography>Noch keine passenden Teilnehmer vorhanden.</Typography></Paper>
  }
  return (
    <Paper sx={{ overflowX: 'auto' }}>
      <Table aria-label="Teilnehmerliste">
        <TableHead><TableRow><TableCell>Teilnehmer</TableCell><TableCell>Land</TableCell><TableCell>Aliasse</TableCell><TableCell align="right">BOTB</TableCell><TableCell>Status</TableCell><TableCell align="right">Aktionen</TableCell></TableRow></TableHead>
        <TableBody>
          {participants.map((participant) => (
            <TableRow key={participant.id} sx={participant.active ? undefined : { opacity: 0.65 }}>
              <TableCell><Stack direction="row" spacing={0.75} sx={{ alignItems: 'center' }}><Typography sx={{ fontWeight: 600 }}>{participant.displayName}</Typography>{participant.participationId === ownParticipationId && <Chip color="secondary" label="Meine Teilnahme" size="small" />}</Stack></TableCell>
              <TableCell><Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}><CountryFlag code={participant.countryCode} countryName={participant.countryName} /><span>{participant.countryName}</span></Stack></TableCell>
              <TableCell>{participant.aliases.length === 0 ? '—' : <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap' }}>{participant.aliases.map((alias) => <Chip key={alias} label={alias} size="small" />)}</Stack>}</TableCell>
              <TableCell align="right">{participant.botbSelectionCount ?? 0}</TableCell>
              <TableCell>{participant.active ? 'Aktiv' : 'Inaktiv'}</TableCell>
              <TableCell align="right">
                {participant.participationId !== undefined && <Button onClick={() => {
                  const participationId = participant.participationId
                  if (participationId !== undefined) onSetOwnParticipation(participationId === ownParticipationId ? null : participationId)
                }}>{participant.participationId === ownParticipationId ? 'Eigene Teilnahme aufheben' : 'Als meine Teilnahme markieren'}</Button>}
                <Button onClick={() => onEditParticipation(participant)}>Teilnahme bearbeiten</Button>
                <Button onClick={() => onEditIdentity(participant)}>Identität bearbeiten</Button>
                <Button onClick={() => onEditBotb(participant)}>BOTB bearbeiten</Button>
                <Button color="error" onClick={() => onDelete(participant)}>Entfernen</Button>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Paper>
  )
}

function NewParticipantEditor({ countries, initialInput, onClose, onSave }: {
  countries: Country[]
  initialInput: ParticipantInput
  onClose: () => void
  onSave: (input: ParticipantInput) => Promise<void>
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

  return (
    <Dialog fullWidth maxWidth="sm" onClose={onClose} open>
      <DialogTitle>Teilnehmer anlegen</DialogTitle>
      <DialogContent><Stack spacing={2} sx={{ pt: 1 }}>
        {error !== null && <Alert severity="error">{error.message}</Alert>}
        <Typography variant="subtitle2">Dauerhafte Identität</Typography>
        <TextField autoFocus label="Anzeigename" onChange={(event) => setInput((current) => ({ ...current, displayName: event.target.value }))} required value={input.displayName} />
        <Typography variant="subtitle2">Teilnahme in dieser CSC-Ausgabe</Typography>
        <CountryInput countries={countries} countryCode={input.countryCode} onChange={(countryCode) => setInput((current) => ({ ...current, countryCode }))} />
        <FormControlLabel control={<Switch checked={input.active} onChange={(event) => setInput((current) => ({ ...current, active: event.target.checked }))} />} label="Aktiver Teilnehmer" />
        <AliasEditor aliases={input.aliases} onChange={(aliases) => setInput((current) => ({ ...current, aliases }))} />
      </Stack></DialogContent>
      <DialogActions><Button onClick={onClose}>Abbrechen</Button><Button disabled={isSaving} onClick={() => void save()} variant="contained">Speichern</Button></DialogActions>
    </Dialog>
  )
}

function IdentityEditor({ initialInput, onClose, onSave, title }: {
  initialInput: IdentityInput
  onClose: () => void
  onSave: (input: IdentityInput) => Promise<void>
  title: string
}) {
  const [input, setInput] = useState<IdentityInput>(() => ({ ...initialInput, aliases: [...initialInput.aliases] }))
  const [error, setError] = useState<ParticipantApiError | null>(null)
  const [isSaving, setIsSaving] = useState(false)
  const save = async () => {
    setIsSaving(true)
    setError(null)
    try { await onSave(input) } catch (caughtError) { setError(asParticipantApiError(caughtError, '/api/participants')) } finally { setIsSaving(false) }
  }
  return (
    <Dialog fullWidth maxWidth="sm" onClose={onClose} open>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent><Stack spacing={2} sx={{ pt: 1 }}>
        {error !== null && <Alert severity="error">{error.message}</Alert>}
        <Typography color="text.secondary">Diese Daten gehören dauerhaft zur Identität und gelten in allen CSC-Ausgaben.</Typography>
        <TextField autoFocus label="Anzeigename" onChange={(event) => setInput((current) => ({ ...current, displayName: event.target.value }))} required value={input.displayName} />
        <AliasEditor aliases={input.aliases} onChange={(aliases) => setInput((current) => ({ ...current, aliases }))} />
      </Stack></DialogContent>
      <DialogActions><Button onClick={onClose}>Abbrechen</Button><Button disabled={isSaving} onClick={() => void save()} variant="contained">Speichern</Button></DialogActions>
    </Dialog>
  )
}

function BotbSelectionEditor({ initialSelections, onClose, onSave, title }: {
  initialSelections: BotbSelectionInput[]
  onClose: () => void
  onSave: (selections: BotbSelectionInput[]) => Promise<void>
  title: string
}) {
  const [selections, setSelections] = useState<BotbSelectionInput[]>(() => initialSelections.map((selection) => ({ ...selection })))
  const [error, setError] = useState<string | null>(null)
  const [isSaving, setIsSaving] = useState(false)

  const update = (index: number, value: Partial<BotbSelectionInput>) => {
    setSelections((current) => current.map((selection, currentIndex) => currentIndex === index ? { ...selection, ...value } : selection))
  }

  const save = async () => {
    const validationError = validateBotbSelections(selections)
    if (validationError !== null) {
      setError(validationError)
      return
    }
    setIsSaving(true)
    setError(null)
    try {
      await onSave(selections)
    } catch (caughtError) {
      setError(asParticipantApiError(caughtError, '/api/participants/botb-selections').message)
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <Dialog fullWidth maxWidth="md" onClose={onClose} open>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent><Stack spacing={2} sx={{ pt: 1 }}>
        <Typography color="text.secondary">Fehlende Einträge bedeuten nur, dass keine BOTB-Auswahl erfasst wurde. Sie belegen keine Nichtteilnahme.</Typography>
        {error !== null && <Alert severity="error">{error}</Alert>}
        {selections.map((selection, index) => <Stack direction={{ xs: 'column', sm: 'row' }} key={selection.id ?? `new-${index}`} spacing={1}>
          <TextField
            inputProps={{ min: 1 }}
            label="BOTB-Ausgabe"
            onChange={(event) => update(index, { editionNumber: event.target.value === '' ? 0 : Number(event.target.value) })}
            required
            type="number"
            value={selection.editionNumber || ''}
          />
          <TextField
            fullWidth
            label="Interpret"
            onChange={(event) => update(index, { artist: event.target.value })}
            required
            value={selection.artist}
          />
          <TextField
            InputLabelProps={{ shrink: true }}
            label="Bekannt seit"
            onChange={(event) => update(index, { knownSince: event.target.value || null })}
            type="date"
            value={selection.knownSince ?? ''}
          />
          <IconButton aria-label={`BOTB-Auswahl ${index + 1} entfernen`} onClick={() => setSelections((current) => current.filter((_, currentIndex) => currentIndex !== index))}>×</IconButton>
        </Stack>)}
        <Button onClick={() => setSelections((current) => [...current, { editionNumber: 0, artist: '', knownSince: null }])} sx={{ alignSelf: 'flex-start' }}>Zeile hinzufügen</Button>
      </Stack></DialogContent>
      <DialogActions><Button disabled={isSaving} onClick={onClose}>Abbrechen</Button><Button disabled={isSaving} onClick={() => void save()} variant="contained">Speichern</Button></DialogActions>
    </Dialog>
  )
}

function validateBotbSelections(selections: BotbSelectionInput[]): string | null {
  const editions = new Set<number>()
  for (const selection of selections) {
    if (!Number.isInteger(selection.editionNumber) || selection.editionNumber < 1) return 'Die BOTB-Ausgabe muss positiv sein.'
    if (!editions.add(selection.editionNumber)) return 'Pro Teilnehmer darf jede BOTB-Ausgabe nur einmal erfasst werden.'
    if (!selection.artist.trim()) return 'Der BOTB-Interpret darf nicht leer sein.'
    if (selection.knownSince !== null && !/^\d{4}-\d{2}-\d{2}$/.test(selection.knownSince)) return 'Der Bekannt-seit-Zeitpunkt ist nicht gültig.'
  }
  return null
}

function ParticipationEditor({ countries, initialInput, onClose, onSave, title }: {
  countries: Country[]
  initialInput: ParticipationInput
  onClose: () => void
  onSave: (input: ParticipationInput) => Promise<void>
  title: string
}) {
  const [input, setInput] = useState<ParticipationInput>(initialInput)
  const [error, setError] = useState<ParticipantApiError | null>(null)
  const [isSaving, setIsSaving] = useState(false)
  const save = async () => {
    setIsSaving(true)
    setError(null)
    try { await onSave(input) } catch (caughtError) { setError(asParticipantApiError(caughtError, '/api/participants')) } finally { setIsSaving(false) }
  }
  return (
    <Dialog fullWidth maxWidth="sm" onClose={onClose} open>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent><Stack spacing={2} sx={{ pt: 1 }}>
        {error !== null && <Alert severity="error">{error.message}</Alert>}
        <Typography color="text.secondary">Diese Angaben gelten ausschließlich für die ausgewählte CSC-Ausgabe.</Typography>
        <CountryInput countries={countries} countryCode={input.countryCode} onChange={(countryCode) => setInput((current) => ({ ...current, countryCode }))} />
        <FormControlLabel control={<Switch checked={input.active} onChange={(event) => setInput((current) => ({ ...current, active: event.target.checked }))} />} label="Aktiver Teilnehmer" />
      </Stack></DialogContent>
      <DialogActions><Button onClick={onClose}>Abbrechen</Button><Button disabled={isSaving} onClick={() => void save()} variant="contained">Speichern</Button></DialogActions>
    </Dialog>
  )
}

function ExistingIdentityParticipationEditor({ countries, identities, onClose, onSave }: {
  countries: Country[]
  identities: ParticipantIdentity[]
  onClose: () => void
  onSave: (identity: ParticipantIdentity, input: ParticipationInput) => Promise<void>
}) {
  const [identity, setIdentity] = useState<ParticipantIdentity | null>(null)
  const [input, setInput] = useState<ParticipationInput>({ countryCode: '', active: true })
  const [error, setError] = useState<ParticipantApiError | null>(null)
  const [isSaving, setIsSaving] = useState(false)
  const save = async () => {
    if (identity === null) return
    setIsSaving(true)
    setError(null)
    try { await onSave(identity, input) } catch (caughtError) { setError(asParticipantApiError(caughtError, '/api/participants')) } finally { setIsSaving(false) }
  }
  return (
    <Dialog fullWidth maxWidth="sm" onClose={onClose} open>
      <DialogTitle>Vorhandene Identität hinzufügen</DialogTitle>
      <DialogContent><Stack spacing={2} sx={{ pt: 1 }}>
        {error !== null && <Alert severity="error">{error.message}</Alert>}
        <Typography color="text.secondary">Die ausgewählte Identität wird dieser CSC-Ausgabe zugeordnet. Anzeigename und Aliasse bleiben unverändert.</Typography>
        <Autocomplete
          getOptionKey={(option) => option.id}
          getOptionLabel={(option) => option.displayName}
          isOptionEqualToValue={(left, right) => left.id === right.id}
          onChange={(_, selected) => setIdentity(selected)}
          options={identities}
          renderInput={(params) => <TextField {...params} autoFocus label="Vorhandene Identität" required />}
          renderOption={(props, option) => <Box component="li" {...props} key={option.id}><Stack><span>{option.displayName}</span>{option.aliases.length > 0 && <Typography color="text.secondary" variant="caption">Aliasse: {option.aliases.join(', ')}</Typography>}</Stack></Box>}
          value={identity}
        />
        <Typography variant="subtitle2">Teilnahme in dieser CSC-Ausgabe</Typography>
        <CountryInput countries={countries} countryCode={input.countryCode} onChange={(countryCode) => setInput((current) => ({ ...current, countryCode }))} />
        <FormControlLabel control={<Switch checked={input.active} onChange={(event) => setInput((current) => ({ ...current, active: event.target.checked }))} />} label="Aktiver Teilnehmer" />
      </Stack></DialogContent>
      <DialogActions><Button onClick={onClose}>Abbrechen</Button><Button disabled={isSaving || identity === null} onClick={() => void save()} variant="contained">Zuordnen</Button></DialogActions>
    </Dialog>
  )
}

function CountryInput({ countries, countryCode, onChange }: { countries: Country[], countryCode: string, onChange: (countryCode: string) => void }) {
  const selectedCountry = countries.find((country) => country.code === countryCode) ?? null
  return <Autocomplete
    getOptionKey={(country) => country.code}
    getOptionLabel={(country) => country.name}
    isOptionEqualToValue={(left, right) => left.code === right.code}
    onChange={(_, country) => onChange(country?.code ?? '')}
    options={countries}
    renderInput={(params) => <TextField {...params} label="Land" required />}
    renderOption={(props, country) => <Box component="li" {...props} key={country.code} sx={{ alignItems: 'center', gap: 1 }}><CountryFlag code={country.code} countryName={country.name} />{country.name}</Box>}
    value={selectedCountry}
  />
}

function AliasEditor({ aliases, onChange }: { aliases: string[], onChange: (aliases: string[]) => void }) {
  const updateAlias = (index: number, alias: string) => onChange(aliases.map((existingAlias, existingIndex) => existingIndex === index ? alias : existingAlias))
  return <Stack spacing={1}><Typography variant="subtitle2">Dauerhafte Aliasse</Typography>
    {aliases.map((alias, index) => <Stack direction="row" key={index} spacing={1}><TextField fullWidth label={`Alias ${index + 1}`} onChange={(event) => updateAlias(index, event.target.value)} value={alias} /><IconButton aria-label={`Alias ${index + 1} entfernen`} onClick={() => onChange(aliases.filter((_, aliasIndex) => aliasIndex !== index))}>×</IconButton></Stack>)}
    <Button onClick={() => onChange([...aliases, ''])} sx={{ alignSelf: 'flex-start' }}>Alias hinzufügen</Button>
  </Stack>
}

function DeleteParticipantDialog({ participant, onClose, onConfirm }: {
  participant: Participant | null
  onClose: () => void
  onConfirm: () => void
}) {
  return <Dialog onClose={onClose} open={participant !== null}>
    <DialogTitle>Teilnahme entfernen?</DialogTitle>
    <DialogContent><Typography>{participant?.displayName ?? ''} wird nur aus dieser CSC-Ausgabe entfernt. Die dauerhafte Identität und ihre Aliasse bleiben erhalten.</Typography></DialogContent>
    <DialogActions><Button onClick={onClose}>Abbrechen</Button><Button color="error" onClick={onConfirm} variant="contained">Entfernen</Button></DialogActions>
  </Dialog>
}

function ParticipantLoading() {
  return <Stack spacing={1}>{Array.from({ length: 3 }, (_, index) => <Skeleton height={64} key={index} variant="rounded" />)}</Stack>
}

function asParticipantApiError(error: unknown, path: string): ParticipantApiError {
  if (error instanceof ParticipantApiError) return error
  if (error instanceof ContestApiError) return new ParticipantApiError(error.apiError)
  return new ParticipantApiError({ timestamp: new Date().toISOString(), status: 0, code: 'NETWORK_ERROR', message: 'Die Teilnehmerdaten konnten nicht verarbeitet werden.', path })
}
