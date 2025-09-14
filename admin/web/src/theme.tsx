import React, { createContext, useContext, useEffect, useMemo, useState } from 'react'
import { CssBaseline, ThemeProvider, createTheme, PaletteMode } from '@mui/material'

type ThemeCtx = { mode: PaletteMode; toggle: () => void }
const Ctx = createContext<ThemeCtx>({ mode: 'dark', toggle: () => {} })

export function useColorMode() { return useContext(Ctx) }

export function AppThemeProvider({ children }: { children: React.ReactNode }) {
  const [mode, setMode] = useState<PaletteMode>(() => (localStorage.getItem('theme') as PaletteMode) || 'dark')
  useEffect(() => { localStorage.setItem('theme', mode) }, [mode])
  const toggle = () => setMode(m => (m === 'dark' ? 'light' : 'dark'))
  const theme = useMemo(() => createTheme({
    palette: { mode },
    shape: { borderRadius: 10 },
    components: {
      MuiButton: { styleOverrides: { root: { textTransform: 'none', borderRadius: 10 } } },
      MuiCard: { styleOverrides: { root: { borderRadius: 14 } } },
    },
    typography: { fontFamily: 'Inter, system-ui, Arial' },
  }), [mode])
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
