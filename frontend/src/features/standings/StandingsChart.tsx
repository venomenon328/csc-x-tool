import { Box, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Typography } from '@mui/material'
import type { ContestShow, ContestStanding } from './contestApi'

const colors = ['#61dafb', '#ffc857', '#ce93d8', '#81c784', '#ff8a80', '#b0bec5']

export function StandingsChart({ rows, shows, metric }: { rows: ContestStanding[], shows: ContestShow[], metric: 'points' | 'rank' }) {
  if (!rows.length) return <Typography color="text.secondary">Wähle Teilnehmer, um ihren Verlauf zu sehen.</Typography>
  const label = metric === 'points' ? 'Gesamtwertungspunkte' : 'Contestplatz'
  const numbers = shows.map((s) => s.showNumber)
  const first = Math.min(...numbers, 1)
  const last = Math.max(...numbers, first + 1)
  const values = rows.flatMap((r) => r.history.map((s) => metric === 'points' ? s.totalPoints : s.rank)).filter((v): v is number => v !== null)
  const max = Math.max(...values, 1)
  const x = (n: number) => 60 + (n - first) / (last - first) * 760
  const y = (n: number) => metric === 'rank' ? 40 + (n - 1) / Math.max(max - 1, 1) * 220 : 260 - n / max * 220
  return <>
    <Typography variant="body2" color="text.secondary">Offene und fehlende Shows unterbrechen die Linie. Die Werte beziehen sich auf dieselben gewerteten Shows wie die Tabelle.</Typography>
    <Box sx={{ overflowX: 'auto' }}>
      <svg role="img" aria-label={`${label} nach Showreihenfolge`} viewBox="0 0 860 310" style={{ width: '100%', minWidth: 560, maxHeight: 380 }}>
        <title>{label} nach Showreihenfolge; genaue Werte in der folgenden Verlaufstabelle</title>
        <line x1="60" x2="820" y1="260" y2="260" stroke="currentColor" />
        <text x="6" y="22" fill="currentColor" fontSize="12">{label}</text>
        {[0, 0.5, 1].map((f) => <text key={f} x="12" y={metric === 'rank' ? 44 + f * 220 : 264 - f * 220} fill="currentColor" fontSize="12">{metric === 'rank' ? Math.round(1 + (max - 1) * f) : Math.round(max * f)}</text>)}
        {shows.map((s) => <g key={s.showId}><line x1={x(s.showNumber)} x2={x(s.showNumber)} y1="35" y2="265" stroke="currentColor" opacity="0.12" /><text x={x(s.showNumber)} y="286" textAnchor="middle" fill="currentColor" fontSize="12">{s.showNumber}{s.status !== 'CLOSED' ? ' –' : ''}</text></g>)}
        {rows.map((row, index) => {
          let previous: { number: number, value: number } | null = null
          return <g key={row.participationId} stroke={colors[index % colors.length]} fill={colors[index % colors.length]}>{row.history.map((step) => {
            const value = metric === 'points' ? step.totalPoints : step.rank
            if (value === null) { previous = null; return null }
            const before = previous
            previous = { number: step.showNumber, value }
            return <g key={step.showId}>{before && before.number + 1 === step.showNumber && <line x1={x(before.number)} y1={y(before.value)} x2={x(step.showNumber)} y2={y(value)} strokeWidth="2" />}<circle cx={x(step.showNumber)} cy={y(value)} r="4"><title>{row.displayName}, Show {step.showNumber}: {value} {label}</title></circle></g>
          })}</g>
        })}
        <text x="820" y="307" textAnchor="end" fill="currentColor" fontSize="12">Shownummer</text>
      </svg>
    </Box>
    <TableContainer sx={{ overflowX: 'auto' }}><Table aria-label="Genaue Verlaufswerte" size="small"><TableHead><TableRow><TableCell>Teilnehmer</TableCell>{shows.map((s) => <TableCell key={s.showId}>Show {s.showNumber}</TableCell>)}</TableRow></TableHead><TableBody>{rows.map((row, index) => <TableRow key={row.participationId}><TableCell><Box component="span" sx={{ color: colors[index % colors.length], mr: 1 }}>●</Box>{row.displayName}</TableCell>{row.history.map((s) => <TableCell key={s.showId}>{s.included ? `${s.totalPoints} Punkte · Platz ${s.rank}` : 'Nicht gewertet'}</TableCell>)}</TableRow>)}</TableBody></Table></TableContainer>
  </>
}
