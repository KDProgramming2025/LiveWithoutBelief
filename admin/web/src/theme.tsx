import React, { createContext, useContext, useEffect, useMemo, useState } from 'react'
import { CssBaseline, ThemeProvider, createTheme, PaletteMode } from '@mui/material'

type ThemeCtx = { mode: PaletteMode; toggle: () => void }
const Ctx = createContext<ThemeCtx>({ mode: 'dark', toggle: () => {} })

export function useColorMode() { return useContext(Ctx) }

export function AppThemeProvider({ children }: { children: React.ReactNode }) {
  const [mode, setMode] = useState<PaletteMode>(() => (localStorage.getItem('theme') as PaletteMode) || 'dark')
  useEffect(() => { localStorage.setItem('theme', mode) }, [mode])
  const toggle = () => setMode(m => (m === 'dark' ? 'light' : 'dark'))
  const theme = useMemo(() => {
    // Base theme with palette tuned per mode
    const base = createTheme({
      palette: {
        mode,
        ...(mode === 'dark'
          ? {
              background: { default: '#0b0f1a', paper: '#111827' },
              primary: { main: '#7c9cff' },
              secondary: { main: '#c084fc' },
            }
          : {
              background: { default: '#f7f9fc', paper: '#ffffff' },
            }),
      },
      shape: { borderRadius: 10 },
      typography: { fontFamily: 'Inter, system-ui, Arial' },
      components: {
        MuiButton: { styleOverrides: { root: { textTransform: 'none', borderRadius: 10 } } },
        MuiCard: { styleOverrides: { root: { borderRadius: 14 } } },
      },
    })

    // Add a tasteful background via CssBaseline, especially for dark mode
    const darkBg = [
      'radial-gradient(1200px 700px at -10% -20%, rgba(124,156,255,0.10), transparent 60%)',
      'radial-gradient(900px 600px at 110% -10%, rgba(192,132,252,0.10), transparent 60%)',
      'linear-gradient(180deg, #0b0f1a 0%, #0a0e17 100%)',
    ].join(',')
    const lightBg = 'linear-gradient(180deg, #fdfefe 0%, #f5f7fb 100%)'

    return createTheme({
      ...base,
      components: {
        ...base.components,
        MuiCssBaseline: {
          styleOverrides: {
            html: { height: '100%' },
            body: {
              minHeight: '100%',
              background: mode === 'dark' ? darkBg : lightBg,
              backgroundAttachment: 'fixed',
              backgroundRepeat: 'no-repeat',
              backgroundColor: base.palette.background.default,
              MozOsxFontSmoothing: 'grayscale',
              WebkitFontSmoothing: 'antialiased',
            },
            '#root': { minHeight: '100%' },
          },
        },
      },
    })
  }, [mode])
  const value = useMemo(() => ({ mode, toggle }), [mode])
  return (
    <Ctx.Provider value={value}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        {children}
      </ThemeProvider>
    </Ctx.Provider>
  )
}
