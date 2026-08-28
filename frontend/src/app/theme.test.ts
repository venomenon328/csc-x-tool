import { getContrastRatio } from '@mui/material/styles'
import { describe, expect, it } from 'vitest'
import { cyBoardColors, theme } from './theme'

const wcagAaTextPairs = [
  [theme.palette.text.primary, theme.palette.background.default],
  [theme.palette.text.secondary, theme.palette.background.paper],
  [theme.palette.secondary.main, theme.palette.background.default],
  [theme.palette.secondary.main, theme.palette.background.paper],
  [theme.palette.secondary.main, cyBoardColors.raised],
] as const

describe('CyBoard theme', () => {
  it('maps the CyBoard surfaces and interaction roles to the global palette', () => {
    expect(theme.palette.background.default).toBe(cyBoardColors.background)
    expect(theme.palette.background.paper).toBe(cyBoardColors.surface)
    expect(theme.palette.divider).toBe(cyBoardColors.divider)
    expect(theme.palette.action.hover).toBe(cyBoardColors.raised)
    expect(theme.palette.action.selected).toBe(cyBoardColors.interactive)
  })

  it('keeps the required text and accent pairs at WCAG AA contrast', () => {
    wcagAaTextPairs.forEach(([foreground, background]) => {
      expect(getContrastRatio(foreground, background)).toBeGreaterThanOrEqual(4.5)
    })
    expect(getContrastRatio(theme.palette.primary.contrastText, theme.palette.primary.main)).toBeGreaterThanOrEqual(4.5)
    expect(getContrastRatio(theme.palette.secondary.main, theme.palette.background.paper)).toBeGreaterThanOrEqual(3)
  })

  it('uses the cyan accent for focus indicators with sufficient non-text contrast', () => {
    [theme.palette.background.default, theme.palette.background.paper, cyBoardColors.raised].forEach((surface) => {
      expect(getContrastRatio(theme.palette.secondary.main, surface)).toBeGreaterThanOrEqual(3)
    })
    expect(theme.components?.MuiButtonBase?.styleOverrides?.root).toMatchObject({
      '&.Mui-focusVisible': expect.objectContaining({ outline: `2px solid ${cyBoardColors.accent}` }),
    })
  })

  it('reserves the dark action blue for filled actions instead of default text controls', () => {
    expect(getContrastRatio(theme.palette.primary.main, theme.palette.background.default)).toBeLessThan(4.5)
    expect(theme.components?.MuiButton?.styleOverrides?.root).toMatchObject({
      '&.MuiButton-text.MuiButton-colorPrimary': { color: cyBoardColors.accent },
      '&.MuiButton-outlined.MuiButton-colorPrimary': {
        borderColor: cyBoardColors.accent,
        color: cyBoardColors.accent,
      },
      '&.MuiButton-contained.MuiButton-colorPrimary': {
        backgroundColor: cyBoardColors.action,
        color: cyBoardColors.actionText,
      },
    })
  })

  it('keeps semantic error, warning, and success colors independent from the CyBoard action palette', () => {
    const semanticColors = [theme.palette.error.main, theme.palette.warning.main, theme.palette.success.main]
    expect(theme.palette.error.main).toBe(cyBoardColors.error)
    expect(theme.palette.warning.main).toBe(cyBoardColors.warning)
    expect(theme.palette.success.main).toBe(cyBoardColors.success)
    expect(semanticColors).not.toContain(cyBoardColors.action)
    semanticColors.forEach((color) => {
      expect(getContrastRatio(color, cyBoardColors.raised)).toBeGreaterThanOrEqual(4.5)
    })
  })
})
