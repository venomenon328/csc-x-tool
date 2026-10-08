import { Alert, Autocomplete, Box, Button, Paper, Stack, TextField, Typography } from '@mui/material'
import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useContest } from '../contests/ContestContext'
import { closureLabels } from '../standings/contestApi'
import { fetchStatistics, type Direction, type Statistics } from './api'
import { StatisticsNavigation } from './StatisticsNavigation'
import { Heatmap, RelationList, RelationshipDetail } from './RelationshipViews'
import { EvidenceDetail, ProfileView, StatisticsRecords, type Evidence } from './ProfileAndRecords'

export function StatisticsPage({ view }: { view: 'participants' | 'relationships' | 'records' }) {
  const { selectedContestId, selectedContest } = useContest()
  const [query] = useSearchParams()
  const title = { participants: 'Teilnehmerprofile', relationships: 'Punktebeziehungen', records: 'Rekorde' }[view]
  return <Stack spacing={3}><Box><Typography variant="overline" color="secondary">{selectedContest?.name}</Typography><Typography component="h1" variant="h4">{title}</Typography></Box><StatisticsNavigation />
    {selectedContestId === null ? <Alert severity="info">Bitte eine CSC-Ausgabe auswählen.</Alert> : <StatisticsContent key={`${selectedContestId}-${view}-${view === 'participants' ? query.get('participant') : ''}`} contestId={selectedContestId} view={view} />}
  </Stack>
}

function StatisticsContent({ contestId, view }: { contestId: number, view: 'participants' | 'relationships' | 'records' }) {
  const [data, setData] = useState<Statistics | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const [direction, setDirection] = useState<Direction | null>(null)
  const [evidence, setEvidence] = useState<Evidence | null>(null)
  const [all, setAll] = useState(false)
  const [query, setQuery] = useSearchParams()
  const refresh = useCallback(() => { setData(null); setDirection(null); setEvidence(null); setError(null); setRevision(n => n + 1) }, [])
  useEffect(() => {
    let cancelled = false
    void fetchStatistics(contestId).then(loaded => {
      if (!cancelled && loaded.standings.contestId === contestId) setData(loaded)
    }).catch((caught: unknown) => { if (!cancelled) setError(caught instanceof Error ? caught.message : 'Statistiken konnten nicht geladen werden.') })
    return () => { cancelled = true }
  }, [contestId, revision])
  useEffect(() => {
    window.addEventListener('focus', refresh)
    return () => window.removeEventListener('focus', refresh)
  }, [refresh])
  if (error) return <Alert severity="error" action={<Button onClick={refresh}>Erneut laden</Button>}>{error}</Alert>
  if (!data) return <Typography>Statistiken werden geladen …</Typography>
  const selectedId = Number(query.get('participant'))
  const selected = data.standings.rows.find(r => r.participationId === selectedId) ?? null
  const profile = data.profiles.find(p => p.participationId === selected?.participationId)
  const topKeys = new Set(data.topRelations.map(d => `${d.giverId}-${d.receiverId}`))
  const relations = all ? data.relations.filter(r => r.opportunities > 0) : data.relations.filter(r => topKeys.has(`${r.giverId}-${r.receiverId}`))
  return <Stack spacing={3}>
    <Paper sx={{ p: 2 }}><Typography component="h2" variant="h6">Datenbasis: {data.standings.includedShowIds.length} von {data.standings.shows.length} Shows gewertet</Typography><Typography>{data.standings.shows.map(s => `Show ${s.showNumber}: ${closureLabels[s.status]}`).join(' · ')}</Typography><Typography color="text.secondary">Nur veröffentlichte Stimmzettel abgeschlossener Shows. Summen vergleichen Stimmzettelpunkte; fehlende Gelegenheiten sind keine Nullwerte.</Typography><Button onClick={refresh}>Statistiken aktualisieren</Button></Paper>
    {!data.standings.includedShowIds.length && <Alert severity="info">Noch keine gewertete Show. Es gibt keine Statistikrekordsieger.</Alert>}
    {view === 'participants' && <><Autocomplete options={data.standings.rows} value={selected} getOptionKey={r => r.participationId} getOptionLabel={r => `${r.displayName} · ${r.countryName}`} isOptionEqualToValue={(a, b) => a.participationId === b.participationId} onChange={(_, person) => { setDirection(null); setEvidence(null); setQuery(person ? { participant: String(person.participationId) } : {}) }} renderInput={params => <TextField {...params} label="Teilnehmerprofil auswählen" />} />{profile ? <ProfileView key={profile.participationId} data={data} profile={profile} openRelation={setDirection} openEvidence={setEvidence} /> : <Typography>Wähle eine Contest-Teilnahme für ihr Profil.</Typography>}</>}
    {view === 'relationships' && <><RelationList key={`${revision}-${all}`} data={data} relations={relations} title={all ? 'Alle gerichteten Beziehungen einschließlich bekannter Nullen' : 'Globale Top-Beziehungen mit Grenzgleichständen'} open={setDirection} /><Button onClick={() => setAll(v => !v)}>{all ? 'Top-Beziehungen anzeigen' : 'Vollständige Beziehungsliste anzeigen'}</Button><Heatmap key={revision} data={data} open={setDirection} /></>}
    {view === 'records' && <StatisticsRecords data={data} openRelation={setDirection} openEvidence={setEvidence} />}
    <RelationshipDetail data={data} direction={direction} close={() => setDirection(null)} />
    <EvidenceDetail data={data} evidence={evidence} close={() => setEvidence(null)} openRelation={setDirection} />
  </Stack>
}
