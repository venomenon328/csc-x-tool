import { createTheme } from '@mui/material/styles'

export const theme = createTheme({
  palette: {
    mode: 'dark',
    background: {
      default: '#121318',
      paper: '#1b1d24',
    },
    primary: {
      main: '#c6d1ff',
      contrastText: '#182041',
    },
    secondary: {
      main: '#91d8c7',
    },
  },
  shape: {
    borderRadius: 12,
  },
  typography: {
    fontFamily: 'Inter, Segoe UI, sans-serif',
    h4: {
      fontWeight: 700,
      letterSpacing: '-0.02em',
    },
  },
  components: {
    MuiPaper: {
      styleOverrides: {
        root: {
          backgroundImage: 'none',
        },
      },
    },
  },
})
