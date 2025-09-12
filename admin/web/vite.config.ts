import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Build to be hosted at https://aparat.feezor.net/LWB/Admin
export default defineConfig({
  plugins: [react()],
  base: '/LWB/Admin/',
})
