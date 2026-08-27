import { Autocomplete, Box, TextField, Typography } from '@mui/material'
import { CountryFlag } from './CountryFlag'
import { filterParticipants } from './participantSearch'
import type { Participant } from './api'

type ParticipantSelectProps = {
  label?: string
  options: Participant[]
  value: Participant | null
  onChange: (participant: Participant | null) => void
  includeInactive?: boolean
  disabled?: boolean
}

export function ParticipantSelect({
  label = 'Teilnehmer', options, value, onChange, includeInactive = false, disabled = false,
}: ParticipantSelectProps) {
  const selectableParticipants = options.filter((participant) => includeInactive || participant.active)
  return (
    <Autocomplete
      disabled={disabled}
      filterOptions={(participants, state) => filterParticipants(participants, state.inputValue)}
      getOptionKey={(participant) => participant.id}
      getOptionLabel={participantLabel}
      isOptionEqualToValue={(left, right) => left.id === right.id}
      onChange={(_, participant) => onChange(participant)}
      options={selectableParticipants}
      renderInput={(params) => <TextField {...params} label={label} />}
      renderOption={(props, participant) => (
        <Box component="li" {...props} key={participant.id} sx={{ alignItems: 'center', gap: 1 }}>
          <CountryFlag code={participant.countryCode} countryName={participant.countryName} />
          <Box>
            <Typography>{participant.displayName}{participant.active ? '' : ' (inaktiv)'}</Typography>
            <Typography color="text.secondary" variant="body2">{participant.countryName}</Typography>
          </Box>
        </Box>
      )}
      value={value}
    />
  )
}

function participantLabel(participant: Participant): string {
  return `${participant.displayName} – ${participant.countryName}${participant.active ? '' : ' (inaktiv)'}`
}
