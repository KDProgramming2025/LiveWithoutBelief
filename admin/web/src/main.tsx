import React from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import { AppThemeProvider } from './theme'

createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <AppThemeProvider>
      <App />
    </AppThemeProvider>
  </React.StrictMode>
)
