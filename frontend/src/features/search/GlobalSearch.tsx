import { Autocomplete, TextField } from '@mui/material'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { searchSongs, type SearchResult } from './api'
import { useContest } from '../contests/ContestContext'

export function GlobalSearch() {
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<SearchResult[]>([])
  const [loading, setLoading] = useState(false)
  const { selectedContestId } = useContest()

  useEffect(() => {
    const normalized = query.trim()
    if (!normalized) return undefined
    let active = true
    const timer = window.setTimeout(() => {
      setLoading(true)
      void searchSongs(normalized, selectedContestId)
        .then((response) => { if (active) setResults(response) })
        .catch(() => { if (active) setResults([]) })
        .finally(() => { if (active) setLoading(false) })
    }, 160)
    return () => {
      active = false
      window.clearTimeout(timer)
    }
  }, [query, selectedContestId])

  return (
    <Autocomplete
      clearOnEscape
      filterOptions={(options) => options}
      getOptionKey={(option) => `${option.type}-${option.id}`}
      getOptionLabel={(option) => `${option.artist} – ${option.title}`}
      loading={loading}
      noOptionsText={query.trim() ? 'Keine Treffer' : 'Interpret oder Titel eingeben'}
      onChange={(_, option) => {
        if (!option) return
        navigate(`/shows/${option.showId}/${option.type === 'CANDIDATE' ? 'candidates' : 'voting'}`)
        setResults([])
        setQuery('')
      }}
      onInputChange={(_, value) => {
        setQuery(value)
        if (!value.trim()) setResults([])
      }}
      options={results}
      renderInput={(params) => (
        <TextField
          {...params}
          label="Globale Suche"
          placeholder="Interpret oder Titel"
        />
      )}
      renderOption={(props, option) => (
        <li {...props} key={`${option.type}-${option.id}`}>
          {option.artist} – {option.title} · {option.type === 'CANDIDATE' ? 'Kandidat' : 'Beitrag'} · Show {option.showNumber}
        </li>
      )}
      sx={{ minWidth: 320, px: 2, pb: 2 }}
    />
  )
}
