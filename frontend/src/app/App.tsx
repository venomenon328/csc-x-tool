import {
  Box,
  CssBaseline,
  Drawer,
  List,
  ListItemButton,
  ListItemText,
  Stack,
  ThemeProvider,
  Toolbar,
  Typography,
} from '@mui/material'
import { BrowserRouter, Link as RouterLink, Route, Routes, useLocation } from 'react-router-dom'
import { ShowOverview } from '../features/shows/ShowOverview'
import { CandidatePage } from '../features/candidates/CandidatePage'
import { ParticipantPage } from '../features/participants/ParticipantPage'
import { ErrorBoundary } from './ErrorBoundary'
import { theme } from './theme'

const navigation = [
  { label: 'Übersicht', to: '/' },
  { label: 'Teilnehmer', to: '/participants' },
  { label: 'Daten und Sicherungen', to: '/data' },
]

function PlaceholderPage({ title }: { title: string }) {
  return (
    <Stack spacing={1}>
      <Typography component="h1" variant="h4">{title}</Typography>
      <Typography color="text.secondary">
        Dieser Bereich wird in einem nachfolgenden Entwicklungspaket mit echten Daten ergänzt.
      </Typography>
    </Stack>
  )
}

function AppShell() {
  const location = useLocation()

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <Drawer
        anchor="left"
        slotProps={{ paper: { sx: { borderRight: 1, borderColor: 'divider', width: 272 } } }}
        variant="permanent"
      >
        <Toolbar>
          <Typography sx={{ fontWeight: 700 }} variant="h6">CSC X Tool</Typography>
        </Toolbar>
        <List aria-label="Hauptnavigation">
          {navigation.map((item) => (
            <ListItemButton
              component={RouterLink}
              key={item.to}
              selected={location.pathname === item.to}
              to={item.to}
            >
              <ListItemText primary={item.label} />
            </ListItemButton>
          ))}
        </List>
      </Drawer>
      <Box component="main" sx={{ flexGrow: 1, ml: '272px', p: { xs: 3, md: 5 } }}>
        <Routes>
          <Route element={<ShowOverview />} path="/" />
          <Route element={<ParticipantPage />} path="/participants" />
          <Route element={<PlaceholderPage title="Daten und Sicherungen" />} path="/data" />
          <Route element={<CandidatePage />} path="/shows/:showId/candidates" />
          <Route element={<PlaceholderPage title="Abstimmung" />} path="/shows/:showId/voting" />
          <Route element={<PlaceholderPage title="Ergebnis" />} path="/shows/:showId/result" />
          <Route element={<PlaceholderPage title="Seite nicht gefunden" />} path="*" />
        </Routes>
      </Box>
    </Box>
  )
}

export function App() {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <ErrorBoundary>
        <BrowserRouter>
          <AppShell />
        </BrowserRouter>
      </ErrorBoundary>
    </ThemeProvider>
  )
}
