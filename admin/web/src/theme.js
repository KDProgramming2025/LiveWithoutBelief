import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { CssBaseline, ThemeProvider, createTheme } from '@mui/material';
const Ctx = createContext({ mode: 'dark', toggle: () => { } });
export function useColorMode() { return useContext(Ctx); }
export function AppThemeProvider({ children }) {
    const [mode, setMode] = useState(() => localStorage.getItem('theme') || 'dark');
    useEffect(() => { localStorage.setItem('theme', mode); }, [mode]);
    const toggle = () => setMode(m => (m === 'dark' ? 'light' : 'dark'));
    const theme = useMemo(() => createTheme({
        palette: { mode },
        shape: { borderRadius: 10 },
        components: {
            MuiButton: { styleOverrides: { root: { textTransform: 'none', borderRadius: 10 } } },
            MuiCard: { styleOverrides: { root: { borderRadius: 14 } } },
        },
        typography: { fontFamily: 'Inter, system-ui, Arial' },
    }), [mode]);
    const value = useMemo(() => ({ mode, toggle }), [mode]);
    return (_jsx(Ctx.Provider, { value: value, children: _jsxs(ThemeProvider, { theme: theme, children: [_jsx(CssBaseline, {}), children] }) }));
}
