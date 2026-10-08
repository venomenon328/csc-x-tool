import { Button, Stack } from '@mui/material'
import { Link as RouterLink } from 'react-router-dom'

export function StatisticsNavigation() {
  return <Stack component="nav" aria-label="Contestauswertung" direction="row" sx={{ flexWrap: 'wrap', gap: 1 }}>
    {[['Gesamtwertung', '/standings'], ['Teilnehmerprofile', '/statistics/participants'], ['Punktebeziehungen', '/statistics/relationships'], ['Rekorde', '/statistics/records']].map(([label, path]) =>
      <Button component={RouterLink} to={path} key={path}>{label}</Button>)}
  </Stack>
}
