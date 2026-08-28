import {
  Box,
  CssBaseline,
  Drawer,
  List,
  ListItemButton,
  ListItemText,
  Stack,
  Button,
  Alert,
  ThemeProvider,
  Toolbar,
  Typography,
} from '@mui/material'
import { BrowserRouter, Link as RouterLink, Route, Routes, useLocation } from 'react-router-dom'
import { useEffect, useState } from 'react'
import { ShowOverview } from '../features/shows/ShowOverview'
import { CandidatePage } from '../features/candidates/CandidatePage'
import { EntryPage } from '../features/entries/EntryPage'
import { ParticipantPage } from '../features/participants/ParticipantPage'
import { ResultPage } from '../features/results/ResultPage'
import { DataManagementPage } from '../features/data/DataManagementPage'
import { ErrorBoundary } from './ErrorBoundary'
import { theme } from './theme'
import { initializeCsrfProtection, apiFetch } from '../api/request'
import { GlobalSearch } from '../features/search/GlobalSearch'
import appLogo from '../assets/csc-x-tool-logo.png'

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
  const [shuttingDown, setShuttingDown] = useState(false)
  const [shutdownError, setShutdownError] = useState<string | null>(null)

  useEffect(() => {
    void initializeCsrfProtection()
  }, [])

  async function shutdown() {
    setShutdownError(null)
    try {
      const response = await apiFetch('/api/system/shutdown', { method: 'POST' })
      if (!response.ok) throw new Error('Der Shutdown wurde abgewiesen.')
      setShuttingDown(true)
    } catch {
      setShutdownError('Die Anwendung konnte nicht kontrolliert beendet werden. Bitte versuchen Sie es erneut.')
    }
  }

  if (shuttingDown) {
    return (
      <Box role="status" sx={{ display: 'grid', minHeight: '100vh', placeItems: 'center', p: 4 }}>
        <Stack spacing={1} sx={{ textAlign: 'center' }}>
          <Typography component="h1" variant="h4">Anwendung wurde beendet</Typography>
          <Typography color="text.secondary">Dieser Tab kann geschlossen werden.</Typography>
        </Stack>
      </Box>
    )
  }

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <Drawer
        anchor="left"
        slotProps={{ paper: { sx: { borderRight: 1, borderColor: 'divider', width: 272 } } }}
        variant="permanent"
      >
        <Toolbar sx={{ justifyContent: 'center', minHeight: 112 }}>
          <Box
            aria-label="CSC X Tool"
            component={RouterLink}
            sx={{
              display: 'block',
              lineHeight: 0,
              width: 96,
              '&:focus-visible': {
                borderRadius: 1,
                outline: '2px solid',
                outlineColor: 'primary.light',
                outlineOffset: 4,
              },
            }}
            to="/"
          >
            <Box alt="CSC X Tool" component="img" src={appLogo} sx={{ display: 'block', height: 'auto', width: '100%' }} />
          </Box>
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
        <Box sx={{ mt: 'auto', p: 2 }}>
          {shutdownError !== null && <Alert severity="error" sx={{ mb: 1 }}>{shutdownError}</Alert>}
          <Button color="inherit" fullWidth onClick={() => void shutdown()} variant="outlined">
            Anwendung beenden
          </Button>
        </Box>
      </Drawer>
      <Box component="main" sx={{ flexGrow: 1, ml: '272px', p: { xs: 3, md: 5 } }}>
        <GlobalSearch />
        <Routes>
          <Route element={<ShowOverview />} path="/" />
          <Route element={<ParticipantPage />} path="/participants" />
          <Route element={<DataManagementPage />} path="/data" />
          <Route element={<CandidatePage />} path="/shows/:showId/candidates" />
          <Route element={<EntryPage />} path="/shows/:showId/voting" />
          <Route element={<ResultPage />} path="/shows/:showId/result" />
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
