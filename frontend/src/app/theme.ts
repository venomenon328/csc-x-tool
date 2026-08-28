import { createTheme } from '@mui/material/styles'

/**
 * The named CyBoard reference colors are kept here so feature components can
 * consume semantic MUI roles instead of recreating a local palette.
 */
export const cyBoardColors = {
  background: '#1A1D21',
  surface: '#1E2734',
  shell: '#1A222D',
  raised: '#242E3D',
  interactive: '#2F394C',
  divider: '#36373B',
  action: '#01579B',
  actionDark: '#014A80',
  accent: '#01A8E4',
  text: '#E6E7EA',
  textSecondary: '#B9C7D2',
  actionText: '#FFFFFF',
  accentText: '#07191F',
  disabledText: '#84919B',
  error: '#F87171',
  warning: '#FBBF24',
  success: '#34D399',
  info: '#38BDF8',
} as const

const focusRing = {
  outline: `2px solid ${cyBoardColors.accent}`,
  outlineOffset: 2,
}

export const theme = createTheme({
  palette: {
    mode: 'dark',
    background: {
      default: cyBoardColors.background,
      paper: cyBoardColors.surface,
    },
    text: {
      primary: cyBoardColors.text,
      secondary: cyBoardColors.textSecondary,
      disabled: cyBoardColors.disabledText,
    },
    divider: cyBoardColors.divider,
    primary: {
      main: cyBoardColors.action,
      light: cyBoardColors.accent,
      dark: cyBoardColors.actionDark,
      contrastText: cyBoardColors.actionText,
    },
    secondary: {
      main: cyBoardColors.accent,
      light: cyBoardColors.info,
      dark: cyBoardColors.action,
      contrastText: cyBoardColors.accentText,
    },
    error: {
      main: cyBoardColors.error,
      contrastText: cyBoardColors.accentText,
    },
    warning: {
      main: cyBoardColors.warning,
      contrastText: cyBoardColors.accentText,
    },
    success: {
      main: cyBoardColors.success,
      contrastText: cyBoardColors.accentText,
    },
    info: {
      main: cyBoardColors.info,
      contrastText: cyBoardColors.accentText,
    },
    action: {
      active: cyBoardColors.textSecondary,
      hover: cyBoardColors.raised,
      hoverOpacity: 1,
      selected: cyBoardColors.interactive,
      selectedOpacity: 1,
      disabled: cyBoardColors.disabledText,
      disabledBackground: cyBoardColors.raised,
      focus: cyBoardColors.accent,
      focusOpacity: 1,
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
    MuiCssBaseline: {
      styleOverrides: {
        a: {
          color: cyBoardColors.accent,
        },
        'a:focus-visible, button:focus-visible, [tabindex]:focus-visible': focusRing,
      },
    },
    MuiPaper: {
      styleOverrides: {
        root: {
          backgroundImage: 'none',
        },
      },
    },
    MuiDrawer: {
      styleOverrides: {
        paper: {
          backgroundColor: cyBoardColors.surface,
        },
      },
    },
    MuiDialog: {
      styleOverrides: {
        paper: {
          backgroundColor: cyBoardColors.surface,
        },
      },
    },
    MuiTooltip: {
      styleOverrides: {
        tooltip: {
          backgroundColor: cyBoardColors.raised,
          color: cyBoardColors.text,
        },
        arrow: {
          color: cyBoardColors.raised,
        },
      },
    },
    MuiButtonBase: {
      styleOverrides: {
        root: {
          '&.Mui-focusVisible': focusRing,
        },
      },
    },
    MuiButton: {
      styleOverrides: {
        root: {
          '&.MuiButton-text.MuiButton-colorPrimary': {
            color: cyBoardColors.accent,
            '&:hover': {
              backgroundColor: cyBoardColors.raised,
              color: cyBoardColors.accent,
            },
          },
          '&.MuiButton-outlined.MuiButton-colorPrimary': {
            borderColor: cyBoardColors.accent,
            color: cyBoardColors.accent,
            '&:hover': {
              backgroundColor: cyBoardColors.raised,
              borderColor: cyBoardColors.accent,
            },
          },
          '&.MuiButton-contained.MuiButton-colorPrimary': {
            backgroundColor: cyBoardColors.action,
            color: cyBoardColors.actionText,
            '&:hover': {
              backgroundColor: cyBoardColors.actionDark,
            },
          },
          '&.MuiButton-contained.MuiButton-colorSecondary': {
            backgroundColor: cyBoardColors.interactive,
            color: cyBoardColors.text,
            '&:hover': {
              backgroundColor: cyBoardColors.raised,
            },
          },
        },
      },
    },
    MuiIconButton: {
      styleOverrides: {
        colorPrimary: {
          color: cyBoardColors.accent,
          '&:hover': {
            backgroundColor: cyBoardColors.raised,
          },
        },
      },
    },
    MuiLink: {
      styleOverrides: {
        root: {
          color: cyBoardColors.accent,
          '&:focus-visible': focusRing,
        },
      },
    },
    MuiListItemButton: {
      styleOverrides: {
        root: {
          '&:hover': {
            backgroundColor: cyBoardColors.raised,
          },
          '&.Mui-selected, &.Mui-selected:hover': {
            backgroundColor: cyBoardColors.interactive,
            borderLeft: `3px solid ${cyBoardColors.accent}`,
          },
        },
      },
    },
    MuiMenuItem: {
      styleOverrides: {
        root: {
          '&.Mui-focusVisible': {
            backgroundColor: cyBoardColors.raised,
          },
          '&.Mui-selected, &.Mui-selected:hover': {
            backgroundColor: cyBoardColors.interactive,
          },
        },
      },
    },
    MuiOutlinedInput: {
      styleOverrides: {
        root: {
          backgroundColor: cyBoardColors.raised,
          '&:hover .MuiOutlinedInput-notchedOutline': {
            borderColor: cyBoardColors.textSecondary,
          },
          '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
            borderColor: cyBoardColors.accent,
            borderWidth: 2,
          },
        },
        notchedOutline: {
          borderColor: cyBoardColors.divider,
        },
      },
    },
    MuiInputLabel: {
      styleOverrides: {
        root: {
          color: cyBoardColors.textSecondary,
          '&.Mui-focused': {
            color: cyBoardColors.accent,
          },
        },
      },
    },
    MuiFormHelperText: {
      styleOverrides: {
        root: {
          color: cyBoardColors.textSecondary,
        },
      },
    },
    MuiAutocomplete: {
      styleOverrides: {
        option: {
          '&.Mui-focused': {
            backgroundColor: cyBoardColors.raised,
          },
          '&[aria-selected="true"]': {
            backgroundColor: cyBoardColors.interactive,
          },
        },
      },
    },
    MuiCheckbox: {
      styleOverrides: {
        root: {
          color: cyBoardColors.accent,
          '&.Mui-checked, &.MuiCheckbox-indeterminate': {
            color: cyBoardColors.accent,
          },
        },
      },
    },
    MuiSwitch: {
      styleOverrides: {
        switchBase: {
          '&.Mui-checked': {
            color: cyBoardColors.accent,
          },
          '&.Mui-checked + .MuiSwitch-track': {
            backgroundColor: cyBoardColors.accent,
          },
        },
        track: {
          backgroundColor: cyBoardColors.interactive,
        },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: {
          backgroundColor: cyBoardColors.raised,
          color: cyBoardColors.text,
        },
        colorSecondary: {
          backgroundColor: cyBoardColors.accent,
          color: cyBoardColors.accentText,
        },
      },
    },
    MuiTableCell: {
      styleOverrides: {
        root: {
          borderColor: cyBoardColors.divider,
        },
      },
    },
    MuiTableRow: {
      styleOverrides: {
        root: {
          '&.MuiTableRow-hover:hover': {
            backgroundColor: cyBoardColors.raised,
          },
        },
      },
    },
    MuiAlert: {
      styleOverrides: {
        root: {
          backgroundColor: cyBoardColors.raised,
          border: '1px solid currentColor',
        },
      },
    },
  },
})
