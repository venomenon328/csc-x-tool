import { useState } from 'react'
import {
  Box,
  Button,
  CssBaseline,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Drawer,
  List,
  ListItemButton,
  ListItemText,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  ThemeProvider,
  Toolbar,
  Typography,
} from '@mui/material'
import { BrowserRouter, Link as RouterLink, Route, Routes, useLocation } from 'react-router-dom'
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

function Overview() {
  const [dialogOpen, setDialogOpen] = useState(false)

  return (
    <Stack spacing={3}>
      <PlaceholderPage title="CSC X Tool" />
      <Paper component="section" elevation={0} sx={{ border: 1, borderColor: 'divider', p: 3 }}>
        <Stack spacing={2}>
          <Typography variant="h6">Technische Basis</Typography>
          <Typography color="text.secondary">
            Die Oberfläche, Navigation und API-Grundlagen stehen. Fachliche Datenhaltung folgt mit P1.
          </Typography>
          <TextField label="Beispiel für Eingabefelder" size="small" sx={{ maxWidth: 360 }} />
          <Table aria-label="Technische Prüfoberfläche" size="small">
            <TableHead>
              <TableRow>
                <TableCell>Baustein</TableCell>
                <TableCell>Status</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              <TableRow>
                <TableCell>Dark Theme</TableCell>
                <TableCell>bereit</TableCell>
              </TableRow>
              <TableRow>
                <TableCell>Formular, Tabelle und Dialog</TableCell>
                <TableCell>geprüft</TableCell>
              </TableRow>
            </TableBody>
          </Table>
          <Box>
            <Button onClick={() => setDialogOpen(true)} variant="contained">Dialog prüfen</Button>
          </Box>
        </Stack>
      </Paper>
      <Dialog onClose={() => setDialogOpen(false)} open={dialogOpen}>
        <DialogTitle>Komponentenbibliothek</DialogTitle>
        <DialogContent>Die Dialog- und Dark-Theme-Basis ist lokal in den Build eingebunden.</DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Schließen</Button>
        </DialogActions>
      </Dialog>
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
          <Route element={<Overview />} path="/" />
          <Route element={<PlaceholderPage title="Teilnehmer" />} path="/participants" />
          <Route element={<PlaceholderPage title="Daten und Sicherungen" />} path="/data" />
          <Route element={<PlaceholderPage title="Kandidaten" />} path="/shows/:showId/candidates" />
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
